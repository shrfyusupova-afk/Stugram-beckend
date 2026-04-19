const User = require("../models/User");
const Post = require("../models/Post");
const SearchHistory = require("../models/SearchHistory");
const { getPagination } = require("../utils/pagination");
const { getFollowStatusesForUsers } = require("./followService");

const escapeRegex = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const normalizeSearchInput = (value = "") => value.trim().slice(0, 60);
const buildSafeRegex = (value) => new RegExp(escapeRegex(normalizeSearchInput(value)), "i");
const SEARCH_HISTORY_LIMIT = 50;
const userPreviewProjection =
  "_id username fullName avatar bio region district school grade group followersCount";
const mapUserPreview = (user) => ({
  _id: user._id,
  username: user.username,
  fullName: user.fullName,
  avatar: user.avatar,
  bio: user.bio,
  region: user.region,
  district: user.district,
  school: user.school,
  grade: user.grade,
  group: user.group,
  followersCount: user.followersCount,
});

const suggestionUserProjection = "_id username fullName avatar bio school location";
const mapSuggestionUser = (user) => ({
  _id: user._id,
  username: user.username,
  fullName: user.fullName,
  avatar: user.avatar,
  bio: user.bio,
  school: user.school,
  location: user.location,
});

const searchUsers = async (query, viewerId = null) => {
  const { page, limit, skip } = getPagination(query);
  const filter = { username: buildSafeRegex(query.q) };
  const [items, total] = await Promise.all([
    User.find(filter).select("username fullName avatar bio").skip(skip).limit(limit).lean(),
    User.countDocuments(filter),
  ]);
  const statuses = await getFollowStatusesForUsers(viewerId, items.map((item) => item._id));
  return {
    items: items.map((item) => ({
      ...item,
      followStatus: statuses.get(String(item._id)) || "not_following",
    })),
    meta: { page, limit, total, totalPages: Math.ceil(total / limit) },
  };
};

const buildAdvancedUsersFilter = (query = {}) => {
  const filter = {};
  const exactFilters = ["region", "district", "grade", "group"];
  const partialFilters = ["school", "location", "username", "fullName"];

  for (const field of exactFilters) {
    if (query[field]) {
      filter[field] = query[field].trim();
    }
  }

  for (const field of partialFilters) {
    if (query[field]) {
      filter[field] = buildSafeRegex(query[field]);
    }
  }

  return filter;
};

