const Post = require("../models/Post");
const User = require("../models/User");
const { getPagination } = require("../utils/pagination");
const profileSuggestionService = require("./profileSuggestionService");

const TRENDING_POST_FETCH_LIMIT = 60;
const TRENDING_DEFAULT_LIMIT = 8;
const TRENDING_LOOKBACK_DAYS = 14;

const creatorProjection = "_id username fullName avatar bio followersCount isPrivateAccount";
const authorPopulateProjection = "username fullName avatar bio followersCount isPrivateAccount";

const mapCreatorPreview = (user) => ({
  _id: user._id,
  username: user.username,
  fullName: user.fullName,
  avatar: user.avatar,
  bio: user.bio,
  followersCount: user.followersCount,
  isPrivateAccount: user.isPrivateAccount,
});

const mapExplorePostPreview = (post) => ({
  _id: post._id,
  author: post.author
    ? {
        _id: post.author._id,
        username: post.author.username,
        fullName: post.author.fullName,
        avatar: post.author.avatar,
      }
    : null,
  media: post.media,
  caption: post.caption,
  location: post.location,
  likesCount: post.likesCount,
  commentsCount: post.commentsCount,
  createdAt: post.createdAt,
});

const computeTrendingScore = (post) => {
  const ageMs = Date.now() - new Date(post.createdAt).getTime();
  const ageDays = ageMs / (24 * 60 * 60 * 1000);
  const engagementScore = (post.likesCount || 0) * 2 + (post.commentsCount || 0) * 3;
  const freshnessBoost = ageDays <= 1 ? 12 : ageDays <= 3 ? 7 : ageDays <= 7 ? 4 : 1;
  return engagementScore + freshnessBoost;
};

const getTrendingExplore = async (query = {}) => {
  const limit = Math.min(Number(query.limit) || TRENDING_DEFAULT_LIMIT, 20);
  const since = new Date(Date.now() - TRENDING_LOOKBACK_DAYS * 24 * 60 * 60 * 1000);

  const recentPosts = await Post.find({
    isHiddenByAdmin: false,
    createdAt: { $gte: since },
  })
    .populate("author", authorPopulateProjection)
    .sort({ createdAt: -1 })
    .limit(TRENDING_POST_FETCH_LIMIT)
    .lean();

  const visiblePosts = recentPosts
    .filter((post) => post.author && !post.author.isPrivateAccount)
    .map((post) => ({
      ...post,
      __trendingScore: computeTrendingScore(post),
    }))
    .sort((left, right) => right.__trendingScore - left.__trendingScore);

  const posts = visiblePosts
    .filter((post) => !post.media?.some((item) => item.type === "video"))
    .slice(0, limit)
    .map(mapExplorePostPreview);

  const reels = visiblePosts
    .filter((post) => post.media?.some((item) => item.type === "video"))
    .slice(0, limit)
    .map(mapExplorePostPreview);

  const creatorMap = new Map();
  for (const post of visiblePosts) {
    if (!post.author) continue;
    const key = String(post.author._id);
    if (!creatorMap.has(key)) {
      creatorMap.set(key, {
        ...mapCreatorPreview(post.author),
        __score: computeTrendingScore(post) + (post.author.followersCount || 0) * 0.01,
      });
    }
  }

  let creators = [...creatorMap.values()]
    .sort((left, right) => right.__score - left.__score)
    .slice(0, limit)
    .map(({ __score, ...creator }) => creator);

  if (creators.length < limit) {
    const existingIds = new Set(creators.map((creator) => String(creator._id)));
    const fallbackCreators = await User.find({
      _id: { $nin: [...existingIds] },
      isPrivateAccount: false,
      isSuspended: false,
    })
      .select(creatorProjection)
      .sort({ followersCount: -1, createdAt: -1 })
      .limit(limit - creators.length)
      .lean();

    creators = creators.concat(fallbackCreators.map(mapCreatorPreview));
  }

  return {
    posts,
    reels,
    creators,
  };
};

const getCreatorsDiscovery = async (userId, query = {}) => {
  const result = await profileSuggestionService.getProfileSuggestions(userId, query);

  return {
    items: result.items.map((candidate) => ({
      _id: candidate._id,
      username: candidate.username,
      fullName: candidate.fullName,
      avatar: candidate.avatar,
      bio: candidate.bio,
      followersCount: candidate.followersCount,
      isPrivateAccount: candidate.isPrivateAccount,
    })),
    meta: result.meta,
  };
};

module.exports = {
  getTrendingExplore,
  getCreatorsDiscovery,
};
