const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const cookieParser = require("cookie-parser");
const morgan = require("morgan");

const { env } = require("./config/env");
const { getDatabaseStatus, isPrimaryDatabaseReady, isMemoryFallbackReady } = require("./config/db");
const { getRedisStatus, isRedisReady } = require("./config/redis");
const { apiLimiter } = require("./middlewares/rateLimiter");
const { assignRequestId } = require("./middlewares/requestId");
const { attachResponseMeta } = require("./middlewares/responseMeta");
const { logRequestCompletion } = require("./middlewares/observability");
const { notFoundHandler, errorHandler } = require("./middlewares/errorHandler");
const routes = require("./routes");
const { getFirebaseStatus } = require("./config/firebaseAdmin");
const { isCloudinaryConfigured } = require("./config/cloudinary");
const recommendationRefreshService = require("./services/recommendationRefreshService");
const { renderPrometheusMetrics, getMetricsSnapshot } = require("./services/chatMetricsService");

const app = express();

const resolveTrustProxySetting = () => {
  const rawValue = process.env.TRUST_PROXY;
  if (rawValue == null || rawValue === "") {
    return 1;
  }

  const normalized = String(rawValue).trim().toLowerCase();
  if (normalized === "true") return true;
  if (normalized === "false") return false;
  if (/^\d+$/.test(normalized)) return Number(normalized);
  return rawValue;
};

// Production deploys behind Render/proxies must trust proxy headers so req.ip
// reflects X-Forwarded-For before any global rate limiter is evaluated.
app.set("trust proxy", resolveTrustProxySetting());

const isClosedAlphaNoWorkerMode = () =>
  env.recommendationMode === "db-direct" && (!env.queueEnabled || !env.recommendationWorkerEnabled);

const getCacheMode = (redis) => {
  if (redis.connected) return "enabled";
  if (env.cacheMode === "disabled") return "disabled";
  if (isClosedAlphaNoWorkerMode() && !redis.required) return "redis-optional-unavailable";
  return redis.required ? "required-failed" : "optional-degraded";
};

const buildRuntimeMode = (redis, queueHealth) => ({
  closedAlphaNoWorker: isClosedAlphaNoWorkerMode(),
  recommendationMode: env.recommendationMode,
  queueEnabled: env.queueEnabled,
  workerRequired: env.workerRequired,
  recommendationWorkerEnabled: env.recommendationWorkerEnabled,
  queueIntentionallyDisabled: queueHealth?.queue?.mode === "disabled-for-closed-alpha",
  cacheMode: getCacheMode(redis),
});

const buildChatControls = () => ({
  groupSendEnabled: env.chatGroupSendEnabled,
  mediaSendEnabled: env.chatMediaSendEnabled,
  replayEnabled: env.chatReplaySyncEnabled,
  realtimeEnabled: env.chatRealtimeEnabled,
  socketJoinConversationEnabled: env.socketJoinConversationEnabled,
  rateLimitStrictMode: env.chatRateLimitStrictMode,
});

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

const isInternalMonitoringRequest = (req) => {
  if (env.nodeEnv !== "production") return true;
  const internalKey = process.env.INTERNAL_METRICS_KEY;
  const suppliedKey = req.headers["x-internal-monitoring-key"];
  return Boolean(internalKey && suppliedKey === internalKey);
};

const buildPublicHealthData = ({ database, redis, queueHealth, pushStatus }) => ({
  environment: env.nodeEnv,
  mongoConnected: Boolean(database.connected),
  redisConnected: Boolean(redis.connected),
  queueReady: env.workerRequired ? queueHealth?.queue?.ready === true : true,
  cacheMode: getCacheMode(redis),
  recommendationMode: env.recommendationMode,
  pushEnabled: Boolean(pushStatus?.enabled),
  cloudinaryConfigured: isCloudinaryConfigured(),
});

// Credentialed CORS cannot use a wildcard origin. Non-browser clients (the
// Android app, curl, server-to-server) send no Origin header and are always
// allowed; browser origins must match the CLIENT_URL allowlist. In development
// only, CLIENT_URL="*" reflects any origin for convenience.
const corsOptions = {
  origin(origin, callback) {
    if (!origin) return callback(null, true);
    if (env.allowAllOrigins) return callback(null, true);
    if (env.clientOrigins.includes(origin)) return callback(null, true);
    return callback(new Error(`Origin ${origin} is not allowed by CORS`));
  },
  credentials: true,
};
app.use(cors(corsOptions));
app.use(helmet());
app.use(assignRequestId);
app.use(attachResponseMeta);
app.use(apiLimiter);
app.use(morgan(env.nodeEnv === "production" ? "combined" : "dev"));
app.use(express.json({ limit: "2mb" }));
app.use(express.urlencoded({ extended: true }));
app.use(cookieParser());
app.use(logRequestCompletion);

app.head("/", (_req, res) => {
  res.sendStatus(200);
});

app.get("/", (_req, res) => {
  res.status(200).json({
    success: true,
    message: "Stugram backend is running",
    data: {
      environment: env.nodeEnv,
    },
    meta: null,
  });
});

