const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const closeFriendService = require("../services/closeFriendService");

const addCloseFriend = catchAsync(async (req, res) => {
  const result = await closeFriendService.addCloseFriend(req.user.id, req.params.userId);
  sendResponse(res, {
    statusCode: 201,
    message: "User added to close friends successfully",
    data: result,
  });
});

const removeCloseFriend = catchAsync(async (req, res) => {
  const result = await closeFriendService.removeCloseFriend(req.user.id, req.params.userId);
  sendResponse(res, {
    message: "User removed from close friends successfully",
    data: result,
  });
});

const getCloseFriends = catchAsync(async (req, res) => {
  const result = await closeFriendService.listCloseFriends(req.user.id, req.query);
  sendResponse(res, {
    message: "Close friends fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

module.exports = {
  addCloseFriend,
  removeCloseFriend,
  getCloseFriends,
};
