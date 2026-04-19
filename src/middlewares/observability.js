const crypto = require("crypto");

const logger = require("../utils/logger");
const { env } = require("../config/env");

const assignRequestContext = (req, res, next) => {
  const incomingRequestId = typeof req.headers["x-request-id"] === "string" ? req.headers["x-request-id"].trim() : "";
  const requestId = incomingRequestId && incomingRequestId.length <= 128 ? incomingRequestId : crypto.randomUUID();
  req.requestId = requestId;
  req.requestStartedAt = process.hrtime.bigint();
  res.setHeader("X-Request-Id", requestId);
  next();
};

const logRequestCompletion = (req, res, next) => {
  res.on("finish", () => {
    if (!req.requestStartedAt) return;

    const durationMs = Number(process.hrtime.bigint() - req.requestStartedAt) / 1_000_000;
    const payload = {
      requestId: req.requestId || null,
      method: req.method,
      path: req.originalUrl.split("?")[0],
      statusCode: res.statusCode,
      durationMs: Number(durationMs.toFixed(2)),
      userId: req.user?.id || null,
    };

    if (durationMs >= env.requestSlowMs) {
      logger.warn("Slow request detected", payload);
      return;
    }

    logger.info("Request completed", payload);
  });

  next();
};

module.exports = {
  assignRequestContext,
  logRequestCompletion,
};
