const SupportTicket = require("../models/SupportTicket");
const User = require("../models/User");
const ApiError = require("../utils/ApiError");
const { uploadBufferToCloudinary } = require("../utils/media");
const { getPagination } = require("../utils/pagination");
const { createAuditLog } = require("./auditLogService");

const escapeRegex = (value = "") => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

const mapSupportTicket = (ticket) => ({
  _id: ticket._id,
  category: ticket.category,
  subject: ticket.subject,
  description: ticket.description,
  screenshot: ticket.screenshot?.url
    ? {
        url: ticket.screenshot.url,
      }
    : null,
  status: ticket.status,
  appVersion: ticket.appVersion,
  deviceInfo: ticket.deviceInfo,
  createdAt: ticket.createdAt,
  updatedAt: ticket.updatedAt,
});

const mapAdminSupportTicket = (ticket, { includeDescription = true, includeInternalNotes = false } = {}) => ({
  _id: ticket._id,
  user: ticket.user
    ? {
        _id: ticket.user._id || ticket.user,
        username: ticket.user.username || null,
        fullName: ticket.user.fullName || null,
        identity: ticket.user.identity || null,
        avatar: ticket.user.avatar || null,
      }
    : null,
  category: ticket.category,
  subject: ticket.subject,
  description: includeDescription ? ticket.description : undefined,
  screenshot: ticket.screenshot?.url
    ? {
        url: ticket.screenshot.url,
      }
    : null,
  status: ticket.status,
  assignedTo: ticket.assignedTo
    ? {
        _id: ticket.assignedTo._id || ticket.assignedTo,
        username: ticket.assignedTo.username || null,
        fullName: ticket.assignedTo.fullName || null,
        role: ticket.assignedTo.role || null,
      }
    : null,
  assignedAt: ticket.assignedAt || null,
  internalNotes: includeInternalNotes
    ? (ticket.internalNotes || []).map((entry) => ({
        _id: entry._id,
        author: entry.author
          ? {
              _id: entry.author._id || entry.author,
              username: entry.author.username || null,
              fullName: entry.author.fullName || null,
              role: entry.author.role || null,
            }
          : null,
        note: entry.note,
        createdAt: entry.createdAt,
      }))
    : undefined,
  internalNotesCount: Array.isArray(ticket.internalNotes) ? ticket.internalNotes.length : 0,
  appVersion: ticket.appVersion,
  deviceInfo: ticket.deviceInfo,
  createdAt: ticket.createdAt,
  updatedAt: ticket.updatedAt,
});

const createSupportTicket = async (userId, payload, file = null) => {
  let screenshot = null;

  if (file) {
    const uploaded = await uploadBufferToCloudinary(file.buffer, "stugram/support", "image");
    screenshot = {
      url: uploaded.url,
      publicId: uploaded.publicId,
    };
  }

  const ticket = await SupportTicket.create({
    user: userId,
    category: payload.category,
    subject: payload.subject,
    description: payload.description,
    screenshot,
    appVersion: payload.appVersion || null,
    deviceInfo: payload.deviceInfo || null,
  });

  return mapSupportTicket(ticket);
};

const getSupportTickets = async (userId, query = {}) => {
  const { page, limit, skip } = getPagination(query);
  const filter = { user: userId };

  const [items, total] = await Promise.all([
    SupportTicket.find(filter).sort({ createdAt: -1 }).skip(skip).limit(limit).lean(),
    SupportTicket.countDocuments(filter),
  ]);

  return {
    items: items.map(mapSupportTicket),
    meta: {
      page,
      limit,
      total,
      totalPages: Math.ceil(total / limit) || 1,
    },
  };
};

const getSupportTicketById = async (userId, ticketId) => {
  const ticket = await SupportTicket.findOne({ _id: ticketId, user: userId }).lean();
  if (!ticket) {
    throw new ApiError(404, "Support ticket not found");
  }

  return mapSupportTicket(ticket);
};

