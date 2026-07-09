const getCorrelationMeta = (req, res, meta) => {
  const currentMeta = meta && typeof meta === "object" && !Array.isArray(meta) ? meta : {};
  return {
    ...currentMeta,
    requestId: currentMeta.requestId || req.requestId || res.locals.requestId || null,
    timestamp: currentMeta.timestamp || new Date().toISOString(),
  };
};

const attachResponseMeta = (req, res, next) => {
  const originalJson = res.json.bind(res);

  res.json = (body) => {
    if (!body || typeof body !== "object" || Array.isArray(body)) {
      return originalJson(body);
    }

    const patched = { ...body };
    patched.meta = getCorrelationMeta(req, res, body.meta);

    if (patched.success === true && !Object.prototype.hasOwnProperty.call(patched, "error")) {
      patched.error = null;
    }

    if (patched.success === false && !Object.prototype.hasOwnProperty.call(patched, "error")) {
      patched.error = {
        code: res.locals.errorCode || "INTERNAL_SERVER_ERROR",
        details: {},
      };
    }

    return originalJson(patched);
  };

  next();
};

module.exports = {
  getCorrelationMeta,
  attachResponseMeta,
};
