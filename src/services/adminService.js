const ApiError = require("../utils/ApiError");
const { getPagination } = require("../utils/pagination");
const UserReport = require("../models/UserReport");
const User = require("../models/User");
const Session = require("../models/Session");
const { createAuditLog } = require("./auditLogService");

const listReports = async (query) => {
  const { page, limit, skip } = getPagination(query);
  const filter = {};
  if (query.status) filter.status = query.status;

  const [items, total] = await Promise.all([
    UserReport.find(filter)
      .populate("reporter", "username fullName avatar")
      .populate("reportedUser", "username fullName avatar role isSuspended suspendedUntil")
      .populate("conversation", "_id")
      .populate("message", "_id text messageType")
      .populate("reviewedBy", "username fullName")
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit),
    UserReport.countDocuments(filter),
  ]);

  return {
    items,
    meta: { page, limit, total, totalPages: Math.ceil(total / limit) },
  };
};

const getReportById = async (reportId) => {
  const report = await UserReport.findById(reportId)
    .populate("reporter", "username fullName avatar")
    .populate("reportedUser", "username fullName avatar role isSuspended suspendedUntil suspensionReason")
    .populate("conversation", "_id participants")
    .populate("message", "_id text messageType media");

  if (!report) throw new ApiError(404, "Report not found");
  return report;
};

const reviewReport = async (adminUser, reportId, payload, meta = {}) => {
  const report = await UserReport.findById(reportId);
  if (!report) throw new ApiError(404, "Report not found");

  report.status = payload.status;
  report.reviewNotes = payload.reviewNotes || "";
  report.reviewedBy = adminUser.id;
  report.reviewedAt = new Date();
  report.actionTaken = payload.actionTaken || report.actionTaken;
  await report.save();

  await createAuditLog({
    actor: adminUser.id,
    action: "admin.review_report",
    category: "abuse",
    status: "success",
    targetUser: report.reportedUser,
    conversation: report.conversation || null,
    message: report.message || null,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
    details: { status: payload.status, actionTaken: report.actionTaken },
  });

  return report;
};

const suspendUser = async (adminUser, targetUserId, payload, meta = {}) => {
  const user = await User.findById(targetUserId);
  if (!user) throw new ApiError(404, "User not found");

  user.isSuspended = true;
  user.suspendedUntil = payload.suspendedUntil ? new Date(payload.suspendedUntil) : null;
  user.suspensionReason = payload.reason;
  user.tokenInvalidBefore = new Date();
  await user.save();

  await Session.updateMany(
    { user: user.id, isRevoked: false },
    { isRevoked: true, revokedReason: "admin_suspend" }
  );

  await createAuditLog({
    actor: adminUser.id,
    action: "admin.suspend_user",
    category: "security",
    status: "warning",
    targetUser: user.id,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
    details: { reason: payload.reason, suspendedUntil: user.suspendedUntil },
  });

  return user;
};

const banUser = async (adminUser, targetUserId, payload, meta = {}) => {
  const user = await User.findById(targetUserId);
  if (!user) throw new ApiError(404, "User not found");

  user.isSuspended = true;
  user.suspendedUntil = null;
  user.suspensionReason = payload.reason;
  user.tokenInvalidBefore = new Date();
  await user.save();

  await Session.updateMany(
    { user: user.id, isRevoked: false },
    { isRevoked: true, revokedReason: "admin_ban", isCompromised: true }
  );

  await createAuditLog({
    actor: adminUser.id,
    action: "admin.ban_user",
    category: "security",
    status: "warning",
    targetUser: user.id,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
    details: { reason: payload.reason },
  });

  return user;
};

module.exports = {
  listReports,
  getReportById,
  reviewReport,
  suspendUser,
  banUser,
};