app.get("/health", (req, res) => {
  Promise.all([Promise.resolve(getDatabaseStatus()), Promise.resolve(getRedisStatus()), getQueueHealthSnapshot(), Promise.resolve(getFirebaseStatus())]).then(
    ([database, redis, queueHealth, pushStatus]) => {
      const publicData = buildPublicHealthData({ database, redis, queueHealth, pushStatus });
      res.status(200).json({
        success: true,
        message: "Server is alive",
        data: isInternalMonitoringRequest(req) ? {
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
          cacheMode: getCacheMode(redis),
          recommendationMode: env.recommendationMode,
          queueEnabled: env.queueEnabled,
          workerRequired: env.workerRequired,
          recommendationWorkerEnabled: env.recommendationWorkerEnabled,
          closedAlphaNoWorker: isClosedAlphaNoWorkerMode(),
          runtimeMode: buildRuntimeMode(redis, queueHealth),
          chatControls: buildChatControls(),
          pushEnabled: pushStatus.enabled,
          cloudinaryConfigured: isCloudinaryConfigured(),
        } : publicData,
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

// Telegram's inline keyboard buttons only accept http(s) URLs, so the bot
// can't link straight to a custom app scheme (stugram://...). This bridge
// page is https, gets accepted by Telegram, and immediately redirects into
// the app via the custom scheme once opened in a browser/in-app browser.
app.get("/app/telegram-register", (req, res) => {
  const code = String(req.query.code || "").replace(/[^a-zA-Z0-9]/g, "");
  const deepLink = `stugram://telegram-register?code=${code}`;

  res.status(200).type("html").send(`<!doctype html>
<html lang="uz">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Stugram</title>
<script>window.location.href = ${JSON.stringify(deepLink)};</script>
</head>
<body style="font-family:sans-serif;text-align:center;padding-top:60px;">
  <p>Stugram ilovasiga o'tilmoqda...</p>
  <p><a href="${deepLink}">Agar avtomatik ochilmasa, shu yerni bosing</a></p>
</body>
</html>`);
});

app.get("/readyz", (req, res) => {
  Promise.all([Promise.resolve(getDatabaseStatus()), Promise.resolve(getRedisStatus()), getQueueHealthSnapshot()]).then(
    ([database, redis, queueHealth]) => {
      const mongoReady =
        env.nodeEnv === "production"
          ? isPrimaryDatabaseReady()
          : isPrimaryDatabaseReady() || isMemoryFallbackReady();
      const redisReady = env.redisRequired ? isRedisReady() : true;
      const queueReady = env.workerRequired ? queueHealth?.queue?.enabled === true && queueHealth?.queue?.ready === true : true;
      const ready = mongoReady && redisReady && queueReady;
      const pushStatus = getFirebaseStatus();
      const publicData = {
        ...buildPublicHealthData({ database, redis, queueHealth, pushStatus }),
        ready,
      };

      res.status(ready ? 200 : 503).json({
        success: ready,
        message: ready ? "Server is ready" : "Server is not ready",
        data: isInternalMonitoringRequest(req) ? {
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
          cacheMode: getCacheMode(redis),
          recommendationMode: env.recommendationMode,
          queueEnabled: env.queueEnabled,
          workerRequired: env.workerRequired,
          recommendationWorkerEnabled: env.recommendationWorkerEnabled,
          closedAlphaNoWorker: isClosedAlphaNoWorkerMode(),
          runtimeMode: buildRuntimeMode(redis, queueHealth),
          chatControls: buildChatControls(),
          pushEnabled: pushStatus.enabled,
          cloudinaryConfigured: isCloudinaryConfigured(),
        } : publicData,
        meta: null,
      });
    }
  );
});

app.get("/health/push", (req, res) => {
  const pushStatus = getFirebaseStatus();
  res.status(200).json({
    success: true,
    message: "Push health fetched successfully",
    data: isInternalMonitoringRequest(req) ? {
      pushEnabled: pushStatus.enabled,
      reason: pushStatus.reason,
      credentialSource: pushStatus.credentialSource,
      missingFields: pushStatus.missingFields,
      projectId: pushStatus.projectId,
    } : {
      pushEnabled: pushStatus.enabled,
    },
    meta: null,
  });
});

app.get("/health/chat-observability", (req, res) => {
  if (!isInternalMonitoringRequest(req)) {
    return res.status(404).json({
      success: false,
      message: "Not found",
      data: null,
      meta: null,
    });
  }

  return res.status(200).json({
    success: true,
    message: "Chat observability metrics fetched successfully",
    data: {
      metrics: getMetricsSnapshot(),
    },
    meta: null,
  });
});

app.get("/metrics/chat", (req, res) => {
  if (!isInternalMonitoringRequest(req)) {
    return res.status(404).send("Not found");
  }

  res.setHeader("Content-Type", "text/plain; version=0.0.4");
  return res.status(200).send(renderPrometheusMetrics());
});

app.use("/api/v1", routes);

app.use(notFoundHandler);
app.use(errorHandler);

module.exports = app;
