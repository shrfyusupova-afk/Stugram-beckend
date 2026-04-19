const crypto = require("crypto");
const os = require("os");

const { redis } = require("../config/redis");

const GLOBAL_METRICS_KEY = "rec:refresh:metrics:global";
const JOB_METRICS_KEY_PREFIX = "rec:refresh:metrics:job";
const WORKER_STATUS_KEY = "rec:refresh:worker:status";
const DEAD_LETTER_LIST_KEY = "rec:refresh:dead_letters";
const DEAD_LETTER_MAX_ITEMS = 200;
const WORKER_STALE_THRESHOLD_MS = 60 * 1000;
const metricsEnabled = () => redis.status === "ready";

const buildJobMetricsKey = (jobName) => `${JOB_METRICS_KEY_PREFIX}:${jobName}`;

const incrementMetricCounter = async (key, field, by = 1) => {
  if (!metricsEnabled()) return;
  await redis.hincrby(key, field, by);
};

const incrementMetricFloat = async (key, field, by = 0) => {
  if (!metricsEnabled()) return;
  await redis.hincrbyfloat(key, field, by);
};

const setMetricField = async (key, field, value) => {
  if (!metricsEnabled()) return;
  await redis.hset(key, field, typeof value === "object" ? JSON.stringify(value) : String(value));
};

const recordQueuedMetric = async ({ jobName }) => {
  await Promise.all([
    incrementMetricCounter(GLOBAL_METRICS_KEY, "queuedCount", 1),
    incrementMetricCounter(buildJobMetricsKey(jobName), "queuedCount", 1),
  ]);
};

const recordRetryMetric = async ({ jobName, attempt, maxAttempts, error }) => {
  const payload = {
    attempt,
    maxAttempts,
    lastRetryAt: new Date().toISOString(),
  };

  if (error) payload.lastRetryError = error.message;

  await Promise.all([
    incrementMetricCounter(GLOBAL_METRICS_KEY, "retryCount", 1),
    incrementMetricCounter(buildJobMetricsKey(jobName), "retryCount", 1),
    setMetricField(GLOBAL_METRICS_KEY, "lastRetryMeta", payload),
    setMetricField(buildJobMetricsKey(jobName), "lastRetryMeta", payload),
  ]);
};

const recordCompletedMetric = async ({ jobName, latencyMs }) => {
  const timestamp = new Date().toISOString();

  await Promise.all([
    incrementMetricCounter(GLOBAL_METRICS_KEY, "completedCount", 1),
    incrementMetricCounter(buildJobMetricsKey(jobName), "completedCount", 1),
    incrementMetricFloat(GLOBAL_METRICS_KEY, "totalLatencyMs", latencyMs),
    incrementMetricFloat(buildJobMetricsKey(jobName), "totalLatencyMs", latencyMs),
    setMetricField(GLOBAL_METRICS_KEY, "lastSuccessAt", timestamp),
    setMetricField(buildJobMetricsKey(jobName), "lastSuccessAt", timestamp),
  ]);
};

const recordFailedMetric = async ({ jobName, latencyMs, error, finalFailure = false }) => {
  const timestamp = new Date().toISOString();
  const payload = {
    error: error?.message || "Unknown worker error",
    finalFailure,
    failedAt: timestamp,
  };

  await Promise.all([
    incrementMetricCounter(GLOBAL_METRICS_KEY, "failedCount", 1),
    incrementMetricCounter(buildJobMetricsKey(jobName), "failedCount", 1),
    incrementMetricFloat(GLOBAL_METRICS_KEY, "failedLatencyMs", latencyMs || 0),
    incrementMetricFloat(buildJobMetricsKey(jobName), "failedLatencyMs", latencyMs || 0),
    setMetricField(GLOBAL_METRICS_KEY, "lastFailureAt", timestamp),
    setMetricField(buildJobMetricsKey(jobName), "lastFailureAt", timestamp),
    setMetricField(GLOBAL_METRICS_KEY, "lastFailureMeta", payload),
    setMetricField(buildJobMetricsKey(jobName), "lastFailureMeta", payload),
  ]);
};

const recordDeadLetter = async ({ job, error, latencyMs }) => {
  if (!metricsEnabled()) return;

  const entry = {
    id: crypto.randomUUID(),
    jobId: job.id,
    name: job.name,
    queue: "recommendation-refresh",
    userId: job.data?.userId || null,
    surface: job.data?.surface || null,
    reason: job.data?.reason || null,
    attemptsMade: job.attemptsMade,
    maxAttempts: job.opts?.attempts || null,
    latencyMs: Math.round(latencyMs || 0),
    failedAt: new Date().toISOString(),
    error: error?.message || "Unknown worker error",
    stacktrace: job.stacktrace || [],
    data: job.data,
  };

  const serialized = JSON.stringify(entry);
  const pipeline = redis.pipeline();
  pipeline.lpush(DEAD_LETTER_LIST_KEY, serialized);
  pipeline.ltrim(DEAD_LETTER_LIST_KEY, 0, DEAD_LETTER_MAX_ITEMS - 1);
  pipeline.hincrby(GLOBAL_METRICS_KEY, "deadLetterCount", 1);
  pipeline.hincrby(buildJobMetricsKey(job.name), "deadLetterCount", 1);
  pipeline.hset(GLOBAL_METRICS_KEY, "lastDeadLetterAt", entry.failedAt);
  pipeline.hset(buildJobMetricsKey(job.name), "lastDeadLetterAt", entry.failedAt);
  await pipeline.exec();
};

