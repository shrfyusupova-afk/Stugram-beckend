const { ZodError } = require("zod");
const multer = require("multer");

const ApiError = require("../utils/ApiError");
const { env } = require("../config/env");
const logger = require("../utils/logger");

const notFoundHandler = (req, _res, next) => {
  next(new ApiError(404, `Route not found: ${req.method} ${req.originalUrl}`));
};

const errorHandler = (error, req, res, _next) => {
  const resolvedErrorCode =
    error?.code ||
    error?.details?.code ||
    (error instanceof ZodError
      ? "VALIDATION_ERROR"
      : error instanceof multer.MulterError
      ? "UPLOAD_PAYLOAD_INVALID"
      : error instanceof ApiError
      ? "API_ERROR"
      : "INTERNAL_SERVER_ERROR");
  res.locals.errorCode = resolvedErrorCode;

  logger.error("Request failed", {
    requestId: req.requestId || null,
    userId: req.user?.id || null,
    method: req.method,
    route: req.originalUrl.split("?")[0],
    statusCode: error.statusCode || 500,
    service: error.details?.service || error.service || null,
    errorName: error.name,
    message: error.message,
    errorCode: resolvedErrorCode,
    details: error.details || null,
    stack: error.stack || null,
  });

  if (error instanceof ZodError) {
    return res.status(400).json({
      success: false,
      message: "Validation failed",
      data: null,
      meta: {
        issues: error.issues,
      },
      error: {
        code: "VALIDATION_ERROR",
        details: {},
      },
    });
  }

  if (error instanceof multer.MulterError) {
    const message =
      error.code === "LIMIT_FILE_SIZE"
        ? `Uploaded file exceeds allowed size (${Math.round(env.mediaMaxFileSizeBytes / 1024 / 1024)} MB max)`
        : "Malformed upload payload";
    return res.status(400).json({
      success: false,
      message,
      data: null,
      meta: null,
      error: {
        code: "UPLOAD_PAYLOAD_INVALID",
        details: {
          multerCode: error.code || null,
        },
      },
    });
  }

  if (error instanceof ApiError) {
    return res.status(error.statusCode).json({
      success: false,
      message: error.message,
      data: null,
      meta: error.details ? { details: error.details } : null,
      error: {
        code: error.details?.code || error.code || "API_ERROR",
        details: error.details?.details || {},
      },
    });
  }

  return res.status(500).json({
    success: false,
    message: "Internal server error",
    data: null,
    meta: envSafeStack(),
    error: {
      code: "INTERNAL_SERVER_ERROR",
      details: {},
    },
  });
};

const envSafeStack = () =>
  process.env.NODE_ENV === "development"
    ? { stack: "See server console for full stack trace" }
    : null;

module.exports = { notFoundHandler, errorHandler };
