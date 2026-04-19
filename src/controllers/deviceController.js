const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const deviceService = require("../services/deviceService");

const registerPushToken = catchAsync(async (req, res) => {
  const token = await deviceService.registerPushToken(req.user.id, req.body);
    sendResponse(res, {
      statusCode: 201,
      message: "Push token registered successfully",
      data: {
        updated: true,
        _id: token._id,
        userId: token.user,
        platform: token.platform,
        deviceId: token.deviceId,
      pushToken: token.token,
      appVersion: token.appVersion,
      isActive: token.isActive,
      lastSeenAt: token.lastSeenAt,
      lastActiveAt: token.lastSeenAt,
      updatedAt: token.updatedAt,
    },
  });
});

const deletePushToken = catchAsync(async (req, res) => {
  const result = await deviceService.removePushToken(req.user.id, req.body);
  sendResponse(res, {
    message: "Push token removed successfully",
    data: {
      updated: true,
      ...result,
    },
  });
});

module.exports = {
  registerPushToken,
  deletePushToken,
};
