const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const notificationService = require("../services/notificationService");

const getNotifications = catchAsync(async (req, res) => {
  const result = await notificationService.getNotifications(req.user.id, req.query);
  sendResponse(res, { message: "Notifications fetched successfully", data: result.items, meta: result.meta });
});

const markNotificationAsRead = catchAsync(async (req, res) => {
  const notification = await notificationService.markNotificationAsRead(req.user.id, req.params.notificationId);
  sendResponse(res, { message: "Notification marked as read", data: notification });
});

const markAllAsRead = catchAsync(async (req, res) => {
  await notificationService.markAllNotificationsAsRead(req.user.id);
  sendResponse(res, { message: "All notifications marked as read", data: { updated: true } });
});

const getUnreadCount = catchAsync(async (req, res) => {
  const unreadCount = await notificationService.getUnreadNotificationCount(req.user.id);
  sendResponse(res, {
    message: "Unread notification count fetched successfully",
    data: { unreadCount },
  });
});

const getSummary = catchAsync(async (req, res) => {
  const summary = await notificationService.getNotificationSummary(req.user.id);
  sendResponse(res, {
    message: "Notification summary fetched successfully",
    data: summary,
  });
});

module.exports = { getNotifications, markNotificationAsRead, markAllAsRead, getUnreadCount, getSummary };
