const ApiError = require("../utils/ApiError");
const { env } = require("../config/env");
const { consumeRateLimit } = require("../services/redisSecurityService");

const createChatLimiter = ({ keyPrefix, windowMs, max, message }) => async (req, res, next) => {
  const actorKey = req.user?.id?.toString() || req.ip;
  const resolvedMax = env.chatRateLimitStrictMode ? Math.max(1, Math.floor(max / 2)) : max;
  const result = await consumeRateLimit({
    key: `${keyPrefix}:${actorKey}`,
    limit: resolvedMax,
    windowMs,
  });

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

const ensureFeatureEnabled = (enabled, message, statusCode = 503) => (req, _res, next) => {
  if (!enabled()) {
    return next(new ApiError(statusCode, message));
  }
  return next();
};

const requireReplaySyncEnabled = ensureFeatureEnabled(
  () => env.chatReplaySyncEnabled,
  "Chat replay sync is temporarily unavailable."
);

const requireGroupSendEnabled = ensureFeatureEnabled(
  () => env.chatGroupSendEnabled,
  "Group chat sending is temporarily unavailable."
);

const requireMediaSendEnabled = (req, _res, next) => {
  if (env.chatMediaSendEnabled) {
    return next();
  }

  const messageType = String(req.body?.messageType || "").toLowerCase();
  const isMediaAttempt = Boolean(req.file) || ["image", "video", "voice", "round_video", "file"].includes(messageType);
  if (!isMediaAttempt) {
    return next();
  }

  return next(new ApiError(503, "Media sending is temporarily unavailable."));
};

const messageSendLimiter = createChatLimiter({
  keyPrefix: "chat:message_send",
  windowMs: 15 * 1000,
  max: 60,
  message: "Too many messages sent. Please try again shortly.",
});

const reactionLimiter = createChatLimiter({
  keyPrefix: "chat:reaction",
  windowMs: 10 * 1000,
  max: 20,
  message: "Too many reactions. Please slow down.",
});

const replyLimiter = createChatLimiter({
  keyPrefix: "chat:reply",
  windowMs: 15 * 1000,
  max: 60,
  message: "Too many replies. Please slow down.",
});

module.exports = {
  messageSendLimiter,
  reactionLimiter,
  replyLimiter,
  requireReplaySyncEnabled,
  requireGroupSendEnabled,
  requireMediaSendEnabled,
};
