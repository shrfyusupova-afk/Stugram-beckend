const User = require("../../../models/User");
const Post = require("../../../models/Post");
const PostLike = require("../../../models/PostLike");
const Follow = require("../../../models/Follow");

const buildDailyRange = (days = 7) => {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  start.setDate(start.getDate() - (days - 1));
  return start;
};

const aggregatePerDay = async (Model, dateField = "createdAt", match = {}, days = 7) => {
  const fromDate = buildDailyRange(days);

  return Model.aggregate([
    {
      $match: {
        ...match,
        [dateField]: { $gte: fromDate },
      },
    },
    {
      $group: {
        _id: {
          year: { $year: `$${dateField}` },
          month: { $month: `$${dateField}` },
          day: { $dayOfMonth: `$${dateField}` },
        },
        count: { $sum: 1 },
      },
    },
    {
      $sort: {
        "_id.year": 1,
        "_id.month": 1,
        "_id.day": 1,
      },
    },
    {
      $project: {
        _id: 0,
        date: {
          $concat: [
            { $toString: "$_id.year" },
            "-",
            {
              $cond: [{ $lt: ["$_id.month", 10] }, { $concat: ["0", { $toString: "$_id.month" }] }, { $toString: "$_id.month" }],
            },
            "-",
            {
              $cond: [{ $lt: ["$_id.day", 10] }, { $concat: ["0", { $toString: "$_id.day" }] }, { $toString: "$_id.day" }],
            },
          ],
        },
        count: 1,
      },
    },
  ]);
};

const getDailyAnalytics = async ({ days = 7 } = {}) => {
  const [dailyActiveUsers, postsPerDay, likesPerDay, followsPerDay] = await Promise.all([
    aggregatePerDay(User, "lastLoginAt", { lastLoginAt: { $ne: null } }, days),
    aggregatePerDay(Post, "createdAt", {}, days),
    aggregatePerDay(PostLike, "createdAt", {}, days),
    aggregatePerDay(Follow, "createdAt", {}, days),
  ]);

  return {
    dailyActiveUsers,
    postsPerDay,
    likesPerDay,
    followsPerDay,
  };
};

module.exports = {
  getDailyAnalytics,
};
