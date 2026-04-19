const ApiError = require("../../../utils/ApiError");
const { getPagination } = require("../../../utils/pagination");
const User = require("../../../models/User");
const Session = require("../../../models/Session");
const { createAuditLog } = require("../../../services/auditLogService");

const buildUserFilter = (query) => {
  const filter = {};

  if (query.search) {
    filter.username = { $regex: query.search, $options: "i" };
  }

  if (query.status === "active") {
    filter.isSuspended = false;
  } else if (query.status === "banned") {
    filter.isSuspended = true;
  } else if (query.status === "admin") {
    filter.role = "admin";
  }

  return filter;
};

const listUsers = async (query) => {
  const { page, limit, skip } = getPagination(query);
  const filter = buildUserFilter(query);

  const [items, total] = await Promise.all([
    User.find(filter)
      .select("username fullName identity role isSuspended suspendedUntil suspensionReason lastLoginAt createdAt")
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit),
    User.countDocuments(filter),
  ]);

  return {
    items,
    meta: {
      page,
      limit,
      total,
      totalPages: Math.max(Math.ceil(total / limit), 1),
      appliedFilters: {
        search: query.search || null,
        status: query.status || null,
      },
    },
  };
};

const banUser = async (adminUser, userId, payload, meta = {}) => {
  const user = await User.findById(userId);
  if (!user) throw new ApiError(404, "User not found");

  user.isSuspended = true;
  user.suspendedUntil = null;
  user.suspensionReason = payload.reason || "Banned by admin";
  user.tokenInvalidBefore = new Date();
  await user.save();

  await Session.updateMany({ user: user.id, isRevoked: false }, { isRevoked: true, revokedReason: "admin_panel_ban" });

  await createAuditLog({
    actor: adminUser.id,
    action: "admin_panel.ban_user",
    category: "security",
    status: "warning",
    targetUser: user.id,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
    details: { reason: payload.reason || null },
  });

  return user;
};

const unbanUser = async (adminUser, userId, meta = {}) => {
  const user = await User.findById(userId);
  if (!user) throw new ApiError(404, "User not found");

  user.isSuspended = false;
  user.suspendedUntil = null;
  user.suspensionReason = null;
  await user.save();

  await createAuditLog({
    actor: adminUser.id,
    action: "admin_panel.unban_user",
    category: "security",
    status: "success",
    targetUser: user.id,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
  });

  return user;
};

const deleteUser = async (adminUser, userId, meta = {}) => {
  const user = await User.findById(userId);
  if (!user) throw new ApiError(404, "User not found");

  await User.deleteOne({ _id: userId });
  await Session.updateMany({ user: userId, isRevoked: false }, { isRevoked: true, revokedReason: "admin_panel_delete_user" });

  await createAuditLog({
    actor: adminUser.id,
    action: "admin_panel.delete_user",
    category: "security",
    status: "warning",
    targetUser: userId,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
    details: { username: user.username },
  });

  return { deleted: true, userId };
};

module.exports = {
  banUser,
  deleteUser,
  listUsers,
  unbanUser,
};
