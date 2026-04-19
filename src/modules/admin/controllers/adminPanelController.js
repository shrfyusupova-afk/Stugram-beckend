const catchAsync = require("../../../utils/catchAsync");
const { sendResponse } = require("../../../utils/apiResponse");
const dashboardService = require("../services/dashboardService");
const userManagementService = require("../services/userManagementService");
const contentModerationService = require("../services/contentModerationService");
const reportManagementService = require("../services/reportManagementService");
const systemMonitoringService = require("../services/systemMonitoringService");
const auditLogService = require("../services/auditLogService");

const getRequestMeta = (req) => ({
  ipAddress: req.ip,
  userAgent: req.headers["user-agent"] || null,
});

const getDashboard = catchAsync(async (_req, res) => {
  const result = await dashboardService.getDashboardStatistics();
  sendResponse(res, {
    message: "Admin dashboard fetched successfully",
    data: result,
  });
});

const listUsers = catchAsync(async (req, res) => {
  const result = await userManagementService.listUsers(req.query);
  sendResponse(res, {
    message: "Users fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const banUser = catchAsync(async (req, res) => {
  const user = await userManagementService.banUser(req.user, req.params.id, req.body, getRequestMeta(req));
  sendResponse(res, {
    message: "User banned successfully",
    data: user,
  });
});

const unbanUser = catchAsync(async (req, res) => {
  const user = await userManagementService.unbanUser(req.user, req.params.id, getRequestMeta(req));
  sendResponse(res, {
    message: "User unbanned successfully",
    data: user,
  });
});

const deleteUser = catchAsync(async (req, res) => {
  const result = await userManagementService.deleteUser(req.user, req.params.id, getRequestMeta(req));
  sendResponse(res, {
    message: "User deleted successfully",
    data: result,
  });
});

const listPosts = catchAsync(async (req, res) => {
  const result = await contentModerationService.listPostsForModeration(req.query);
  sendResponse(res, {
    message: "Posts fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const hidePost = catchAsync(async (req, res) => {
  const post = await contentModerationService.hidePost(req.user, req.params.id, req.body, getRequestMeta(req));
  sendResponse(res, {
    message: "Post hidden successfully",
    data: post,
  });
});

const deletePost = catchAsync(async (req, res) => {
  const result = await contentModerationService.deletePost(req.user, req.params.id, getRequestMeta(req));
  sendResponse(res, {
    message: "Post deleted successfully",
    data: result,
  });
});

const listReports = catchAsync(async (req, res) => {
  const result = await reportManagementService.listReports(req.query);
  sendResponse(res, {
    message: "Admin reports fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const resolveReport = catchAsync(async (req, res) => {
  const result = await reportManagementService.resolveReport(req.user, req.params.id, req.body, getRequestMeta(req));
  sendResponse(res, {
    message: "Report resolved successfully",
    data: result,
  });
});

const getSystemHealth = catchAsync(async (_req, res) => {
  const result = await systemMonitoringService.getSystemHealth();
  sendResponse(res, {
    message: "System health fetched successfully",
    data: result,
  });
});

const listAuditLogs = catchAsync(async (req, res) => {
  const result = await auditLogService.listAuditLogs(req.query);
  sendResponse(res, {
    message: "Audit logs fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

module.exports = {
  banUser,
  deletePost,
  deleteUser,
  getDashboard,
  getSystemHealth,
  hidePost,
  listPosts,
  listAuditLogs,
  listReports,
  listUsers,
  resolveReport,
  unbanUser,
};
