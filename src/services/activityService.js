const Notification = require("../models/Notification");
const PostLike = require("../models/PostLike");
const SavedPost = require("../models/SavedPost");
const SearchHistory = require("../models/SearchHistory");
const { getPagination } = require("../utils/pagination");

const formatActivityItem = (item) => ({
  _id: item._id,
  type: item.type,
  actor: item.actor,
  post: item.post,
  comment: item.comment,
  message: item.message,
  isRead: item.isRead,
  createdAt: item.createdAt,
});

const getMyActivity = async (userId, query = {}) => {
  const { page, limit, skip } = getPagination(query);
  const activityTypes = ["like", "comment", "reply", "follow", "follow_request", "follow_accept"];
  const filter = { recipient: userId, type: { $in: activityTypes } };

  const [items, total, savedPostsCount, likedPostsCount, recentSearchesCount] = await Promise.all([
    Notification.find(filter)
      .populate("actor", "username fullName avatar")
      .populate("post", "_id media caption")
      .populate("comment", "_id content")
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .lean(),
    Notification.countDocuments(filter),
    SavedPost.countDocuments({ user: userId }),
    PostLike.countDocuments({ user: userId }),
    SearchHistory.countDocuments({ user: userId }),
  ]);

  return {
    items: items.map(formatActivityItem),
    meta: {
      page,
      limit,
      total,
      totalPages: Math.max(Math.ceil(total / limit), 1),
    },
    summary: {
      savedPostsCount,
      likedPostsCount,
      recentSearchesCount,
    },
  };
};

module.exports = {
  getMyActivity,
};
