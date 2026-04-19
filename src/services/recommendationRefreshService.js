const ApiError = require("../utils/ApiError");
const logger = require("../utils/logger");
const { redis, isRedisReady, getRedisStatus } = require("../config/redis");
const { env } = require("../config/env");
const { getPagination } = require("../utils/pagination");
const RecommendationReplayAudit = require("../models/RecommendationReplayAudit");
const recommendationQueue = require("../queues/recommendationRefreshQueue");
const {
  getDeadLetterById,
  getDeadLetters,
  getRefreshMetricsSnapshot,
  getWorkerStatus,
  recordQueuedMetric,
} = require("./recommendationRefreshMetricsService");

const RECOMMENDATION_REFRESH_DEDUPE_TTL_SECONDS = 30;
const DEFAULT_REFRESH_PAGE = 1;
const DEFAULT_REFRESH_LIMIT = 20;
const RECENT_FAILED_JOBS_LIMIT = 10;
const DEAD_LETTER_REPLAY_LOCK_TTL_SECONDS = 60;
const BULK_REPLAY_LOCK_TTL_SECONDS = 60;
const MAX_BULK_REPLAY_LIMIT = 50;

const SURFACE_TO_JOB_NAME = {
  feed: "refresh_feed",
  reels: "refresh_reels",
  profiles: "refresh_profiles",
};
const JOB_NAME_TO_SURFACE = Object.fromEntries(Object.entries(SURFACE_TO_JOB_NAME).map(([surface, jobName]) => [jobName, surface]));

const buildRefreshDedupeKey = ({ userId, surface }) => `rec:refresh:dedupe:user:${userId}:surface:${surface}`;
const buildRefreshJobId = ({ userId, surface }) => `${SURFACE_TO_JOB_NAME[surface]}:${userId}`;
const buildDeadLetterReplayLockKey = (deadLetterId) => `rec:refresh:dead_letter:replay:${deadLetterId}`;
const buildBulkReplayLockKey = ({ surface = "all", jobName = "all" }) => `rec:refresh:dead_letter:bulk_replay:surface:${surface}:job:${jobName}`;

const normalizeSurfaces = (surfaces = []) => [...new Set(surfaces.filter((surface) => SURFACE_TO_JOB_NAME[surface]))];
const normalizeJobName = (jobName) => (jobName && JOB_NAME_TO_SURFACE[jobName] ? jobName : null);
const getRecommendationRefreshQueue = () => recommendationQueue.getRecommendationRefreshQueue();
const isRecommendationRefreshAvailable = () => Boolean(getRecommendationRefreshQueue()) && recommendationQueue.isRecommendationQueueReady();
const isRecommendationRefreshDisabledForClosedAlpha = () =>
  !env.queueEnabled || !env.recommendationWorkerEnabled || env.recommendationMode === "db-direct";

