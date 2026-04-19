require("dotenv").config();

const http = require("http");

const serializeError = (error) => ({
  name: error?.name || "Error",
  message: error?.message || String(error),
  stack: error?.stack || null,
  code: error?.code || null,
  errno: error?.errno || null,
  syscall: error?.syscall || null,
  hostname: error?.hostname || null,
});

let connectDatabase;
let closeDatabaseConnection;
let getDatabaseStatus;
let env;
let app;
let logger;
let initSocketServer;
let closeSocketServer;
let registerChatSocket;
let connectRedis;
let getRedisStatus;
let closeRedisConnection;
let isRedisReady;
let closeRecommendationQueueResources;
let isFirebaseEnabled;
let isCloudinaryConfigured;
let ensureBootstrapLoginUser;
let startMaintenanceCleanupScheduler;
let stopMaintenanceCleanupScheduler;

try {
  ({ connectDatabase, closeDatabaseConnection, getDatabaseStatus } = require("./src/config/db"));
  ({ env } = require("./src/config/env"));
  app = require("./src/app");
  logger = require("./src/utils/logger");
  ({ initSocketServer, closeSocketServer } = require("./src/socket/socketServer"));
  ({ registerChatSocket } = require("./src/socket/chatSocket"));
  ({ connectRedis, getRedisStatus, closeRedisConnection, isRedisReady } = require("./src/config/redis"));
  ({ closeRecommendationQueueResources } = require("./src/queues/recommendationRefreshQueue"));
  ({ isFirebaseEnabled } = require("./src/config/firebaseAdmin"));
  ({ isCloudinaryConfigured } = require("./src/config/cloudinary"));
  ({ ensureBootstrapLoginUser } = require("./src/services/bootstrapAuthService"));
  ({ startMaintenanceCleanupScheduler, stopMaintenanceCleanupScheduler } = require("./src/services/maintenanceService"));
} catch (error) {
  console.error(
    JSON.stringify({
      level: "error",
      message: "Startup fatal error",
      phase: "module_load",
      ...serializeError(error),
    })
  );
  process.exit(1);
}

let httpServer = null;
let isShuttingDown = false;
let startupPhase = "module_loaded";

const PORT = Number(process.env.PORT || 10000);
const HOST = process.env.HOST || "0.0.0.0";

const setStartupPhase = (phase, extra = {}) => {
  startupPhase = phase;
  logger.info("Startup phase", {
    phase,
    ...extra,
  });
};

const logStartupFailure = (error, phase = startupPhase) => {
  logger.error("Startup fatal error", {
    phase,
    ...serializeError(error),
  });
};

process.on("uncaughtException", (error) => {
  logStartupFailure(error, startupPhase);
  process.exit(1);
});

process.on("unhandledRejection", (reason) => {
  const error = reason instanceof Error ? reason : new Error(String(reason));
  logStartupFailure(error, startupPhase);
  process.exit(1);
});

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
    queueEnabled: env.queueEnabled,
    workerRequired: env.workerRequired,
    recommendationWorkerEnabled: env.recommendationWorkerEnabled,
    recommendationMode: env.recommendationMode,
    cacheMode: env.cacheMode,
    bootstrapUserEnabled: env.enableBootstrapUser,
    firebaseEnabled: isFirebaseEnabled(),
    cloudinaryConfigured: isCloudinaryConfigured(),
  });

  if (mongo.mode === "memory-fallback") {
    logger.warn("Running with MongoDB memory fallback; data is temporary and will be lost on restart");
  }

  if (!env.redisRequired && !isRedisReady()) {
    if (env.recommendationMode === "db-direct" && !env.workerRequired) {
      logger.warn("Redis optional support is unavailable; closed-alpha db-direct mode will continue without queue/cache support");
    } else {
      logger.warn("Running in degraded mode: Redis is unavailable but not required");
    }
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
      ...serializeError(error),
    });
    clearTimeout(forceShutdownTimer);
    process.exit(1);
  }
};

const listen = async () =>
  new Promise((resolve, reject) => {
    const onError = (error) => {
      httpServer.off("listening", onListening);
      reject(error);
    };

    const onListening = () => {
      httpServer.off("error", onError);
      resolve();
    };

    httpServer.once("error", onError);
    httpServer.once("listening", onListening);
    httpServer.listen(PORT, HOST);
  });

const startServer = async () => {
  try {
    setStartupPhase("startup_begin", {
      nodeEnv: env.nodeEnv,
      nodeVersion: process.version,
      port: PORT,
      host: HOST,
      renderService: Boolean(process.env.RENDER),
    });

    setStartupPhase("env_validated", {
      port: PORT,
      host: HOST,
      redisRequired: env.redisRequired,
      mongoFallbackAllowed: env.allowMemoryDbFallback,
    });

    setStartupPhase("before_connectDatabase");
    await connectDatabase();

    setStartupPhase("after_connectDatabase", {
      database: getDatabaseStatus(),
    });

    setStartupPhase("before_connectRedis");
    await connectRedis();

    setStartupPhase("after_connectRedis", {
      redis: getRedisStatus(),
    });

    setStartupPhase("before_bootstrap_user");
    const bootstrapUser = await ensureBootstrapLoginUser();
    if (!bootstrapUser) {
      logger.info("Skipping bootstrap user creation");
    }

    setStartupPhase("before_create_http_server");
    httpServer = http.createServer(app);

    setStartupPhase("before_socket_hooks");
    const io = initSocketServer(httpServer);
    registerChatSocket(io);

    setStartupPhase("before_maintenance_hooks");
    startMaintenanceCleanupScheduler();
    logStartupSummary();

    setStartupPhase("before_http_listen", {
      port: PORT,
      host: HOST,
    });

    await listen();

    setStartupPhase("after_http_listen", {
      port: PORT,
      host: HOST,
    });
    logger.info(`API running on http://${HOST}:${PORT}`);
  } catch (error) {
    logStartupFailure(error);
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
