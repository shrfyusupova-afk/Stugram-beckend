const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const settingsService = require("../services/settingsService");

const getSettings = catchAsync(async (req, res) => {
  const settings = await settingsService.getSettings(req.user.id);
  sendResponse(res, { message: "Settings fetched successfully", data: settings });
});

const updateSettings = catchAsync(async (req, res) => {
  const settings = await settingsService.updateSettings(req.user.id, req.body);
  sendResponse(res, { message: "Settings updated successfully", data: settings });
});

const getNotificationSettings = catchAsync(async (req, res) => {
  const settings = await settingsService.getNotificationSettings(req.user.id);
  sendResponse(res, { message: "Notification settings fetched successfully", data: settings });
});

const updateNotificationSettings = catchAsync(async (req, res) => {
  const settings = await settingsService.updateNotificationSettings(req.user.id, req.body);
  sendResponse(res, { message: "Notification settings updated successfully", data: settings });
});

const getHiddenWordsSettings = catchAsync(async (req, res) => {
  const settings = await settingsService.getHiddenWordsSettings(req.user.id);
  sendResponse(res, { message: "Hidden words settings fetched successfully", data: settings });
});

const updateHiddenWordsSettings = catchAsync(async (req, res) => {
  const settings = await settingsService.updateHiddenWordsSettings(req.user.id, req.body);
  sendResponse(res, { message: "Hidden words settings updated successfully", data: settings });
});

module.exports = {
  getSettings,
  updateSettings,
  getNotificationSettings,
  updateNotificationSettings,
  getHiddenWordsSettings,
  updateHiddenWordsSettings,
};