const enqueueRecommendationRefreshJobs = async ({ userId, surfaces = [], reason = "strong_signal", version = null }) => {
  const normalizedSurfaces = normalizeSurfaces(surfaces);
  if (!normalizedSurfaces.length) return [];

  if (isRecommendationRefreshDisabledForClosedAlpha()) {
    logger.info("Recommendation refresh queue intentionally disabled; using db-direct recommendations", {
      userId,
      surfaces: normalizedSurfaces,
      reason,
      recommendationMode: env.recommendationMode,
      queueEnabled: env.queueEnabled,
      recommendationWorkerEnabled: env.recommendationWorkerEnabled,
    });
    return [];
  }

  recommendationQueue.initRecommendationRefreshQueueResources();

  if (!isRecommendationRefreshAvailable()) {
    if (env.redisRequired) {
      throw new Error("Recommendation refresh queue is unavailable because Redis is not connected");
    }

    logger.warn("Recommendation refresh queue is disabled; skipping enqueue", {
      userId,
      surfaces: normalizedSurfaces,
      reason,
    });
    return [];
  }

  const enqueued = [];

  for (const surface of normalizedSurfaces) {
    try {
      const dedupeKey = buildRefreshDedupeKey({ userId, surface });
      const dedupeLock = await redis.set(dedupeKey, String(Date.now()), "EX", RECOMMENDATION_REFRESH_DEDUPE_TTL_SECONDS, "NX");

      if (!dedupeLock) {
        logger.info("Recommendation refresh job skipped due to dedupe window", {
          userId,
          surface,
          reason,
          dedupeTtlSeconds: RECOMMENDATION_REFRESH_DEDUPE_TTL_SECONDS,
        });
        continue;
      }

      const jobName = SURFACE_TO_JOB_NAME[surface];
      const jobId = buildRefreshJobId({ userId, surface });

      await getRecommendationRefreshQueue().add(
        jobName,
        {
          userId: String(userId),
          surface,
          reason,
          version,
          page: DEFAULT_REFRESH_PAGE,
          limit: DEFAULT_REFRESH_LIMIT,
          requestedAt: new Date().toISOString(),
        },
        {
          jobId,
        }
      );

      await recordQueuedMetric({ jobName });

      logger.info("Recommendation refresh job enqueued", {
        userId,
        surface,
        reason,
        version,
        jobId,
      });

      enqueued.push({ surface, jobId });
    } catch (error) {
      logger.error("Recommendation refresh enqueue failed", {
        userId,
        surface,
        reason,
        version,
        message: error.message,
      });

      if (env.redisRequired) {
        throw error;
      }
    }
  }

  return enqueued;
};

const formatFailedJob = (job) => ({
  id: job.id,
  name: job.name,
  attemptsMade: job.attemptsMade,
  maxAttempts: job.opts?.attempts || null,
  timestamp: job.timestamp ? new Date(job.timestamp).toISOString() : null,
  processedOn: job.processedOn ? new Date(job.processedOn).toISOString() : null,
  finishedOn: job.finishedOn ? new Date(job.finishedOn).toISOString() : null,
  failedReason: job.failedReason || null,
  data: {
    userId: job.data?.userId || null,
    surface: job.data?.surface || null,
    version: job.data?.version || null,
    reason: job.data?.reason || null,
  },
});

const formatDeadLetterDetail = (deadLetter) => ({
  id: deadLetter.id,
  jobId: deadLetter.jobId,
  userId: deadLetter.userId,
  surface: deadLetter.surface,
  attempts: {
    made: deadLetter.attemptsMade || 0,
    max: deadLetter.maxAttempts || null,
  },
  reason: deadLetter.reason || null,
  error: deadLetter.error || null,
  stacktrace: deadLetter.stacktrace || [],
  latencyMs: deadLetter.latencyMs || 0,
  payload: deadLetter.data || null,
  createdAt: deadLetter.failedAt || null,
  jobName: deadLetter.name,
  queue: deadLetter.queue,
});

const formatReplayAuditEntry = (entry) => ({
  id: entry._id,
  actor: entry.actor,
  replayType: entry.replayType,
  status: entry.status,
  replayedCount: entry.replayedCount,
  skippedCount: entry.skippedCount,
  failureReason: entry.failureReason,
  createdAt: entry.createdAt,
});

const createReplayAudit = async ({
  actorId,
  replayType,
  status,
  deadLetterIds = [],
  originalJobIds = [],
  filters = null,
  affectedSurfaces = [],
  replayedCount = 0,
  skippedCount = 0,
  details = null,
  failureReason = null,
}) =>
  RecommendationReplayAudit.create({
    actor: actorId,
    replayType,
    status,
    deadLetterIds,
    originalJobIds,
    filters,
    affectedSurfaces,
    replayedCount,
    skippedCount,
    details,
    failureReason,
  });