const searchUsersAdvanced = async (query, viewerId = null) => {
  const { page, limit, skip } = getPagination(query);
  const filter = buildAdvancedUsersFilter(query);

  const [items, total] = await Promise.all([
    User.find(filter)
      .select(userPreviewProjection)
      .sort({ followersCount: -1, createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .lean(),
    User.countDocuments(filter),
  ]);

  const statuses = await getFollowStatusesForUsers(viewerId, items.map((item) => item._id));

  return {
    items: items.map((item) => ({
      ...mapUserPreview(item),
      followStatus: statuses.get(String(item._id)) || "not_following",
    })),
    meta: { page, limit, total, totalPages: Math.ceil(total / limit) },
  };
};

const searchPosts = async (query) => {
  const { page, limit, skip } = getPagination(query);
  const regex = buildSafeRegex(query.q);
  const filter = {
    $or: [{ caption: regex }, { location: regex }, { hashtags: regex }],
  };
  const [items, total] = await Promise.all([
    Post.find(filter)
      .populate("author", "username fullName avatar")
      .skip(skip)
      .limit(limit)
      .sort({ createdAt: -1 })
      .lean(),
    Post.countDocuments(filter),
  ]);
  return { items, meta: { page, limit, total, totalPages: Math.ceil(total / limit) } };
};

const searchHashtags = async (query) => {
  const { page, limit, skip } = getPagination(query);
  const hashtagRegex = buildSafeRegex(query.q.replace(/^#/, ""));
  const hashtagResults = await Post.aggregate([
    { $unwind: "$hashtags" },
    { $match: { hashtags: hashtagRegex } },
    { $group: { _id: "$hashtags", postsCount: { $sum: 1 } } },
    { $sort: { postsCount: -1 } },
    { $skip: skip },
    { $limit: limit },
  ]);

  return {
    items: hashtagResults.map((item) => ({ hashtag: item._id, postsCount: item.postsCount })),
    meta: { page, limit },
  };
};

const getSearchSuggestions = async (query = {}) => {
  const normalizedQuery = normalizeSearchInput(query.q || "");
  if (!normalizedQuery) {
    return { users: [], hashtags: [], keywords: [] };
  }

  const regex = buildSafeRegex(normalizedQuery);

  const [users, hashtags, schools, locations] = await Promise.all([
    User.find({
      $or: [
        { username: regex },
        { fullName: regex },
        { school: regex },
        { location: regex },
      ],
      isSuspended: false,
    })
      .select(suggestionUserProjection)
      .sort({ followersCount: -1, createdAt: -1 })
      .limit(6)
      .lean(),
    Post.aggregate([
      { $unwind: "$hashtags" },
      { $match: { hashtags: regex } },
      { $group: { _id: "$hashtags", postsCount: { $sum: 1 } } },
      { $sort: { postsCount: -1 } },
      { $limit: 5 },
    ]),
    User.aggregate([
      {
        $match: {
          $and: [{ school: regex }, { school: { $ne: "" } }],
          isSuspended: false,
        },
      },
      { $group: { _id: "$school" } },
      { $limit: 3 },
    ]),
    User.aggregate([
      {
        $match: {
          $and: [{ location: regex }, { location: { $ne: "" } }],
          isSuspended: false,
        },
      },
      { $group: { _id: "$location" } },
      { $limit: 3 },
    ]),
  ]);

  const keywords = [...new Set([...schools, ...locations].map((item) => item._id).filter(Boolean))].slice(0, 6);

  return {
    users: users.map(mapSuggestionUser),
    hashtags: hashtags.map((item) => ({
      hashtag: item._id,
      postsCount: item.postsCount,
    })),
    keywords,
  };
};

const trimSearchHistoryToLimit = async (userId) => {
  const total = await SearchHistory.countDocuments({ user: userId });
  if (total <= SEARCH_HISTORY_LIMIT) return;

  const extraItems = await SearchHistory.find({ user: userId })
    .sort({ updatedAt: -1, createdAt: -1 })
    .skip(SEARCH_HISTORY_LIMIT)
    .select("_id")
    .lean();

  await SearchHistory.deleteMany({
    _id: { $in: extraItems.map((item) => item._id) },
  });
};

const saveSearchHistory = async (userId, payload) => {
  const queryText = payload.queryText.trim();

  let historyItem;

  if (payload.targetId) {
    historyItem = await SearchHistory.findOneAndUpdate(
      { user: userId, searchType: payload.searchType, targetId: payload.targetId },
      { $set: { queryText, updatedAt: new Date() } },
      { new: true, upsert: true, setDefaultsOnInsert: true }
    );
  } else {
    historyItem = await SearchHistory.findOneAndUpdate(
      { user: userId, searchType: payload.searchType, queryText, targetId: null },
      { $set: { updatedAt: new Date() } },
      { new: true, upsert: true, setDefaultsOnInsert: true }
    );
  }

  await trimSearchHistoryToLimit(userId);

  return historyItem;
};

const getSearchHistory = async (userId, query = {}) => {
  const { page, limit, skip } = getPagination(query);
  const filter = { user: userId };

  const [items, total] = await Promise.all([
    SearchHistory.find(filter).sort({ updatedAt: -1, createdAt: -1 }).skip(skip).limit(limit).lean(),
    SearchHistory.countDocuments(filter),
  ]);

  return {
    items,
    meta: {
      page,
      limit,
      total,
      totalPages: Math.ceil(total / limit) || 1,
    },
  };
};

const deleteSearchHistoryItem = async (userId, historyId) => {
  const deletedItem = await SearchHistory.findOneAndDelete({ _id: historyId, user: userId });

  return {
    deleted: Boolean(deletedItem),
    historyId,
  };
};

const clearSearchHistory = async (userId) => {
  const result = await SearchHistory.deleteMany({ user: userId });

  return {
    cleared: true,
    deletedCount: result.deletedCount || 0,
  };
};

module.exports = {
  searchUsers,
  searchUsersAdvanced,
  searchPosts,
  searchHashtags,
  getSearchSuggestions,
  saveSearchHistory,
  getSearchHistory,
  deleteSearchHistoryItem,
  clearSearchHistory,
};
