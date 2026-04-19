const IORedis = require("ioredis");
const { Queue, QueueEvents } = require("bullmq");

const { env } = require("../config/env");
const { buildRedisClientOptions, isRedisReady, REDIS_RETRY_POLICY } = require("../config/redis");
const logger = require("../utils/logger");
const RECOMMENDATION_REFRESH_QUEUE_NAME = "recommendation-refresh";
const recommendationQueueConfigured = Boolean(env.queueEnabled && env.recommendationWorkerEnabled && env.redisUrl);

let recommendationRefreshConnection = null;
let recommendationRefreshEventsConnection = null;
let recommendationRefreshQueue = null;
let recommendationRefreshQueueEvents = null;

const getRecommendationRefreshQueue = () => recommendationRefreshQueue;
const getRecommendationRefreshQueueEvents = () => recommendationRefreshQueueEvents;
const getRecommendationRefreshConnection = () => recommendationRefreshConnection;
const getRecommendationRefreshEventsConnection = () => recommendationRefreshEventsConnection;

const initRecommendationRefreshQueueResources = () => {
  if (!env.queueEnabled || !env.recommendationWorkerEnabled) {
    return {
      initialized: false,
      ready: false,
      disabled: true,
      mode: "disabled-for-closed-alpha",
    };
  }

  if (!recommendationQueueConfigured || !isRedisReady()) {
    return {
      initialized: false,
      ready: false,
    };
  }

  if (recommendationRefreshQueue && recommendationRefreshQueueEvents && recommendationRefreshConnection) {
    return {
      initialized: true,
      ready: true,
    };
  }

  recommendationRefreshConnection = new IORedis(env.redisUrl, buildRedisClientOptions({ purpose: "bullmq" }));
  recommendationRefreshEventsConnection = recommendationRefreshConnection.duplicate();

  recommendationRefreshConnection.on("error", (error) => {
    logger.warn("Recommendation refresh queue Redis connection error", {
      message: error.message,
      retryPolicy: REDIS_RETRY_POLICY,
    });
  });

  recommendationRefreshQueue = new Queue(RECOMMENDATION_REFRESH_QUEUE_NAME, {
    connection: recommendationRefreshConnection,
    defaultJobOptions: {
      attempts: 4,
      backoff: {
        type: "exponential",
        delay: 3000,
      },
      removeOnComplete: {
        age: 60 * 60,
        count: 1000,
      },
      removeOnFail: {
        age: 24 * 60 * 60,
        count: 5000,
      },
    },
    prefix: env.redisPrefix,
  });

  recommendationRefreshQueueEvents = new QueueEvents(RECOMMENDATION_REFRESH_QUEUE_NAME, {
    connection: recommendationRefreshEventsConnection,
    prefix: env.redisPrefix,
  });

  return {
    initialized: true,
    ready: true,
  };
};

const isRecommendationQueueReady = () => Boolean(recommendationRefreshQueue) && isRedisReady();

const closeRecommendationQueueResources = async () => {
  if (!recommendationQueueConfigured) {
    return;
  }

  await Promise.allSettled([
    recommendationRefreshQueue?.close?.(),
    recommendationRefreshQueueEvents?.close?.(),
    recommendationRefreshConnection?.quit?.(),
    recommendationRefreshEventsConnection?.quit?.(),
  ]);
};

module.exports = {
  RECOMMENDATION_REFRESH_QUEUE_NAME,
  recommendationRefreshConnection,
  recommendationRefreshEventsConnection,
  recommendationRefreshQueue,
  recommendationRefreshQueueEvents,
  recommendationQueueConfigured,
  initRecommendationRefreshQueueResources,
  getRecommendationRefreshQueue,
  getRecommendationRefreshQueueEvents,
  getRecommendationRefreshConnection,
  getRecommendationRefreshEventsConnection,
  isRecommendationQueueReady,
  closeRecommendationQueueResources,
};