const getRecommendationRefreshQueueHealth = async () => {
  if (isRecommendationRefreshDisabledForClosedAlpha()) {
    return {
      redis: getRedisStatus(),
      queue: {
        counts: {
          waiting: 0,
          active: 0,
          completed: 0,
          failed: 0,
          delayed: 0,
        },
        recentFailedJobs: [],
        recentDeadLetters: [],
        enabled: false,
        configured: false,
        ready: false,
        required: false,
        mode: "disabled-for-closed-alpha",
        reason: "Closed alpha uses db-direct recommendations and does not require a paid background worker",
      },
      worker: {
        required: env.workerRequired,
        enabled: env.recommendationWorkerEnabled,
        status: "not-required",
        stale: false,
        mode: "disabled-for-closed-alpha",
      },
      metrics: {
        global: {
          queuedCount: 0,
          completedCount: 0,
          failedCount: 0,
          retryCount: 0,
          deadLetterCount: 0,
          averageProcessingLatencyMs: 0,
          lastSuccessAt: null,
          lastFailureAt: null,
          lastDeadLetterAt: null,
          lastRetryMeta: null,
          lastFailureMeta: null,
        },
        perJobType: {},
      },
    };
  }

  recommendationQueue.initRecommendationRefreshQueueResources();

  if (!isRecommendationRefreshAvailable()) {
    return {
      redis: getRedisStatus(),
      queue: {
        counts: {
          waiting: 0,
          active: 0,
          completed: 0,
          failed: 0,
          delayed: 0,
        },
        recentFailedJobs: [],
        recentDeadLetters: [],
        enabled: false,
        configured: recommendationQueue.recommendationQueueConfigured,
        ready: isRedisReady(),
      },
      worker: await getWorkerStatus(),
      metrics: await getRefreshMetricsSnapshot(),
    };
  }

  const [counts, failedJobs, metrics, workerStatus, deadLetters] = await Promise.all([
    getRecommendationRefreshQueue().getJobCounts("waiting", "active", "completed", "failed", "delayed"),
    getRecommendationRefreshQueue().getJobs(["failed"], 0, RECENT_FAILED_JOBS_LIMIT - 1, false),
    getRefreshMetricsSnapshot(),
    getWorkerStatus(),
    getDeadLetters(RECENT_FAILED_JOBS_LIMIT),
  ]);

  return {
    redis: getRedisStatus(),
    queue: {
      counts: {
        waiting: counts.waiting || 0,
        active: counts.active || 0,
        completed: counts.completed || 0,
        failed: counts.failed || 0,
        delayed: counts.delayed || 0,
      },
      recentFailedJobs: failedJobs.map(formatFailedJob),
      recentDeadLetters: deadLetters,
      enabled: true,
      configured: recommendationQueue.recommendationQueueConfigured,
      ready: true,
    },
    worker: workerStatus,
    metrics,
  };
};

const getDeadLetterDetail = async (deadLetterId) => {
  if (!isRecommendationRefreshAvailable()) {
    throw new ApiError(503, "Recommendation refresh queue is disabled");
  }

  const deadLetter = await getDeadLetterById(deadLetterId);
  if (!deadLetter) {
    throw new ApiError(404, "Dead-letter record not found");
  }

  const relatedReplayAudits = await RecommendationReplayAudit.find({
    deadLetterIds: deadLetterId,
  })
    .sort({ createdAt: -1 })
    .limit(10)
    .populate("actor", "username fullName role");

  return {
    ...formatDeadLetterDetail(deadLetter),
    relatedReplayAudits: relatedReplayAudits.map(formatReplayAuditEntry),
  };
};

