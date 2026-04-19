require("dotenv").config();

const http = require("http");

const { connectDatabase, closeDatabaseConnection, getDatabaseStatus } = require("./src/config/db");
const { env } = require("./src/config/env");
const app = require("./src/app");
const logger = require("./src/utils/logger");
const { initSocketServer, closeSocketServer } = require("./src/socket/socketServer");
const { registerChatSocket } = require("./src/socket/chatSocket");
const { connectRedis, getRedisStatus, closeRedisConnection, isRedisReady } = require("./src/config/redis");
const { closeRecommendationQueueResources } = require("./src/queues/recommendationRefreshQueue");
const { isFirebaseEnabled } = require("./src/config/firebaseAdmin");
const { isCloudinaryConfigured } = require("./src/config/cloudinary");
const { ensureBootstrapLoginUser } = require("./src/services/bootstrapAuthService");
const { startMaintenanceCleanupScheduler, stopMaintenanceCleanupScheduler } = require("./src/services/maintenanceService");

let httpServer = null;
let isShuttingDown = false;

const logStartupSummary = () => {
  const mongo = getDatabaseStatus();
  const redis = getRedisStatus();
  const atlasUri = mongo.atlasReport?.uri || {};

  logger.info("Startup dependency summary", {
    nodeEnv: env.nodeEnv,
    atlasHost: atlasUri.host || null,
    atlasDbName: atlasUri.dbName || null,
    atlasMode: mongo.mode,
    mongoConnected: mongo.connected,
    mongoMode: mongo.mode,
    mongoFallbackAllowed: mongo.fallbackAllowed,
    mongoLastAtlasFailure: mongo.lastAtlasFailure,
    mongoServerSelectionTimeoutMs: env.mongoServerSelectionTimeoutMs,
    mongoConnectTimeoutMs: env.mongoConnectTimeoutMs,
    mongoSocketTimeoutMs: env.mongoSocketTimeoutMs,
    mongoForceIpv4: env.mongoForceIpv4,
    mongoDnsServers: env.mongoDnsServers.length ? env.mongoDnsServers : null,
    nodeVersion: process.version,
    opensslVersion: process.versions.openssl || null,
    redisRequired: env.redisRequired,
    redisReady: redis.ready,
    redisMode: redis.mode,
    redisStatus: redis.status,
    redisHost: redis.host,
    redisPort: redis.port,
    redisUrlConfigured: redis.urlConfigured,
    redisConfigSource: redis.configSource,
    redisTlsEnabled: redis.tlsEnabled,
    redisTlsRejectUnauthorized: redis.tlsRejectUnauthorized,
    redisPasswordConfigured: redis.passwordConfigured,
    redisUsernameConfigured: redis.usernameConfigured,
    redisRetryPolicy: redis.retryPolicy,
    redisLastFailure: redis.lastFailure,
    bootstrapUserEnabled: env.enableBootstrapUser,
    firebaseEnabled: isFirebaseEnabled(),
    cloudinaryConfigured: isCloudinaryConfigured(),
  });

  if (mongo.mode === "memory-fallback") {
    logger.warn("Running with MongoDB memory fallback; data is temporary and will be lost on restart");
  }

  if (!env.redisRequired && !isRedisReady()) {
    logger.warn("Running in degraded mode: Redis is unavailable but not required");
  }
};

const shutdown = async (signal) => {
  if (isShuttingDown) return;
  isShuttingDown = true;

  logger.warn(`Received ${signal}, starting graceful shutdown`);
  stopMaintenanceCleanupScheduler();

  const forceShutdownTimer = setTimeout(() => {
    logger.error("Graceful shutdown timed out, forcing exit");
    process.exit(1);
  }, 10000);

  forceShutdownTimer.unref?.();

  try {
    if (httpServer) {
      await new Promise((resolve, reject) => {
        httpServer.close((error) => {
          if (error) {
            reject(error);
            return;
          }
          resolve();
        });
      });
      logger.info("HTTP server closed");
    }

    await closeSocketServer();
    logger.info("Socket server closed");

    await Promise.allSettled([
      closeRecommendationQueueResources(),
      closeRedisConnection(),
    ]).then((results) => {
      const queueResult = results[0];
      const redisResult = results[1];

      if (queueResult.status === "fulfilled") {
        logger.info("Recommendation queue Redis resources closed");
      } else {
        logger.warn("Recommendation queue Redis shutdown failed", {
          message: queueResult.reason?.message || "Unknown error",
        });
      }

      if (redisResult.status === "fulfilled") {
        logger.info("Redis connection closed");
      } else {
        logger.warn("Redis shutdown failed", {
          message: redisResult.reason?.message || "Unknown error",
        });
      }
    });

    await closeDatabaseConnection();
    logger.info("MongoDB connection closed");

    clearTimeout(forceShutdownTimer);
    process.exit(0);
  } catch (error) {
    logger.error("Graceful shutdown failed", {
      message: error.message,
    });
    clearTimeout(forceShutdownTimer);
    process.exit(1);
  }
};

const startServer = async () => {
  try {
    await connectDatabase();
    await connectRedis();
    const bootstrapUser = await ensureBootstrapLoginUser();
    if (!bootstrapUser) {
      logger.info("Skipping bootstrap user creation");
    }

    httpServer = http.createServer(app);
    const io = initSocketServer(httpServer);
    registerChatSocket(io);
    startMaintenanceCleanupScheduler();
    logStartupSummary();

    const host = process.env.HOST || "0.0.0.0";
    httpServer.listen(env.port, host, () => {
      logger.info(`API running on http://${host}:${env.port}`);
    });
  } catch (error) {
    logger.error("Failed to start server", {
      message: error.message,
    });
    process.exit(1);
  }
};

process.on("SIGINT", () => {
  shutdown("SIGINT");
});

process.on("SIGTERM", () => {
  shutdown("SIGTERM");
});

startServer();
