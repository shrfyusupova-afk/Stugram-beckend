const buildFeatureDisabledPayload = (req, feature, errorCode) => ({
  success: false,
  message: "This feature is temporarily disabled.",
  data: null,
  meta: {
    requestId: req.requestId || req.res?.locals?.requestId || null,
    timestamp: new Date().toISOString(),
  },
  error: {
    code: errorCode,
    details: {
      feature,
    },
  },
});

const requireFeatureEnabled = ({ isEnabled, feature, errorCode, statusCode = 503, isAttempt = null }) => (req, res, next) => {
  if (typeof isAttempt === "function" && !isAttempt(req)) {
    return next();
  }

  if (isEnabled()) {
    return next();
  }

  res.locals.errorCode = errorCode;
  return res.status(statusCode).json(buildFeatureDisabledPayload(req, feature, errorCode));
};

module.exports = {
  buildFeatureDisabledPayload,
  requireFeatureEnabled,
};
