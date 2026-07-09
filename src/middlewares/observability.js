const logger = require("../utils/logger");
const { env } = require("../config/env");

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
      errorCode: res.locals.errorCode || null,
      result: res.statusCode >= 400 ? "error" : "success",
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
  logRequestCompletion,
};