const buildAdminSupportTicketFilter = async (query = {}) => {
  const filter = {};

  if (query.status) {
    filter.status = query.status;
  }

  if (query.category) {
    filter.category = query.category;
  }

  if (query.assignedTo) {
    filter.assignedTo = query.assignedTo;
  }

  if (query.fromDate || query.toDate) {
    filter.createdAt = {};
    if (query.fromDate) {
      filter.createdAt.$gte = new Date(query.fromDate);
    }
    if (query.toDate) {
      filter.createdAt.$lte = new Date(query.toDate);
    }
  }

  if (query.search) {
    const safeSearch = escapeRegex(query.search.trim());
    const regex = new RegExp(safeSearch, "i");
    const matchingUsers = await User.find({
      $or: [{ username: regex }, { fullName: regex }, { identity: regex }],
    })
      .select("_id")
      .limit(20)
      .lean();

    const userIds = matchingUsers.map((user) => user._id);
    filter.$or = [{ subject: regex }];
    if (userIds.length) {
      filter.$or.push({ user: { $in: userIds } });
    }
  }

  return filter;
};

const getAdminSupportTickets = async (query = {}) => {
  const { page, limit, skip } = getPagination(query);
  const filter = await buildAdminSupportTicketFilter(query);

  const [items, total] = await Promise.all([
    SupportTicket.find(filter)
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .populate("user", "username fullName identity avatar")
      .populate("assignedTo", "username fullName role")
      .lean(),
    SupportTicket.countDocuments(filter),
  ]);

  return {
    items: items.map((ticket) => mapAdminSupportTicket(ticket, { includeDescription: false })),
    meta: {
      page,
      limit,
      total,
      totalPages: Math.ceil(total / limit) || 1,
      filters: {
        status: query.status || null,
        category: query.category || null,
        fromDate: query.fromDate || null,
        toDate: query.toDate || null,
        search: query.search || null,
        assignedTo: query.assignedTo || null,
      },
    },
  };
};

const getAdminSupportTicketById = async (ticketId) => {
  const ticket = await SupportTicket.findById(ticketId)
    .populate("user", "username fullName identity avatar")
    .populate("assignedTo", "username fullName role")
    .populate("internalNotes.author", "username fullName role")
    .lean();

  if (!ticket) {
    throw new ApiError(404, "Support ticket not found");
  }

  return mapAdminSupportTicket(ticket, {
    includeDescription: true,
    includeInternalNotes: true,
  });
};

const updateAdminSupportTicketStatus = async (actor, ticketId, payload, meta = {}) => {
  const ticket = await SupportTicket.findById(ticketId);
  if (!ticket) {
    throw new ApiError(404, "Support ticket not found");
  }

  ticket.status = payload.status;
  await ticket.save();

  await createAuditLog({
    actor: actor.id,
    action: "support.ticket.status_updated",
    category: "support",
    status: "success",
    targetUser: ticket.user,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
    details: {
      ticketId: ticket.id,
      newStatus: payload.status,
    },
  });

  return getAdminSupportTicketById(ticketId);
};

const assignAdminSupportTicket = async (actor, ticketId, payload, meta = {}) => {
  const ticket = await SupportTicket.findById(ticketId);
  if (!ticket) {
    throw new ApiError(404, "Support ticket not found");
  }

  let assignee = null;
  if (payload.assignedTo) {
    assignee = await User.findOne({
      _id: payload.assignedTo,
      role: { $in: ["admin", "moderator"] },
    }).select("_id username fullName role");

    if (!assignee) {
      throw new ApiError(404, "Assignable admin or moderator not found");
    }
  }

  ticket.assignedTo = assignee?._id || null;
  ticket.assignedAt = assignee ? new Date() : null;
  await ticket.save();

  await createAuditLog({
    actor: actor.id,
    action: "support.ticket.assigned",
    category: "support",
    status: "success",
    targetUser: ticket.user,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
    details: {
      ticketId: ticket.id,
      assignedTo: assignee?._id || null,
    },
  });

  return getAdminSupportTicketById(ticketId);
};

const addAdminSupportTicketNote = async (actor, ticketId, payload, meta = {}) => {
  const ticket = await SupportTicket.findById(ticketId);
  if (!ticket) {
    throw new ApiError(404, "Support ticket not found");
  }

  ticket.internalNotes.push({
    author: actor.id,
    note: payload.note,
  });

  await ticket.save();

  await createAuditLog({
    actor: actor.id,
    action: "support.ticket.note_added",
    category: "support",
    status: "success",
    targetUser: ticket.user,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
    details: {
      ticketId: ticket.id,
      noteLength: payload.note.length,
    },
  });

  return getAdminSupportTicketById(ticketId);
};

module.exports = {
  createSupportTicket,
  getSupportTickets,
  getSupportTicketById,
  getAdminSupportTickets,
  getAdminSupportTicketById,
  updateAdminSupportTicketStatus,
  assignAdminSupportTicket,
  addAdminSupportTicketNote,
};
