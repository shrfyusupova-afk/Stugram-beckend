const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const adminService = require("../services/adminService");
const { sendPushToUser } = require("../services/pushNotificationService");

const getRequestMeta = (req) => ({
  ipAddress: req.ip,
  userAgent: req.headers["user-agent"] || null,
});

const listReports = catchAsync(async (req, res) => {
  const result = await adminService.listReports(req.query);
  sendResponse(res, {
    message: "Reports fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const getReport = catchAsync(async (req, res) => {
  const report = await adminService.getReportById(req.params.reportId);
  sendResponse(res, {
    message: "Report fetched successfully",
    data: report,
  });
});

const reviewReport = catchAsync(async (req, res) => {
  const report = await adminService.reviewReport(req.user, req.params.reportId, req.body, getRequestMeta(req));
  sendResponse(res, {
    message: "Report reviewed successfully",
    data: report,
  });
});

const suspendUser = catchAsync(async (req, res) => {
  const user = await adminService.suspendUser(req.user, req.params.userId, req.body, getRequestMeta(req));
  sendResponse(res, {
    message: "User suspended successfully",
    data: user,
  });
});

const banUser = catchAsync(async (req, res) => {
  const user = await adminService.banUser(req.user, req.params.userId, req.body, getRequestMeta(req));
  sendResponse(res, {
    message: "User banned successfully",
    data: user,
  });
});

const testPush = catchAsync(async (req, res) => {
  const result = await sendPushToUser(
    req.body.userId,
    {
      title: req.body.title,
      body: req.body.body,
      data: req.body.data || {},
      notificationType: "system",
    },
    {
      notificationType: "system",
      respectPreferences: false,
    }
  );

  sendResponse(res, {
    message: "Test push processed",
    data: result,
  });
});

module.exports = {
  listReports,
  getReport,
  reviewReport,
  suspendUser,
  banUser,
  testPush,
};
