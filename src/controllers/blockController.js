const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const blockService = require("../services/blockService");

const getRequestMeta = (req) => ({
  ipAddress: req.ip,
  userAgent: req.headers["user-agent"] || null,
});

const getBlockedAccounts = catchAsync(async (req, res) => {
  const result = await blockService.getBlockedAccounts(req.user.id, req.query);
  sendResponse(res, {
    message: "Blocked accounts fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const unblockUser = catchAsync(async (req, res) => {
  const result = await blockService.unblockUser(req.user.id, req.params.userId, getRequestMeta(req));
  sendResponse(res, {
    message: "User unblocked successfully",
    data: result,
  });
});

const blockUser = catchAsync(async (req, res) => {
  const result = await blockService.blockUser(req.user.id, req.params.userId, getRequestMeta(req));
  sendResponse(res, {
    message: "User blocked successfully",
    data: result,
  });
});

module.exports = {
  getBlockedAccounts,
  blockUser,
  unblockUser,
};
