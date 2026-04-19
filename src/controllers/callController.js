const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const callService = require("../services/callService");

const getCallHistory = catchAsync(async (req, res) => {
  const result = await callService.getCallHistory(req.user.id, req.query);
  sendResponse(res, {
    message: "Call history fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const getCallDetail = catchAsync(async (req, res) => {
  const call = await callService.getCallById(req.user.id, req.params.callId);
  sendResponse(res, {
    message: "Call fetched successfully",
    data: call,
  });
});

module.exports = {
  getCallHistory,
  getCallDetail,
};