const replayDeadLetterById = async ({ deadLetterId, actorId = null }) => {
  if (!isRecommendationRefreshAvailable()) {
    throw new ApiError(503, "Recommendation refresh queue is disabled");
  }

  const deadLetter = await getDeadLetterById(deadLetterId);
  if (!deadLetter) {
    throw new ApiError(404, "Dead-letter record not found");
  }

  const replayLockKey = buildDeadLetterReplayLockKey(deadLetterId);
  const replayLock = await redis.set(replayLockKey, String(Date.now()), "EX", DEAD_LETTER_REPLAY_LOCK_TTL_SECONDS, "NX");

  logger.info("Recommendation dead-letter replay requested", {
    deadLetterId,
    actorId,
    userId: deadLetter.userId,
    surface: deadLetter.surface,
    jobName: deadLetter.name,
  });

  if (!replayLock) {
    await createReplayAudit({
      actorId,
      replayType: "single",
      status: "skipped",
      deadLetterIds: [deadLetterId],
      originalJobIds: deadLetter.jobId ? [deadLetter.jobId] : [],
      affectedSurfaces: deadLetter.surface ? [deadLetter.surface] : [],
      replayedCount: 0,
      skippedCount: 1,
      details: { reason: "replay_locked" },
    });

    logger.warn("Recommendation dead-letter replay skipped due to replay lock", {
      deadLetterId,
      actorId,
    });

    return {
      deadLetterId,
      replayed: false,
      skipped: true,
      reason: "replay_locked",
    };
  }

  try {
    const jobs = await enqueueRecommendationRefreshJobs({
      userId: deadLetter.userId,
      surfaces: [deadLetter.surface],
      reason: "dead_letter_replay",
      version: deadLetter.data?.version || null,
    });

    logger.info("Recommendation dead-letter replay succeeded", {
      deadLetterId,
      actorId,
      jobs,
    });

    await createReplayAudit({
      actorId,
      replayType: "single",
      status: jobs.length > 0 ? "success" : "skipped",
      deadLetterIds: [deadLetterId],
      originalJobIds: deadLetter.jobId ? [deadLetter.jobId] : [],
      affectedSurfaces: deadLetter.surface ? [deadLetter.surface] : [],
      replayedCount: jobs.length > 0 ? 1 : 0,
      skippedCount: jobs.length > 0 ? 0 : 1,
      details: {
        jobs,
        replayReason: jobs.length ? "dead_letter_replay" : "job_deduped",
      },
    });

    return {
      deadLetterId,
      replayed: jobs.length > 0,
      jobs,
      skipped: jobs.length === 0,
      reason: jobs.length ? null : "job_deduped",
    };
  } catch (error) {
    await createReplayAudit({
      actorId,
      replayType: "single",
      status: "failed",
      deadLetterIds: [deadLetterId],
      originalJobIds: deadLetter.jobId ? [deadLetter.jobId] : [],
      affectedSurfaces: deadLetter.surface ? [deadLetter.surface] : [],
      replayedCount: 0,
      skippedCount: 0,
      details: {
        userId: deadLetter.userId,
        surface: deadLetter.surface,
      },
      failureReason: error.message,
    });

    logger.error("Recommendation dead-letter replay failed", {
      deadLetterId,
      actorId,
      error: error.message,
    });
    throw error;
  }
};

