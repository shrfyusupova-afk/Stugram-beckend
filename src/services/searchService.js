const User = require("../models/User");
const Post = require("../models/Post");
const SearchHistory = require("../models/SearchHistory");
const Follow = require("../models/Follow");
const { getPagination, buildPaginationMeta } = require("../utils/pagination");
const { getFollowStatusesForUsers } = require("./followService");
const { loadBlockedUserIds } = require("./chatSecurityService");

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

const applyUserVisibility = async (viewerId, users = []) => {
  if (!users.length) return [];

  const blockedIds = viewerId ? await loadBlockedUserIds(viewerId) : new Set();
  const userIds = users.map((item) => String(item._id));
  const followedIds = viewerId
    ? new Set(
        (await Follow.find({ follower: viewerId, following: { $in: userIds } })
          .select("following")
          .lean()).map((row) => String(row.following))
      )
    : new Set();

  return users.filter((item) => {
    const userId = String(item._id);
    if (blockedIds.has(userId)) return false;
    if (item.isSuspended) return false;
    if (viewerId && userId === String(viewerId)) return true;
    if (!item.isPrivateAccount) return true;
    return followedIds.has(userId);
  });
};

const applyPostVisibility = async (viewerId, posts = []) => {
  if (!posts.length) return [];

  const blockedIds = viewerId ? await loadBlockedUserIds(viewerId) : new Set();
  const privateAuthorIds = posts
    .map((post) => post.author)
    .filter((author) => author?.isPrivateAccount)
    .map((author) => String(author._id));
  const followedIds = viewerId && privateAuthorIds.length
    ? new Set(
        (await Follow.find({ follower: viewerId, following: { $in: privateAuthorIds } })
          .select("following")
          .lean()).map((row) => String(row.following))
      )
    : new Set();

  return posts.filter((post) => {
    const author = post.author;
    if (!author?._id) return false;
    const authorId = String(author._id);
    if (blockedIds.has(authorId)) return false;
    if (viewerId && authorId === String(viewerId)) return true;
    if (!author.isPrivateAccount) return true;
    return followedIds.has(authorId);
  });
};

const searchUsers = async (query, viewerId = null) => {
  const { page, limit, skip } = getPagination(query);
  const filter = { username: buildSafeRegex(query.q) };
  const items = await User.find(filter)
    .select("username fullName avatar bio isPrivateAccount isSuspended")
    .sort({ followersCount: -1, createdAt: -1 })
    .limit(skip + Math.max(limit * 3, 60))
    .lean();
  const visibleItems = (await applyUserVisibility(viewerId, items)).slice(skip, skip + limit);
  const statuses = await getFollowStatusesForUsers(viewerId, visibleItems.map((item) => item._id));
  return {
    items: visibleItems.map((item) => ({
      ...item,
      followStatus: statuses.get(String(item._id)) || "not_following",
    })),
    meta: buildPaginationMeta({ page, limit, total: visibleItems.length < limit && skip === 0 ? visibleItems.length : items.length }),
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

  const items = await User.find(filter)
    .select(`${userPreviewProjection} isPrivateAccount isSuspended`)
    .sort({ followersCount: -1, createdAt: -1 })
    .limit(skip + Math.max(limit * 3, 60))
    .lean();
  const visibleItems = (await applyUserVisibility(viewerId, items)).slice(skip, skip + limit);
  const statuses = await getFollowStatusesForUsers(viewerId, visibleItems.map((item) => item._id));

  return {
    items: visibleItems.map((item) => ({
      ...mapUserPreview(item),
      followStatus: statuses.get(String(item._id)) || "not_following",
    })),
    meta: buildPaginationMeta({ page, limit, total: visibleItems.length < limit && skip === 0 ? visibleItems.length : items.length }),
  };
};

const searchPosts = async (query, viewerId = null) => {
  const { page, limit, skip } = getPagination(query);
  const regex = buildSafeRegex(query.q);
  const filter = {
    $or: [{ caption: regex }, { location: regex }, { hashtags: regex }],
  };
  const items = await Post.find(filter)
    .populate("author", "username fullName avatar isPrivateAccount isSuspended")
    .limit(skip + Math.max(limit * 3, 60))
    .sort({ createdAt: -1 })
    .lean();
  const visibleItems = (await applyPostVisibility(viewerId, items)).slice(skip, skip + limit);
  return {
    items: visibleItems,
    meta: buildPaginationMeta({ page, limit, total: visibleItems.length < limit && skip === 0 ? visibleItems.length : items.length }),
  };
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

const getSearchSuggestions = async (query = {}, viewerId = null) => {
  const normalizedQuery = normalizeSearchInput(query.q || "");
  if (!normalizedQuery) {
    return { users: [], hashtags: [], keywords: [] };
  }

  const regex = buildSafeRegex(normalizedQuery);

  const [usersRaw, hashtags, schools, locations] = await Promise.all([
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
  const users = await applyUserVisibility(viewerId, usersRaw);

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
