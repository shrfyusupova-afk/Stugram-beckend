const { consumeRateLimit } = require("../services/redisSecurityService");

const takeSocketToken = async ({ userId, eventName, limit, windowMs }) => {
  const result = await consumeRateLimit({
    key: `socket:${eventName}:${userId}`,
    limit,
    windowMs,
  });

  return result.allowed;
};

module.exports = { takeSocketToken };
