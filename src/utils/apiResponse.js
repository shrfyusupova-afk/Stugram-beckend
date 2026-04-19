const sendResponse = (res, { statusCode = 200, message = "OK", data = null, meta = null }) => {
  return res.status(statusCode).json({
    success: statusCode < 400,
    message,
    data,
    meta,
  });
};

module.exports = { sendResponse };
