const { env } = require("../config/env");
const { requireFeatureEnabled } = require("./featureGate");
const {
  isChatReplayEnabled,
  isGroupSendEnabled,
  isMediaSendEnabled,
} = require("../config/featureFlags");
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

const requireReplaySyncEnabled = requireFeatureEnabled({
  isEnabled: isChatReplayEnabled,
  feature: "chat_replay",
  errorCode: "FEATURE_CHAT_REPLAY_DISABLED",
});

const requireGroupSendEnabled = requireFeatureEnabled({
  isEnabled: isGroupSendEnabled,
  feature: "group_send",
  errorCode: "FEATURE_GROUP_SEND_DISABLED",
});

const requireMediaSendEnabled = requireFeatureEnabled({
  isEnabled: isMediaSendEnabled,
  feature: "media_send",
  errorCode: "FEATURE_MEDIA_SEND_DISABLED",
  isAttempt: (req) => {
    const messageType = String(req.body?.messageType || "").toLowerCase();
    return Boolean(req.file) || ["image", "video", "voice", "round_video", "file"].includes(messageType);
  },
});

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
