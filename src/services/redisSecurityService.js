const { redis } = require("../config/redis");
const { env } = require("../config/env");
const logger = require("../utils/logger");

const withPrefix = (key) => `${env.redisPrefix}:${key}`;
const inMemoryRateLimits = new Map();
const inMemoryDeniedTokens = new Map();

const isOptionalRedisMode = () => !env.redisRequired;

const cleanupExpiredMemoryEntries = () => {
  const now = Date.now();

  for (const [key, value] of inMemoryRateLimits.entries()) {
    if (!value?.expiresAt || value.expiresAt <= now) {
      inMemoryRateLimits.delete(key);
    }
  }

  for (const [key, value] of inMemoryDeniedTokens.entries()) {
    if (!value?.expiresAt || value.expiresAt <= now) {
      inMemoryDeniedTokens.delete(key);
    }
  }
};

const withRedisFallback = async ({ operationName, fallback, executor }) => {
  try {
    return await executor();
  } catch (error) {
    if (!isOptionalRedisMode()) {
      throw error;
    }

    logger.warn("Redis unavailable, using in-memory security fallback", {
      operationName,
      message: error.message,
    });

    return fallback();
  }
};

const consumeRateLimit = async ({ key, limit, windowMs }) => {
  const namespacedKey = withPrefix(`ratelimit:${key}`);
  return withRedisFallback({
    operationName: "consumeRateLimit",
    fallback: () => {
      cleanupExpiredMemoryEntries();

      const now = Date.now();
      const currentWindow = inMemoryRateLimits.get(namespacedKey);
      const activeWindow = currentWindow && currentWindow.expiresAt > now ? currentWindow : null;
      const nextCount = (activeWindow?.count || 0) + 1;
      const expiresAt = activeWindow?.expiresAt || now + windowMs;

      inMemoryRateLimits.set(namespacedKey, {
        count: nextCount,
        expiresAt,
      });

      return {
        allowed: nextCount <= limit,
        remaining: Math.max(limit - nextCount, 0),
        retryAfterMs: Math.max(expiresAt - now, 0),
      };
    },
    executor: async () => {
      const current = await redis.incr(namespacedKey);
      if (current === 1) {
        await redis.pexpire(namespacedKey, windowMs);
      }
      const ttl = await redis.pttl(namespacedKey);

      return {
        allowed: current <= limit,
        remaining: Math.max(limit - current, 0),
        retryAfterMs: ttl > 0 ? ttl : windowMs,
      };
    },
  });
};

const denylistToken = async ({ jti, expiresAt, tokenType }) => {
  if (!jti || !expiresAt) return;
  const ttlMs = Math.max(new Date(expiresAt).getTime() - Date.now(), 1000);
  const denylistKey = withPrefix(`denylist:${tokenType}:${jti}`);

  await withRedisFallback({
    operationName: "denylistToken",
    fallback: () => {
      inMemoryDeniedTokens.set(denylistKey, {
        expiresAt: Date.now() + ttlMs,
      });
      return true;
    },
    executor: async () => {
      await redis.set(denylistKey, "1", "PX", ttlMs);
      return true;
    },
  });
};

const isTokenDenied = async ({ jti, tokenType }) => {
  if (!jti) return false;
  const denylistKey = withPrefix(`denylist:${tokenType}:${jti}`);

  return withRedisFallback({
    operationName: "isTokenDenied",
    fallback: () => {
      cleanupExpiredMemoryEntries();
      return inMemoryDeniedTokens.has(denylistKey);
    },
    executor: async () => {
      const exists = await redis.exists(denylistKey);
      return exists === 1;
    },
  });
};

module.exports = {
  consumeRateLimit,
  denylistToken,
  isTokenDenied,
};