const replayDeadLetters = async ({ surface = null, jobName = null, actorId = null, limit = 20 }) => {
  if (!isRecommendationRefreshAvailable()) {
    throw new ApiError(503, "Recommendation refresh queue is disabled");
  }

  const normalizedSurface = surface && SURFACE_TO_JOB_NAME[surface] ? surface : null;
  const normalizedJobName = normalizeJobName(jobName);
  const replayLockKey = buildBulkReplayLockKey({
    surface: normalizedSurface || "all",
    jobName: normalizedJobName || "all",
  });

  const replayLock = await redis.set(replayLockKey, String(Date.now()), "EX", BULK_REPLAY_LOCK_TTL_SECONDS, "NX");
  if (!replayLock) {
    await createReplayAudit({
      actorId,
      replayType: "bulk",
      status: "skipped",
      deadLetterIds: [],
      originalJobIds: [],
      filters: {
        surface: normalizedSurface,
        jobName: normalizedJobName,
        limit: Math.min(Math.max(Number(limit) || 20, 1), MAX_BULK_REPLAY_LIMIT),
      },
      replayedCount: 0,
      skippedCount: 0,
      details: { reason: "bulk_replay_locked" },
    });

    logger.warn("Recommendation bulk dead-letter replay skipped due to replay lock", {
      actorId,
      surface: normalizedSurface,
      jobName: normalizedJobName,
    });

    return {
      replayed: [],
      skipped: [],
      reason: "bulk_replay_locked",
    };
  }

  const safeLimit = Math.min(Math.max(Number(limit) || 20, 1), MAX_BULK_REPLAY_LIMIT);
  const deadLetters = await getDeadLetters(200);
  const filtered = deadLetters
    .filter((item) => (normalizedSurface ? item.surface === normalizedSurface : true))
    .filter((item) => (normalizedJobName ? item.name === normalizedJobName : true))
    .slice(0, safeLimit);

  logger.info("Recommendation bulk dead-letter replay requested", {
    actorId,
    surface: normalizedSurface,
    jobName: normalizedJobName,
    candidates: filtered.length,
    limit: safeLimit,
  });

  const replayed = [];
  const skipped = [];

  for (const item of filtered) {
    const result = await replayDeadLetterById({
      deadLetterId: item.id,
      actorId,
    });

    if (result.replayed) replayed.push(result);
    else skipped.push(result);
  }

  logger.info("Recommendation bulk dead-letter replay finished", {
    actorId,
    surface: normalizedSurface,
    jobName: normalizedJobName,
    replayedCount: replayed.length,
    skippedCount: skipped.length,
  });

  await createReplayAudit({
    actorId,
    replayType: "bulk",
    status: replayed.length && skipped.length ? "partial" : replayed.length ? "success" : "skipped",
    deadLetterIds: filtered.map((item) => item.id),
    originalJobIds: filtered.map((item) => item.jobId).filter(Boolean),
    filters: {
      surface: normalizedSurface,
      jobName: normalizedJobName,
      limit: safeLimit,
    },
    affectedSurfaces: [...new Set(filtered.map((item) => item.surface).filter(Boolean))],
    replayedCount: replayed.length,
    skippedCount: skipped.length,
    details: {
      replayed,
      skipped,
    },
  });

  return {
    replayed,
    skipped,
    filters: {
      surface: normalizedSurface,
      jobName: normalizedJobName,
      limit: safeLimit,
    },
  };
};

const formatMetricLine = (name, value, labels = null) => {
  const normalizedValue = Number.isFinite(Number(value)) ? Number(value) : 0;
  if (!labels || !Object.keys(labels).length) return `${name} ${normalizedValue}`;

  const labelString = Object.entries(labels)
    .map(([key, labelValue]) => `${key}="${String(labelValue).replace(/"/g, '\\"')}"`)
    .join(",");

  return `${name}{${labelString}} ${normalizedValue}`;
};