const updateWorkerStatus = async ({ workerId, status, currentJob, meta }) => {
  if (!metricsEnabled()) return;

  const current = await getWorkerStatus().catch(() => null);

  const payload = {
    workerId,
    host: os.hostname(),
    pid: process.pid,
    status: status || current?.status || "idle",
    currentJob: currentJob === undefined ? current?.currentJob || null : currentJob,
    meta: meta === undefined ? current?.meta || null : meta,
    lastHeartbeatAt: new Date().toISOString(),
  };

  await redis.set(WORKER_STATUS_KEY, JSON.stringify(payload), "EX", Math.ceil(WORKER_STALE_THRESHOLD_MS / 1000) * 3);
};

const getWorkerStatus = async () => {
  if (!metricsEnabled()) {
    return {
      status: "disabled",
      lastHeartbeatAt: null,
      stale: false,
    };
  }

  const raw = await redis.get(WORKER_STATUS_KEY);
  if (!raw) {
    return {
      status: "offline",
      lastHeartbeatAt: null,
      stale: true,
    };
  }

  const parsed = JSON.parse(raw);
  const lastHeartbeatMs = parsed.lastHeartbeatAt ? new Date(parsed.lastHeartbeatAt).getTime() : 0;
  const stale = !lastHeartbeatMs || Date.now() - lastHeartbeatMs > WORKER_STALE_THRESHOLD_MS;

  return {
    ...parsed,
    stale,
    status: stale ? "stale" : parsed.status,
  };
};

const mapMetricsHash = (hash = {}) => {
  const completedCount = Number(hash.completedCount || 0);
  const totalLatencyMs = Number(hash.totalLatencyMs || 0);

  return {
    queuedCount: Number(hash.queuedCount || 0),
    completedCount,
    failedCount: Number(hash.failedCount || 0),
    retryCount: Number(hash.retryCount || 0),
    deadLetterCount: Number(hash.deadLetterCount || 0),
    averageProcessingLatencyMs: completedCount ? Number((totalLatencyMs / completedCount).toFixed(2)) : 0,
    lastSuccessAt: hash.lastSuccessAt || null,
    lastFailureAt: hash.lastFailureAt || null,
    lastDeadLetterAt: hash.lastDeadLetterAt || null,
    lastRetryMeta: hash.lastRetryMeta ? JSON.parse(hash.lastRetryMeta) : null,
    lastFailureMeta: hash.lastFailureMeta ? JSON.parse(hash.lastFailureMeta) : null,
  };
};

const getRefreshMetricsSnapshot = async () => {
  if (!metricsEnabled()) {
    return {
      global: mapMetricsHash(),
      perJobType: {
        refresh_feed: mapMetricsHash(),
        refresh_reels: mapMetricsHash(),
        refresh_profiles: mapMetricsHash(),
      },
    };
  }

  const [globalHash, feedHash, reelsHash, profilesHash] = await Promise.all([
    redis.hgetall(GLOBAL_METRICS_KEY),
    redis.hgetall(buildJobMetricsKey("refresh_feed")),
    redis.hgetall(buildJobMetricsKey("refresh_reels")),
    redis.hgetall(buildJobMetricsKey("refresh_profiles")),
  ]);

  return {
    global: mapMetricsHash(globalHash),
    perJobType: {
      refresh_feed: mapMetricsHash(feedHash),
      refresh_reels: mapMetricsHash(reelsHash),
      refresh_profiles: mapMetricsHash(profilesHash),
    },
  };
};

const getDeadLetters = async (limit = 20) => {
  if (!metricsEnabled()) return [];

  const rows = await redis.lrange(DEAD_LETTER_LIST_KEY, 0, Math.max(limit - 1, 0));
  return rows.map((row) => JSON.parse(row));
};

const getDeadLetterById = async (deadLetterId) => {
  const rows = await getDeadLetters(DEAD_LETTER_MAX_ITEMS);
  return rows.find((row) => row.id === deadLetterId) || null;
};

module.exports = {
  getDeadLetterById,
  getDeadLetters,
  getRefreshMetricsSnapshot,
  getWorkerStatus,
  recordCompletedMetric,
  recordDeadLetter,
  recordFailedMetric,
  recordQueuedMetric,
  recordRetryMetric,
  updateWorkerStatus,
};
