require("dotenv").config();

const { connectDatabase, closeDatabaseConnection } = require("../config/db");
const { closeRedisConnection } = require("../config/redis");
const { closeRecommendationQueueResources } = require("../queues/recommendationRefreshQueue");
const { runMaintenanceCleanup } = require("../services/maintenanceService");
const logger = require("../utils/logger");

const run = async () => {
  try {
    await connectDatabase();
    const summary = await runMaintenanceCleanup();
    logger.info("Maintenance cleanup script finished", summary);
    process.exitCode = 0;
  } catch (error) {
    logger.error("Maintenance cleanup script failed", { message: error.message });
    process.exitCode = 1;
  } finally {
    await Promise.allSettled([
      closeRecommendationQueueResources(),
      closeRedisConnection(),
      closeDatabaseConnection(),
    ]);
  }
};

run();
