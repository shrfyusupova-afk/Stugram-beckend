const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const requestService = require("../services/requestService");

const getRequestMeta = (req) => ({
  ipAddress: req.ip,
  userAgent: req.headers["user-agent"] || null,
});

const listRequests = catchAsync(async (req, res) => {
  const result = await requestService.listRequests(req.user.id, req.query);
  sendResponse(res, {
    message: "Requests fetched successfully.",
    data: { requests: result.requests },
    meta: result.meta,
  });
});

const addToChat = catchAsync(async (req, res) => {
  const result = await requestService.addToChat(req.user.id, req.params.requestId);
  sendResponse(res, {
    message: "Request added to chat successfully.",
    data: result,
  });
});

const blockRequestUser = catchAsync(async (req, res) => {
  const result = await requestService.blockFromRequest(req.user.id, req.params.requestId, getRequestMeta(req));
  sendResponse(res, {
    message: "Request user blocked successfully.",
    data: result,
  });
});

const deleteRequest = catchAsync(async (req, res) => {
  const result = await requestService.removeRequest(req.user.id, req.params.requestId);
  sendResponse(res, {
    message: "Request removed successfully.",
    data: result,
  });
});

module.exports = {
  listRequests,
  addToChat,
  blockRequestUser,
  deleteRequest,
};
