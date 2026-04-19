const { consumeRateLimit } = require("../services/redisSecurityService");

const createChatLimiter = ({ keyPrefix, windowMs, max, message }) => async (req, res, next) => {
  const actorKey = req.user?.id?.toString() || req.ip;
  const result = await consumeRateLimit({
    key: `${keyPrefix}:${actorKey}`,
    limit: max,
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

const messageSendLimiter = createChatLimiter({
  keyPrefix: "chat:message_send",
  windowMs: 15 * 1000,
  max: 8,
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
  max: 12,
  message: "Too many replies. Please slow down.",
});

module.exports = {
  messageSendLimiter,
  reactionLimiter,
  replyLimiter,
};
