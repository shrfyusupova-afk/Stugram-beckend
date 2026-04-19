const AuditLog = require("../../../models/AuditLog");
const { getPagination } = require("../../../utils/pagination");

const listAuditLogs = async (query = {}) => {
  const { page, limit, skip } = getPagination(query);
  const filter = {};

  if (query.category) filter.category = query.category;
  if (query.status) filter.status = query.status;
  if (query.search) {
    filter.action = { $regex: query.search, $options: "i" };
  }

  const [items, total] = await Promise.all([
    AuditLog.find(filter)
      .populate("actor", "username fullName role")
      .populate("targetUser", "username fullName role")
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .lean(),
    AuditLog.countDocuments(filter),
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
        category: query.category || null,
        status: query.status || null,
      },
    },
  };
};

module.exports = {
  listAuditLogs,
};