const getRecommendationRefreshPrometheusMetrics = async () => {
  if (!isRecommendationRefreshAvailable()) {
    return [
      "# recommendation refresh queue disabled",
      'recommendation_refresh_queue_enabled{queue="recommendation-refresh"} 0',
    ].join("\n");
  }

  const [counts, metrics, workerStatus] = await Promise.all([
    getRecommendationRefreshQueue().getJobCounts("waiting", "active", "completed", "failed", "delayed"),
    getRefreshMetricsSnapshot(),
    getWorkerStatus(),
  ]);

  const lines = [
    "# HELP recommendation_refresh_queue_jobs Queue job counts by status",
    "# TYPE recommendation_refresh_queue_jobs gauge",
    formatMetricLine("recommendation_refresh_queue_jobs", counts.waiting || 0, { status: "waiting" }),
    formatMetricLine("recommendation_refresh_queue_jobs", counts.active || 0, { status: "active" }),
    formatMetricLine("recommendation_refresh_queue_jobs", counts.completed || 0, { status: "completed" }),
    formatMetricLine("recommendation_refresh_queue_jobs", counts.failed || 0, { status: "failed" }),
    formatMetricLine("recommendation_refresh_queue_jobs", counts.delayed || 0, { status: "delayed" }),
    "# HELP recommendation_refresh_dead_letter_total Total dead-letter refresh jobs",
    "# TYPE recommendation_refresh_dead_letter_total counter",
    formatMetricLine("recommendation_refresh_dead_letter_total", metrics.global.deadLetterCount),
    "# HELP recommendation_refresh_average_processing_latency_ms Average processing latency in milliseconds",
    "# TYPE recommendation_refresh_average_processing_latency_ms gauge",
    formatMetricLine("recommendation_refresh_average_processing_latency_ms", metrics.global.averageProcessingLatencyMs),
    "# HELP recommendation_refresh_job_total Recommendation refresh counters by job type and result",
    "# TYPE recommendation_refresh_job_total counter",
  ];

  Object.entries(metrics.perJobType).forEach(([jobType, jobMetrics]) => {
    lines.push(formatMetricLine("recommendation_refresh_job_total", jobMetrics.queuedCount, { job_type: jobType, result: "queued" }));
    lines.push(formatMetricLine("recommendation_refresh_job_total", jobMetrics.completedCount, { job_type: jobType, result: "completed" }));
    lines.push(formatMetricLine("recommendation_refresh_job_total", jobMetrics.failedCount, { job_type: jobType, result: "failed" }));
  });

  lines.push("# HELP recommendation_refresh_worker_stale Worker stale state (1 = stale/offline, 0 = healthy)");
  lines.push("# TYPE recommendation_refresh_worker_stale gauge");
  lines.push(formatMetricLine("recommendation_refresh_worker_stale", workerStatus.stale ? 1 : 0));

  return `${lines.join("\n")}\n`;
};

const getReplayAuditHistory = async (query = {}) => {
  const { page, limit, skip } = getPagination(query);
  const filter = {};

  if (query.status) filter.status = query.status;
  if (query.replayType) filter.replayType = query.replayType;
  if (query.actor) filter.actor = query.actor;
  if (query.surface) {
    filter.$or = [{ "filters.surface": query.surface }, { affectedSurfaces: query.surface }];
  }
  if (query.deadLetterId) filter.deadLetterIds = query.deadLetterId;
  if (query.jobId) filter.originalJobIds = query.jobId;
  if (query.fromDate || query.toDate) {
    filter.createdAt = {};
    if (query.fromDate) filter.createdAt.$gte = new Date(query.fromDate);
    if (query.toDate) filter.createdAt.$lte = new Date(query.toDate);
  }

  const [rows, total] = await Promise.all([
    RecommendationReplayAudit.find(filter)
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .populate("actor", "username fullName role"),
    RecommendationReplayAudit.countDocuments(filter),
  ]);

  return {
    items: rows,
    meta: {
      page,
      limit,
      total,
      totalPages: Math.max(Math.ceil(total / limit), 1),
      appliedFilters: {
        status: query.status || null,
        replayType: query.replayType || null,
        actor: query.actor || null,
        surface: query.surface || null,
        fromDate: query.fromDate || null,
        toDate: query.toDate || null,
        deadLetterId: query.deadLetterId || null,
        jobId: query.jobId || null,
      },
    },
  };
};

module.exports = {
  DEFAULT_REFRESH_LIMIT,
  DEFAULT_REFRESH_PAGE,
  enqueueRecommendationRefreshJobs,
  getDeadLetterDetail,
  getRecommendationRefreshQueueHealth,
  getRecommendationRefreshPrometheusMetrics,
  getReplayAuditHistory,
  replayDeadLetterById,
  replayDeadLetters,
};
