const ContentFeature = require("../models/ContentFeature");
const CreatorAffinity = require("../models/CreatorAffinity");
const CreatorQuality = require("../models/CreatorQuality");
const Follow = require("../models/Follow");
const Block = require("../models/Block");
const Post = require("../models/Post");
const UserContentPreference = require("../models/UserContentPreference");

const CANDIDATE_POOL_QUOTAS = {
  feed: {
    followedCreators: 60,
    creatorAffinity: 40,
    topicSimilarity: 40,
    trending: 20,
    fresh: 20,
    exploration: 20,
    highQualityCreators: 20,
  },
  reels: {
    followedCreators: 40,
    creatorAffinity: 50,
    topicSimilarity: 50,
    trending: 30,
    fresh: 20,
    exploration: 30,
    highQualityCreators: 20,
  },
};

const MAX_MERGED_CANDIDATES = {
  feed: 220,
  reels: 260,
};

const normalizeSurface = (surface) => (surface === "reels" ? "reels" : "feed");
const isReelsSurface = (surface) => normalizeSurface(surface) === "reels";

const buildBasePostFilter = ({ userId, blockedUserIds, hiddenPostIds, surface }) => {
  const query = {
    author: { $nin: [userId, ...blockedUserIds] },
    _id: { $nin: hiddenPostIds },
  };

  if (isReelsSurface(surface)) {
    query["media.type"] = "video";
  }

  return query;
};

const getBlockedUserIds = async (userId) => {
  const blocks = await Block.find({ $or: [{ blocker: userId }, { blocked: userId }] }).select("blocker blocked");
  const blockedIds = new Set();

  for (const block of blocks) {
    blockedIds.add(String(block.blocker));
    blockedIds.add(String(block.blocked));
  }

  blockedIds.delete(String(userId));
  return [...blockedIds];
};

const getHiddenPostIds = async (userId) => {
  const hiddenIds = await UserContentPreference.find({
    user: userId,
    preferenceType: { $in: ["hide", "not_interested"] },
  }).distinct("post");

  return hiddenIds.map((item) => String(item));
};

const getTopInterestTopics = (interestProfile, limit = 12) => {
  const positive = Object.entries(interestProfile?.interestScores?.toObject?.() || interestProfile?.interestScores || {});
  const negative = Object.entries(interestProfile?.negativeTopicScores?.toObject?.() || interestProfile?.negativeTopicScores || {});
  const negativeMap = new Map(negative.map(([topic, score]) => [topic, Number(score || 0)]));

  const rankedTopics = positive
    .map(([topic, score]) => ({
      topic,
      score: Number(score || 0) - Number(negativeMap.get(topic) || 0),
    }))
    .filter((item) => item.score > 0)
    .sort((left, right) => right.score - left.score)
    .slice(0, limit)
    .map((item) => item.topic);

  if (rankedTopics.length) return rankedTopics;
  if (interestProfile?.onboardingTopics?.length) return interestProfile.onboardingTopics.slice(0, limit);

  return ["startup", "business", "creator", "technology", "lifestyle", "education"].slice(0, limit);
};

const loadPostsByOrderedIds = async ({ postIds, baseFilter }) => {
  if (!postIds.length) return [];

  const posts = await Post.find({
    ...baseFilter,
    _id: { $in: postIds.filter((id) => !baseFilter._id.$nin.includes(id)) },
  }).populate("author", "username fullName avatar followersCount");

  const postMap = new Map(posts.map((post) => [String(post._id), post]));
  return postIds.map((id) => postMap.get(String(id))).filter(Boolean);
};

const fetchFollowedCreatorPool = async ({ followIds, quota, baseFilter }) => {
  if (!followIds.length || !quota) return [];

  return Post.find({
    ...baseFilter,
    author: { $in: followIds },
  })
    .sort({ createdAt: -1 })
    .limit(quota)
    .populate("author", "username fullName avatar followersCount");
};

const fetchCreatorAffinityPool = async ({ userId, quota, baseFilter }) => {
  if (!quota) return [];

  const creatorIds = await CreatorAffinity.find({ user: userId })
    .sort({ score: -1, lastInteractionAt: -1 })
    .limit(Math.max(quota, 30))
    .distinct("creator");

  if (!creatorIds.length) return [];

  return Post.find({
    ...baseFilter,
    author: { $in: creatorIds },
  })
    .sort({ createdAt: -1 })
    .limit(quota)
    .populate("author", "username fullName avatar followersCount");
};

const fetchTopicSimilarityPool = async ({ surface, quota, baseFilter, interestTopics }) => {
  if (!quota || !interestTopics.length) return [];

  const contentType = isReelsSurface(surface) ? "reel" : "post";
  const featureRows = await ContentFeature.find({
    contentType,
    topics: { $in: interestTopics },
  })
    .sort({ qualityScore: -1, popularityScore: -1, createdAt: -1 })
    .limit(quota * 3)
    .select("post");

  const orderedPostIds = featureRows.map((row) => String(row.post));
  const hydrated = await loadPostsByOrderedIds({ postIds: orderedPostIds, baseFilter });

  if (hydrated.length >= quota) return hydrated.slice(0, quota);

  const fallback = await Post.find({
    ...baseFilter,
    hashtags: { $in: interestTopics },
  })
    .sort({ createdAt: -1 })
    .limit(quota)
    .populate("author", "username fullName avatar followersCount");

  const unique = new Map(hydrated.map((post) => [String(post._id), post]));
  fallback.forEach((post) => unique.set(String(post._id), post));

  return [...unique.values()].slice(0, quota);
};

