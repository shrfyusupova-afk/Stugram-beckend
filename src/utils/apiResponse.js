const { getCorrelationMeta } = require("../middlewares/responseMeta");

const sendResponse = (res, { statusCode = 200, message = "OK", data = null, meta = null }) => {
  return res.status(statusCode).json({
    success: statusCode < 400,
    message,
    data,
    meta: getCorrelationMeta(res.req, res, meta),
    error: statusCode < 400 ? null : undefined,
  });
};

module.exports = { sendResponse };
