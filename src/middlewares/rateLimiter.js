const { env } = require("../config/env");
const { consumeRateLimit } = require("../services/redisSecurityService");
const crypto = require("crypto");
const logger = require("../utils/logger");
const { incrementCounter } = require("../services/chatMetricsService");

// Render/proxy deployments run this limiter before auth middleware, so the
// limiter keys authenticated mobile traffic by bearer-token fingerprint instead
// of grouping every real user behind the same proxy/IP bucket.
const getBearerToken = (req) => {
  const authHeader = req.headers.authorization || "";
  return authHeader.startsWith("Bearer ") ? authHeader.slice("Bearer ".length).trim() : "";
};

const getTokenFingerprint = (token) =>
  token ? crypto.createHash("sha256").update(token).digest("hex").slice(0, 32) : null;

const keyGenerator = (req) => {
  const tokenFingerprint = getTokenFingerprint(getBearerToken(req));
  if (req.user?.id) return `user:${req.user.id.toString()}`;
  if (tokenFingerprint) return `token:${tokenFingerprint}`;
  return `ip:${req.ip}`;
};

const isAuthenticatedRequest = (req) => Boolean(req.user?.id || getBearerToken(req));

const rateLimitBypassPaths = new Set([
  "/health",
  "/livez",
  "/readyz",
  "/health/push",
  "/health/chat-observability",
  "/metrics/chat",
  "/api/v1/health",
  "/api/v1/livez",
  "/api/v1/readyz",
]);

const shouldBypassRateLimit = (req) => {
  const path = req.path || "";
  const originalUrl = (req.originalUrl || "").split("?")[0];
  return rateLimitBypassPaths.has(path) || rateLimitBypassPaths.has(originalUrl);
};

const resolveLimit = (limitConfig, req) => {
  if (typeof limitConfig === "function") {
    return Math.max(Number(limitConfig(req)) || 1, 1);
  }
  return Math.max(Number(limitConfig) || 1, 1);
};

const createDistributedRateLimiter = ({ keyPrefix, windowMs, limit, message }) => async (req, res, next) => {
  if (shouldBypassRateLimit(req)) return next();

  const actorKey = keyGenerator(req);
  const effectiveLimit = resolveLimit(limit, req);
  const result = await consumeRateLimit({
    key: `${keyPrefix}:${actorKey}`,
    limit: effectiveLimit,
    windowMs,
  });

  res.setHeader("X-RateLimit-Limit", String(effectiveLimit));
  res.setHeader("X-RateLimit-Remaining", String(result.remaining));

  if (!result.allowed) {
    res.setHeader("Retry-After", String(Math.ceil(result.retryAfterMs / 1000)));
    const route = req.originalUrl || req.path || "unknown";
    logger.warn("rate_limit_hit", {
      requestId: req.requestId || null,
      route,
      authenticated: isAuthenticatedRequest(req),
      bucketKey: `${keyPrefix}:${actorKey}`,
      httpStatus: 429,
      retryAfterMs: result.retryAfterMs,
      remaining: result.remaining,
      limit: effectiveLimit,
    });
    incrementCounter("chat_rate_limit_hit_total", {
      route,
      authenticated: isAuthenticatedRequest(req) ? "true" : "false",
      limiter: keyPrefix,
    });
    incrementCounter("chat_429_total", {
      route,
      authenticated: isAuthenticatedRequest(req) ? "true" : "false",
      limiter: keyPrefix,
    });
    return res.status(429).json({
      success: false,
      message,
      data: null,
      meta: null,
    });
  }

  next();
};

const apiLimiter = createDistributedRateLimiter({
  keyPrefix: "api",
  windowMs: env.rateLimitWindowMs,
  limit: (req) => {
    if (env.chatRateLimitStrictMode) {
      return isAuthenticatedRequest(req) ? env.authenticatedRateLimitMax : env.rateLimitMax;
    }
    return isAuthenticatedRequest(req)
      ? Math.max(env.authenticatedRateLimitMax, 5000)
      : Math.max(env.rateLimitMax, 500);
  },
  message: "Too many requests. Please try again later.",
});

const authLimiter = createDistributedRateLimiter({
  keyPrefix: "auth",
  windowMs: 60 * 1000,
  limit: 15,
  message: "Too many auth attempts. Please try again later.",
});

module.exports = {
  apiLimiter,
  authLimiter,
  createDistributedRateLimiter,
  keyGenerator,
};
