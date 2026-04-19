const Redis = require("ioredis");

const { env } = require("./env");
const logger = require("../utils/logger");

const REDIS_RETRY_POLICY = "bounded-exponential-5";
const REDIS_RECONNECT_MAX_DELAY_MS = 1000;

const buildRetryStrategy = () => (times) => {
  if (times > 5) {
    return null;
  }

  return Math.min(50 * 2 ** (times - 1), REDIS_RECONNECT_MAX_DELAY_MS);
};

const buildRedisClientOptions = ({ purpose = "shared" } = {}) => {
  const options = {
    maxRetriesPerRequest: purpose === "bullmq" ? null : 1,
    enableReadyCheck: true,
    lazyConnect: true,
    retryStrategy: buildRetryStrategy(),
    reconnectOnError: () => false,
    connectTimeout: 10_000,
  };

  if (env.redisTlsEnabled) {
    options.tls = {
      rejectUnauthorized: env.redisTlsRejectUnauthorized,
    };
  }

  return options;
};

const redis = new Redis(env.redisUrl, buildRedisClientOptions({ purpose: "shared" }));

let lastRedisFailure = null;
let lastRedisConnectedAt = null;

const updateRedisFailure = (error, context = {}) => {
  lastRedisFailure = {
    name: error?.name || "Error",
    message: error?.message || "Unknown Redis error",
    code: error?.code || null,
    status: redis.status || "unknown",
    at: new Date().toISOString(),
    ...context,
  };
};

redis.on("connect", () => {
  logger.info("Redis connected", {
    host: env.redisHost,
    port: env.redisPort,
    urlConfigured: env.redisUrlConfigured,
    configSource: env.redisConfigSource,
    tlsEnabled: env.redisTlsEnabled,
  });
});

redis.on("ready", () => {
  lastRedisConnectedAt = new Date().toISOString();
  lastRedisFailure = null;
  logger.info("Redis ready", {
    mode: "connected",
    host: env.redisHost,
    port: env.redisPort,
    urlConfigured: env.redisUrlConfigured,
    tlsEnabled: env.redisTlsEnabled,
  });
});

redis.on("error", (error) => {
  const level = env.redisRequired ? "error" : "warn";
  updateRedisFailure(error);
  logger[level]("Redis error", {
    message: error.message,
    host: env.redisHost,
    port: env.redisPort,
    urlConfigured: env.redisUrlConfigured,
    tlsEnabled: env.redisTlsEnabled,
    retryPolicy: REDIS_RETRY_POLICY,
  });
});

redis.on("close", () => {
  const mode = env.redisRequired ? "required-failed" : "optional-degraded";
  logger.warn("Redis disconnected", {
    mode,
    status: redis.status || "unknown",
    host: env.redisHost,
    port: env.redisPort,
  });
});

const getRedisStatus = () => {
  const ready = redis.status === "ready";
  const disconnected = redis.status === "end" || redis.status === "wait" || redis.status === "close";
  const mode = ready
    ? "connected"
    : env.redisRequired
      ? lastRedisFailure || disconnected
        ? "required-failed"
        : "disconnected"
      : "optional-degraded";

  return {
    required: env.redisRequired,
    mode,
    status: redis.status || "unknown",
    ready,
    connected: ready,
    host: env.redisHost,
    port: env.redisPort,
    urlConfigured: env.redisUrlConfigured,
    configSource: env.redisConfigSource,
    passwordConfigured: env.redisPasswordConfigured,
    usernameConfigured: env.redisUsernameConfigured,
    tlsEnabled: env.redisTlsEnabled,
    tlsRejectUnauthorized: env.redisTlsRejectUnauthorized,
    retryPolicy: REDIS_RETRY_POLICY,
    lastFailure: lastRedisFailure,
    lastConnectedAt: lastRedisConnectedAt,
  };
};

const isRedisReady = () => redis.status === "ready";

const connectRedis = async () => {
  logger.info("Connecting to Redis", {
    required: env.redisRequired,
    host: env.redisHost,
    port: env.redisPort,
    urlConfigured: env.redisUrlConfigured,
    configSource: env.redisConfigSource,
    passwordConfigured: env.redisPasswordConfigured,
    usernameConfigured: env.redisUsernameConfigured,
    tlsEnabled: env.redisTlsEnabled,
    tlsRejectUnauthorized: env.redisTlsRejectUnauthorized,
    retryPolicy: REDIS_RETRY_POLICY,
  });

  if (redis.status === "ready") {
    return redis;
  }

  try {
    await redis.connect();
    logger.info("Redis dependency selected", {
      mode: "connected",
      status: redis.status,
      host: env.redisHost,
      port: env.redisPort,
      configSource: env.redisConfigSource,
    });
    return redis;
  } catch (error) {
    const payload = {
      name: error?.name || "Error",
      message: error?.message || "Unknown Redis connection error",
      required: env.redisRequired,
      mode: env.redisRequired ? "required-failed" : "optional-degraded",
      host: env.redisHost,
      port: env.redisPort,
      configSource: env.redisConfigSource,
      tlsEnabled: env.redisTlsEnabled,
      tlsRejectUnauthorized: env.redisTlsRejectUnauthorized,
      retryPolicy: REDIS_RETRY_POLICY,
    };

    updateRedisFailure(error, { phase: "startup" });

    if (env.redisRequired) {
      logger.error("Redis required dependency unavailable", payload);
      throw error;
    }

    logger.warn("Redis unavailable; continuing in explicit optional degraded mode", payload);
    return null;
  }
};

const closeRedisConnection = async () => {
  if (redis.status === "end") {
    return;
  }

  try {
    await redis.quit();
  } catch (error) {
    logger.warn("Redis quit failed", { message: error.message });
  }
};

module.exports = {
  redis,
  connectRedis,
  getRedisStatus,
  isRedisReady,
  closeRedisConnection,
  buildRedisClientOptions,
  REDIS_RETRY_POLICY,
};
