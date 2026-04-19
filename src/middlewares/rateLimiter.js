const { env } = require("../config/env");
const { consumeRateLimit } = require("../services/redisSecurityService");

const resolveLimit = (limitConfig, req) => {
  if (typeof limitConfig === "function") {
    return Math.max(Number(limitConfig(req)) || 1, 1);
  }
  return Math.max(Number(limitConfig) || 1, 1);
};

const createDistributedRateLimiter = ({ keyPrefix, windowMs, limit, message }) => async (req, res, next) => {
  const actorKey = req.user?.id?.toString() || req.ip;
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
  // Authenticated mobile users legitimately hit many read endpoints during
  // home/profile/chat bootstrap, so allow a higher per-window budget.
  limit: (req) => (req.user?.id ? env.rateLimitMax * 4 : env.rateLimitMax),
  message: "Too many requests. Please try again later.",
});

const authLimiter = createDistributedRateLimiter({
  keyPrefix: "auth",
  windowMs: 60 * 1000,
  limit: 15,
  message: "Too many auth attempts. Please try again later.",
});

module.exports = { apiLimiter, authLimiter, createDistributedRateLimiter };