const fetchTrendingPool = async ({ surface, quota, baseFilter }) => {
  if (!quota) return [];

  const contentType = isReelsSurface(surface) ? "reel" : "post";
  const featureRows = await ContentFeature.find({
    contentType,
    qualityScore: { $gte: 0.55 },
    spamScore: { $lte: 0.35 },
  })
    .sort({ popularityScore: -1, qualityScore: -1, createdAt: -1 })
    .limit(quota * 3)
    .select("post");

  const orderedPostIds = featureRows.map((row) => String(row.post));
  return loadPostsByOrderedIds({ postIds: orderedPostIds, baseFilter }).then((rows) => rows.slice(0, quota));
};

const fetchFreshPool = async ({ quota, baseFilter }) => {
  if (!quota) return [];

  return Post.find(baseFilter)
    .sort({ createdAt: -1 })
    .limit(quota)
    .populate("author", "username fullName avatar followersCount");
};

const fetchExplorationPool = async ({ surface, quota, baseFilter, interestTopics, followIds }) => {
  if (!quota) return [];

  const contentType = isReelsSurface(surface) ? "reel" : "post";
  const explorationFilter = {
    contentType,
    qualityScore: { $gte: 0.6 },
  };

  if (interestTopics.length) {
    explorationFilter.topics = { $nin: interestTopics.slice(0, 8) };
  }

  const featureRows = await ContentFeature.find(explorationFilter)
    .sort({ qualityScore: -1, createdAt: -1 })
    .limit(quota * 4)
    .select("post creator");

  const excludedFollowIds = new Set(followIds.map(String));
  const filteredIds = featureRows
    .filter((row) => !excludedFollowIds.has(String(row.creator)))
    .map((row) => String(row.post));

  return loadPostsByOrderedIds({ postIds: filteredIds, baseFilter }).then((rows) => rows.slice(0, quota));
};

const fetchHighQualityCreatorPool = async ({ quota, baseFilter }) => {
  if (!quota) return [];

  const creatorIds = await CreatorQuality.find({
    qualityScore: { $gte: 0.72 },
    spamPenalty: { $lte: 0.2 },
    abusePenalty: { $lte: 0.25 },
  })
    .sort({ qualityScore: -1, lastComputedAt: -1 })
    .limit(Math.max(quota, 30))
    .distinct("creator");

  if (!creatorIds.length) return [];

  return Post.find({
    ...baseFilter,
    author: { $in: creatorIds },
  })
    .sort({ createdAt: -1 })
    .limit(quota)
    .populate("author", "username fullName avatar followersCount");
};

const mergeCandidatePools = ({ pools, maxCandidates }) => {
  const merged = [];
  const mergedMap = new Map();
  const cursors = pools.map((pool) => ({ ...pool, index: 0 }));

  let progressed = true;
  while (merged.length < maxCandidates && progressed) {
    progressed = false;

    for (const pool of cursors) {
      while (pool.index < pool.items.length) {
        const post = pool.items[pool.index++];
        const postId = String(post._id);

        if (!mergedMap.has(postId)) {
          mergedMap.set(postId, {
            postId,
            sources: [pool.source],
          });
          merged.push(mergedMap.get(postId));
          progressed = true;
          break;
        }

        const existing = mergedMap.get(postId);
        if (!existing.sources.includes(pool.source)) {
          existing.sources.push(pool.source);
        }
      }
    }
  }

  return merged;
};

const generateCandidatePool = async ({ userId, surface, interestProfile }) => {
  const normalizedSurface = normalizeSurface(surface);
  const quotas = CANDIDATE_POOL_QUOTAS[normalizedSurface];
  const maxCandidates = MAX_MERGED_CANDIDATES[normalizedSurface];

  const [followIds, blockedUserIds, hiddenPostIds] = await Promise.all([
    Follow.find({ follower: userId }).distinct("following"),
    getBlockedUserIds(userId),
    getHiddenPostIds(userId),
  ]);

  const interestTopics = getTopInterestTopics(interestProfile, 12);
  const baseFilter = buildBasePostFilter({
    userId,
    blockedUserIds,
    hiddenPostIds,
    surface: normalizedSurface,
  });

  const pools = await Promise.all([
    fetchFollowedCreatorPool({ followIds, quota: quotas.followedCreators, baseFilter }),
    fetchCreatorAffinityPool({ userId, quota: quotas.creatorAffinity, baseFilter }),
    fetchTopicSimilarityPool({ surface: normalizedSurface, quota: quotas.topicSimilarity, baseFilter, interestTopics }),
    fetchTrendingPool({ surface: normalizedSurface, quota: quotas.trending, baseFilter }),
    fetchFreshPool({ quota: quotas.fresh, baseFilter }),
    fetchExplorationPool({ surface: normalizedSurface, quota: quotas.exploration, baseFilter, interestTopics, followIds }),
    fetchHighQualityCreatorPool({ quota: quotas.highQualityCreators, baseFilter }),
  ]);

  const poolEntries = [
    { source: "followed_creators", items: pools[0] },
    { source: "creator_affinity", items: pools[1] },
    { source: "topic_similarity", items: pools[2] },
    { source: "trending", items: pools[3] },
    { source: "fresh", items: pools[4] },
    { source: "exploration", items: pools[5] },
    { source: "high_quality_creators", items: pools[6] },
  ];

  return {
    items: mergeCandidatePools({ pools: poolEntries, maxCandidates }),
    meta: {
      surface: normalizedSurface,
      quotas,
      maxCandidates,
      generatedAt: new Date().toISOString(),
      poolSizes: poolEntries.reduce((accumulator, pool) => {
        accumulator[pool.source] = pool.items.length;
        return accumulator;
      }, {}),
    },
  };
};

module.exports = {
  CANDIDATE_POOL_QUOTAS,
  generateCandidatePool,
};
