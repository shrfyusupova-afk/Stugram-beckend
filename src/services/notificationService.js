const Notification = require("../models/Notification");
const { getPagination } = require("../utils/pagination");
const { buildNotificationPushPayload } = require("./pushNotificationService");
const logger = require("../utils/logger");

const createNotification = async ({ recipient, actor = null, type, post = null, comment = null, followRequest = null, message = "" }) => {
  if (actor && recipient.toString() === actor.toString()) {
    return null;
  }

  const notification = await Notification.create({ recipient, actor, type, post, comment, followRequest, message });

  buildNotificationPushPayload({
    recipient,
    actor,
    type,
    message,
    data: {
      notificationId: notification._id.toString(),
      type,
      actorId: actor ? actor.toString() : null,
      recipientId: recipient.toString(),
      postId: post ? post.toString() : null,
      commentId: comment ? comment.toString() : null,
      followRequestId: followRequest ? followRequest.toString() : null,
    },
  }).catch((error) => {
    logger.error("Push delivery failed after notification creation", {
      recipient: String(recipient),
      type,
      error: error.message,
    });
  });

  return notification;
};

const getNotifications = async (userId, query) => {
  const { page, limit, skip } = getPagination(query);
  const [items, total] = await Promise.all([
    Notification.find({ recipient: userId })
      .populate("actor", "username fullName avatar")
      .populate("post", "_id media caption")
      .populate("comment", "_id content")
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .lean(),
    Notification.countDocuments({ recipient: userId }),
  ]);

  return { items, meta: { page, limit, total, totalPages: Math.ceil(total / limit) } };
};

const markNotificationAsRead = async (userId, notificationId) =>
  Notification.findOneAndUpdate({ _id: notificationId, recipient: userId }, { isRead: true }, { new: true });

const markAllNotificationsAsRead = async (userId) =>
  Notification.updateMany({ recipient: userId, isRead: false }, { isRead: true });

const getUnreadNotificationCount = async (userId) =>
  Notification.countDocuments({ recipient: userId, isRead: false });

const getNotificationSummary = async (userId) => {
  const [unreadCount, latestNotification] = await Promise.all([
    getUnreadNotificationCount(userId),
    Notification.findOne({ recipient: userId }).sort({ createdAt: -1 }).select("createdAt").lean(),
  ]);

  return {
    unreadCount,
    lastNotificationAt: latestNotification?.createdAt || null,
  };
};

module.exports = {
  createNotification,
  getNotifications,
  markNotificationAsRead,
  markAllNotificationsAsRead,
  getUnreadNotificationCount,
  getNotificationSummary,
};
