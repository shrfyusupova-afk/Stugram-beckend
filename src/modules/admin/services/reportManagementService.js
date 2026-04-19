const ApiError = require("../../../utils/ApiError");
const { getPagination } = require("../../../utils/pagination");
const Report = require("../../../models/Report");
const Post = require("../../../models/Post");
const Comment = require("../../../models/Comment");
const User = require("../../../models/User");
const { createAuditLog } = require("../../../services/auditLogService");

const ensureReportTargetExists = async (targetType, targetId) => {
  const modelMap = {
    post: Post,
    comment: Comment,
    user: User,
  };

  const Model = modelMap[targetType];
  if (!Model) throw new ApiError(400, "Unsupported report target type");

  const target = await Model.findById(targetId).select("_id");
  if (!target) throw new ApiError(404, "Report target not found");
};

const createReport = async (reporterId, payload, meta = {}) => {
  await ensureReportTargetExists(payload.targetType, payload.targetId);

  const report = await Report.create({
    reporterId,
    targetType: payload.targetType,
    targetId: payload.targetId,
    reason: payload.reason,
    details: payload.details || "",
  });

  await createAuditLog({
    actor: reporterId,
    action: "report.create",
    category: "abuse",
    status: "success",
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
    details: {
      reportId: report.id,
      targetType: payload.targetType,
      targetId: payload.targetId,
      reason: payload.reason,
    },
  });

  return report;
};

const listReports = async (query) => {
  const { page, limit, skip } = getPagination(query);
  const filter = {};

  if (query.status) filter.status = query.status;
  if (query.targetType) filter.targetType = query.targetType;

  const [items, total] = await Promise.all([
    Report.find(filter)
      .populate("reporterId", "username fullName avatar")
      .populate("resolvedBy", "username fullName role")
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit),
    Report.countDocuments(filter),
  ]);

  return {
    items,
    meta: {
      page,
      limit,
      total,
      totalPages: Math.max(Math.ceil(total / limit), 1),
      appliedFilters: {
        status: query.status || null,
        targetType: query.targetType || null,
      },
    },
  };
};

const resolveReport = async (adminUser, reportId, payload, meta = {}) => {
  const report = await Report.findById(reportId);
  if (!report) throw new ApiError(404, "Report not found");

  report.status = "resolved";
  report.resolvedBy = adminUser.id;
  report.resolvedAt = new Date();
  report.resolutionNote = payload.resolutionNote || "";
  await report.save();

  await createAuditLog({
    actor: adminUser.id,
    action: "admin_panel.resolve_report",
    category: "abuse",
    status: "success",
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
    details: {
      reportId,
      targetType: report.targetType,
      targetId: report.targetId,
    },
  });

  return report;
};

module.exports = {
  createReport,
  listReports,
  resolveReport,
};
