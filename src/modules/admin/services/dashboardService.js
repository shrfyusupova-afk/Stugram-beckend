const User = require("../../../models/User");
const Post = require("../../../models/Post");
const Comment = require("../../../models/Comment");
const PostLike = require("../../../models/PostLike");
const Follow = require("../../../models/Follow");
const Report = require("../../../models/Report");
const AuditLog = require("../../../models/AuditLog");
const recommendationRefreshService = require("../../../services/recommendationRefreshService");
const { getDailyAnalytics } = require("./analyticsService");

const getStartOfToday = () => {
  const now = new Date();
  now.setHours(0, 0, 0, 0);
  return now;
};

const getDashboardStatistics = async () => {
  const startOfToday = getStartOfToday();

  const [totalUsers, activeUsersToday, postsCount, commentsCount, likesCount, followsCount, reportsCount, errorsCount, queueHealth, analytics] =
    await Promise.all([
      User.countDocuments({}),
      User.countDocuments({ lastLoginAt: { $gte: startOfToday } }),
      Post.countDocuments({}),
      Comment.countDocuments({}),
      PostLike.countDocuments({}),
      Follow.countDocuments({}),
      Report.countDocuments({}),
      AuditLog.countDocuments({ status: "failure" }),
      recommendationRefreshService.getRecommendationRefreshQueueHealth(),
      getDailyAnalytics({ days: 7 }),
    ]);

  return {
    totalUsers,
    activeUsersToday,
    postsCount,
    commentsCount,
    likesCount,
    followsCount,
    reportsCount,
    errorsCount,
    recommendationQueueFailedCount: queueHealth.queue.counts.failed,
    deadLetterCount: queueHealth.metrics.global.deadLetterCount,
    analytics,
  };
};

module.exports = {
  getDashboardStatistics,
};
