const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const cookieParser = require("cookie-parser");
const morgan = require("morgan");

const { env } = require("./config/env");
const { getDatabaseStatus, isPrimaryDatabaseReady, isMemoryFallbackReady } = require("./config/db");
const { getRedisStatus, isRedisReady } = require("./config/redis");
const { apiLimiter } = require("./middlewares/rateLimiter");
const { assignRequestContext, logRequestCompletion } = require("./middlewares/observability");
const { notFoundHandler, errorHandler } = require("./middlewares/errorHandler");
const routes = require("./routes");
const { getFirebaseStatus } = require("./config/firebaseAdmin");
const { isCloudinaryConfigured } = require("./config/cloudinary");
const recommendationRefreshService = require("./services/recommendationRefreshService");

const app = express();

const getQueueHealthSnapshot = async () => {
  try {
    return await recommendationRefreshService.getRecommendationRefreshQueueHealth();
  } catch (error) {
    const redis = getRedisStatus();
    return {
      redis,
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
        error: error.message,
      },
      worker: null,
      metrics: null,
    };
  }
};

app.use(
  cors({
    origin: env.clientUrl,
    credentials: true,
  })
);
app.use(helmet());
app.use(assignRequestContext);
app.use(apiLimiter);
app.use(morgan(env.nodeEnv === "production" ? "combined" : "dev"));
app.use(express.json({ limit: "2mb" }));
app.use(express.urlencoded({ extended: true }));
app.use(cookieParser());
app.use(logRequestCompletion);

app.get("/health", (_req, res) => {
  Promise.all([Promise.resolve(getDatabaseStatus()), Promise.resolve(getRedisStatus()), getQueueHealthSnapshot(), Promise.resolve(getFirebaseStatus())]).then(
    ([database, redis, queueHealth, pushStatus]) => {
      res.status(200).json({
        success: true,
        message: "Server is alive",
        data: {
          environment: env.nodeEnv,
          mongoConnected: database.connected,
          mongoMode: database.mode,
          atlasReport: database.atlasReport,
          lastAtlasFailure: database.lastAtlasFailure,
          database,
          redisConnected: redis.connected,
          redisMode: redis.mode,
          redisHost: redis.host,
          redisPort: redis.port,
          redisRequired: redis.required,
          redisTlsEnabled: redis.tlsEnabled,
          redisConfigSource: redis.configSource,
          redis,
          queueHealth,
          cacheMode: redis.connected ? "enabled" : redis.required ? "required-failed" : "optional-degraded",
          pushEnabled: pushStatus.enabled,
          cloudinaryConfigured: isCloudinaryConfigured(),
        },
        meta: null,
      });
    }
  );
});

app.get("/livez", (_req, res) => {
  res.status(200).json({
    success: true,
    message: "Server is alive",
    data: {
      environment: env.nodeEnv,
    },
    meta: null,
  });
});

app.get("/readyz", (_req, res) => {
  Promise.all([Promise.resolve(getDatabaseStatus()), Promise.resolve(getRedisStatus()), getQueueHealthSnapshot()]).then(
    ([database, redis, queueHealth]) => {
      const mongoReady =
        env.nodeEnv === "production"
          ? isPrimaryDatabaseReady()
          : isPrimaryDatabaseReady() || isMemoryFallbackReady();
      const redisReady = env.redisRequired ? isRedisReady() : true;
      const ready = mongoReady && redisReady;

      res.status(ready ? 200 : 503).json({
        success: ready,
        message: ready ? "Server is ready" : "Server is not ready",
        data: {
          environment: env.nodeEnv,
          mongoConnected: database.connected,
          mongoMode: database.mode,
          atlasReport: database.atlasReport,
          lastAtlasFailure: database.lastAtlasFailure,
          database,
          redisConnected: redis.connected,
          redisMode: redis.mode,
          redisHost: redis.host,
          redisPort: redis.port,
          redisRequired: redis.required,
          redisTlsEnabled: redis.tlsEnabled,
          redisConfigSource: redis.configSource,
          redis,
          queueHealth,
          cacheMode: redis.connected ? "enabled" : redis.required ? "required-failed" : "optional-degraded",
          pushEnabled: getFirebaseStatus().enabled,
          cloudinaryConfigured: isCloudinaryConfigured(),
        },
        meta: null,
      });
    }
  );
});

app.get("/health/push", (_req, res) => {
  const pushStatus = getFirebaseStatus();
  res.status(200).json({
    success: true,
    message: "Push health fetched successfully",
    data: {
      pushEnabled: pushStatus.enabled,
      reason: pushStatus.reason,
      credentialSource: pushStatus.credentialSource,
      missingFields: pushStatus.missingFields,
      projectId: pushStatus.projectId,
    },
    meta: null,
  });
});

app.use("/api/v1", routes);

app.use(notFoundHandler);
app.use(errorHandler);

module.exports = app;
