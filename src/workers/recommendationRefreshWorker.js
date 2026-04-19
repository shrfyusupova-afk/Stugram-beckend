const { Worker } = require("bullmq");

const { env } = require("../config/env");
const logger = require("../utils/logger");
const recommendationService = require("../services/recommendationService");
const profileSuggestionService = require("../services/profileSuggestionService");
const {
  recordCompletedMetric,
  recordDeadLetter,
  recordFailedMetric,
  recordRetryMetric,
  updateWorkerStatus,
} = require("../services/recommendationRefreshMetricsService");
const recommendationQueue = require("../queues/recommendationRefreshQueue");

const WORKER_CONCURRENCY = 4;
const WORKER_ID = `recommendation-refresh-worker:${process.pid}`;

const processRecommendationRefreshJob = async (job) => {
  const { userId, surface, page = 1, limit = 20, version = null, reason = "queued_refresh" } = job.data;

  logger.info("Recommendation refresh job processing started", {
    jobId: job.id,
    userId,
    surface,
    page,
    limit,
    version,
    reason,
    attempt: job.attemptsMade + 1,
  });

  if (surface === "feed" || surface === "reels") {
    const result = await recommendationService.getPersonalizedFeed(
      userId,
      { page, limit },
      { surface, bypassCache: true, expectedVersion: version }
    );

    logger.info("Recommendation refresh job processed successfully", {
      jobId: job.id,
      userId,
      surface,
      page,
      limit,
      items: result.items.length,
    });

    return {
      userId,
      surface,
      page,
      limit,
      items: result.items.length,
    };
  }

  if (surface === "profiles") {
    const result = await profileSuggestionService.getProfileSuggestions(
      userId,
      { page, limit },
      { bypassCache: true, expectedVersion: version }
    );

    logger.info("Recommendation refresh job processed successfully", {
      jobId: job.id,
      userId,
      surface,
      page,
      limit,
      items: result.items.length,
    });

    return {
      userId,
      surface,
      page,
      limit,
      items: result.items.length,
    };
  }

  throw new Error(`Unsupported recommendation refresh surface: ${surface}`);
};

const createRecommendationRefreshWorker = () => {
  const queueResources = recommendationQueue.initRecommendationRefreshQueueResources();
  const recommendationRefreshConnection = recommendationQueue.getRecommendationRefreshConnection();
  const recommendationRefreshQueueEvents = recommendationQueue.getRecommendationRefreshQueueEvents();
  const recommendationRefreshQueue = recommendationQueue.getRecommendationRefreshQueue();

  if (!recommendationQueue.recommendationQueueConfigured || !queueResources.ready || !recommendationRefreshConnection || !recommendationRefreshQueueEvents || !recommendationRefreshQueue) {
    throw new Error("Recommendation refresh worker is disabled because Redis queue support is unavailable");
  }

  const worker = new Worker(recommendationQueue.RECOMMENDATION_REFRESH_QUEUE_NAME, processRecommendationRefreshJob, {
    connection: recommendationRefreshConnection.duplicate(),
    prefix: env.redisPrefix,
    concurrency: WORKER_CONCURRENCY,
  });

  updateWorkerStatus({ workerId: WORKER_ID, status: "idle" }).catch(() => {});

  worker.on("active", (job) => {
    job.updateData({
      ...job.data,
      startedAt: new Date().toISOString(),
    }).catch(() => {});

    logger.info("Recommendation refresh worker picked job", {
      jobId: job.id,
      name: job.name,
      userId: job.data?.userId,
      surface: job.data?.surface,
      attempt: job.attemptsMade + 1,
    });

    updateWorkerStatus({
      workerId: WORKER_ID,
      status: "active",
      currentJob: {
        id: job.id,
        name: job.name,
        userId: job.data?.userId,
        surface: job.data?.surface,
      },
    }).catch(() => {});
  });

  worker.on("completed", (job, result) => {
    const latencyMs = job.processedOn && job.finishedOn ? job.finishedOn - job.processedOn : 0;

    recordCompletedMetric({
      jobName: job.name,
      latencyMs,
    }).catch(() => {});

    logger.info("Recommendation refresh worker completed job", {
      jobId: job.id,
      name: job.name,
      latencyMs,
      result,
    });

    updateWorkerStatus({
      workerId: WORKER_ID,
      status: "idle",
      currentJob: null,
      meta: {
        lastCompletedJobId: job.id,
        lastSuccessAt: new Date().toISOString(),
      },
    }).catch(() => {});
  });

  worker.on("failed", (job, error) => {
    const maxAttempts = job?.opts?.attempts || 1;
    const latencyMs = job?.processedOn && job?.finishedOn ? job.finishedOn - job.processedOn : 0;
    const willRetry = (job?.attemptsMade || 0) < maxAttempts;

    logger.error("Recommendation refresh worker failed job", {
      jobId: job?.id,
      name: job?.name,
      userId: job?.data?.userId,
      surface: job?.data?.surface,
      attemptsMade: job?.attemptsMade,
      maxAttempts,
      willRetry,
      error: error.message,
    });

    recordFailedMetric({
      jobName: job?.name || "unknown",
      latencyMs,
      error,
      finalFailure: !willRetry,
    }).catch(() => {});

    if (willRetry) {
      recordRetryMetric({
        jobName: job?.name || "unknown",
        attempt: job?.attemptsMade || 0,
        maxAttempts,
        error,
      }).catch(() => {});

      logger.warn("Recommendation refresh worker will retry job", {
        jobId: job?.id,
        name: job?.name,
        userId: job?.data?.userId,
        surface: job?.data?.surface,
        nextAttempt: (job?.attemptsMade || 0) + 1,
        maxAttempts,
      });
    } else if (job) {
      recordDeadLetter({
        job,
        error,
        latencyMs,
      }).catch(() => {});
    }

    updateWorkerStatus({
      workerId: WORKER_ID,
      status: willRetry ? "retrying" : "idle",
      currentJob: willRetry
        ? {
            id: job?.id,
            name: job?.name,
            userId: job?.data?.userId,
            surface: job?.data?.surface,
          }
        : null,
      meta: {
        lastFailureAt: new Date().toISOString(),
        lastFailedJobId: job?.id || null,
        willRetry,
      },
    }).catch(() => {});
  });

  recommendationRefreshQueueEvents.on("error", (error) => {
    logger.error("Recommendation refresh queue events error", { error: error.message });
  });

  return worker;
};

module.exports = {
  createRecommendationRefreshWorker,
};
