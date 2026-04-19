const catchAsync = require("../../../utils/catchAsync");
const { sendResponse } = require("../../../utils/apiResponse");
const reportService = require("../services/reportService");

const getRequestMeta = (req) => ({
  ipAddress: req.ip,
  userAgent: req.headers["user-agent"] || null,
});

const createReport = catchAsync(async (req, res) => {
  const report = await reportService.createReport(req.user.id, req.body, getRequestMeta(req));
  sendResponse(res, {
    statusCode: 201,
    message: "Report created successfully",
    data: report,
  });
});

module.exports = {
  createReport,
};
