const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const supportService = require("../services/supportService");

const getRequestMeta = (req) => ({
  ipAddress: req.ip,
  userAgent: req.headers["user-agent"] || null,
});

const createSupportTicket = catchAsync(async (req, res) => {
  const ticket = await supportService.createSupportTicket(req.user.id, req.body, req.file || null);
  sendResponse(res, {
    statusCode: 201,
    message: "Support ticket created successfully",
    data: ticket,
  });
});

const getMySupportTickets = catchAsync(async (req, res) => {
  const result = await supportService.getSupportTickets(req.user.id, req.query);
  sendResponse(res, {
    message: "Support tickets fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const getSupportTicketDetail = catchAsync(async (req, res) => {
  const ticket = await supportService.getSupportTicketById(req.user.id, req.params.ticketId);
  sendResponse(res, {
    message: "Support ticket fetched successfully",
    data: ticket,
  });
});

const getAdminSupportTickets = catchAsync(async (req, res) => {
  const result = await supportService.getAdminSupportTickets(req.query);
  sendResponse(res, {
    message: "Support tickets fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const getAdminSupportTicketDetail = catchAsync(async (req, res) => {
  const ticket = await supportService.getAdminSupportTicketById(req.params.ticketId);
  sendResponse(res, {
    message: "Support ticket fetched successfully",
    data: ticket,
  });
});

const updateAdminSupportTicketStatus = catchAsync(async (req, res) => {
  const ticket = await supportService.updateAdminSupportTicketStatus(
    req.user,
    req.params.ticketId,
    req.body,
    getRequestMeta(req)
  );

  sendResponse(res, {
    message: "Support ticket status updated successfully",
    data: ticket,
  });
});

const assignAdminSupportTicket = catchAsync(async (req, res) => {
  const ticket = await supportService.assignAdminSupportTicket(
    req.user,
    req.params.ticketId,
    req.body,
    getRequestMeta(req)
  );

  sendResponse(res, {
    message: "Support ticket assignment updated successfully",
    data: ticket,
  });
});

const addAdminSupportTicketNote = catchAsync(async (req, res) => {
  const ticket = await supportService.addAdminSupportTicketNote(
    req.user,
    req.params.ticketId,
    req.body,
    getRequestMeta(req)
  );

  sendResponse(res, {
    message: "Support ticket note added successfully",
    data: ticket,
  });
});

module.exports = {
  createSupportTicket,
  getMySupportTickets,
  getSupportTicketDetail,
  getAdminSupportTickets,
  getAdminSupportTicketDetail,
  updateAdminSupportTicketStatus,
  assignAdminSupportTicket,
  addAdminSupportTicketNote,
};
