require("dotenv").config();

const { createRecommendationRefreshWorker } = require("./recommendationRefreshWorker");
const { connectDatabase } = require("../config/database");
const { connectRedis, getRedisStatus } = require("../config/redis");
const { initRecommendationRefreshQueueResources } = require("../queues/recommendationRefreshQueue");
const logger = require("../utils/logger");
const { updateWorkerStatus } = require("../services/recommendationRefreshMetricsService");

const WORKER_ID = `recommendation-refresh-worker:${process.pid}`;

const start = async () => {
  await connectDatabase();
  await connectRedis();

  const redis = getRedisStatus();
  if (!redis.connected) {
    throw new Error(`Redis is not connected; recommendation refresh worker cannot start in ${redis.mode} mode`);
  }

  initRecommendationRefreshQueueResources();
  createRecommendationRefreshWorker();
  logger.info("Recommendation refresh worker started");

  setInterval(() => {
    updateWorkerStatus({
      workerId: WORKER_ID,
      status: "idle",
    }).catch(() => {});
  }, 15 * 1000).unref();
};

start().catch((error) => {
  logger.error("Failed to start recommendation refresh worker", { error: error.message });
  process.exit(1);
});
