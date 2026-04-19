const mongoose = require("mongoose");

const { getPagination } = require("../utils/pagination");
const Post = require("../models/Post");
const Follow = require("../models/Follow");
const PostLike = require("../models/PostLike");
const User = require("../models/User");
const UserReport = require("../models/UserReport");
const ContentFeature = require("../models/ContentFeature");
const UserInterestProfile = require("../models/UserInterestProfile");
const CreatorAffinity = require("../models/CreatorAffinity");
const ContentWatchMetric = require("../models/ContentWatchMetric");
const RecommendationSnapshot = require("../models/RecommendationSnapshot");
const CreatorQuality = require("../models/CreatorQuality");
const RecommendationEvent = require("../models/RecommendationEvent");
const {
  ensureUserRecommendationStateVersion,
  enqueueAsyncRecommendationRefresh,
  getCandidatePoolCache,
  getRankedPageCache,
  setCandidatePoolCache,
  setRankedPageCache,
} = require("./recommendationCacheService");
const { generateCandidatePool } = require("./candidateGenerationService");
const { computeFeedScore, clamp } = require("./recommendationScoringService");

const EXPLORATION_RATIO = 0.15;

const toObjectIdStrings = (values = []) => values.map((value) => String(value));

const getOrCreateInterestProfile = async (userId) =>
  UserInterestProfile.findOneAndUpdate({ user: userId }, { $setOnInsert: { user: userId } }, { upsert: true, new: true });

const inferTopicsFromPost = (post) => {
  const topics = new Set((post.hashtags || []).slice(0, 6).map((topic) => topic.toLowerCase()));
  const captionTokens = (post.caption || "")
    .toLowerCase()
    .split(/[^a-z0-9]+/i)
    .filter(Boolean)
    .slice(0, 12);

  for (const token of captionTokens) {
    if (token.length >= 4) topics.add(token);
    if (topics.size >= 8) break;
  }

  return [...topics];
};

const buildTopicWeights = (topics = []) =>
  topics.reduce((accumulator, topic, index) => {
    accumulator[topic] = Number((1 - index * 0.08).toFixed(2));
    return accumulator;
  }, {});

const ensureContentFeature = async (post) => {
  const topics = inferTopicsFromPost(post);
  const contentType = post.media?.some((item) => item.type === "video") ? "reel" : "post";
  const popularityBase =
    clamp(Math.log1p((post.likesCount || 0) * 2 + (post.commentsCount || 0) * 3) / Math.log(50)) * 0.7 +
    clamp((Date.now() - new Date(post.createdAt).getTime()) < 2 * 24 * 60 * 60 * 1000 ? 0.3 : 0.1);

  return ContentFeature.findOneAndUpdate(
    { post: post._id },
    {
      $setOnInsert: {
        post: post._id,
        creator: post.author,
      },
      $set: {
        contentType,
        topics,
        topicWeights: buildTopicWeights(topics),
        popularityScore: clamp(Number(popularityBase.toFixed(4))),
      },
    },
    { upsert: true, new: true }
  );
};

const loadCreatorQualityMap = async (creatorIds) => {
  const rows = await CreatorQuality.find({ creator: { $in: creatorIds } });
  return new Map(rows.map((row) => [String(row.creator), row]));
};

const loadCreatorAffinityMap = async (userId, creatorIds) => {
  const rows = await CreatorAffinity.find({ user: userId, creator: { $in: creatorIds } });
  return new Map(rows.map((row) => [String(row.creator), row]));
};

const loadWatchMetricMap = async (userId, postIds) => {
  const rows = await ContentWatchMetric.find({ user: userId, post: { $in: postIds } });
  return new Map(rows.map((row) => [String(row.post), row]));
};

const loadLikedPostSet = async (userId, postIds) => {
  const rows = await PostLike.find({ user: userId, post: { $in: postIds } }).select("post");
  return new Set(rows.map((row) => String(row.post)));
};

const loadRecentEvents = async (userId, postIds) => {
  const rows = await RecommendationEvent.find({
    user: userId,
    content: { $in: postIds },
    createdAt: { $gte: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000) },
  })
    .sort({ createdAt: -1 })
    .lean();

  const grouped = new Map();
  for (const row of rows) {
    const key = String(row.content);
    if (!grouped.has(key)) grouped.set(key, []);
    grouped.get(key).push(row);
  }
  return grouped;
};

const deriveCandidateSignals = ({ postId, recentEventsMap, likedPostSet, hiddenPostIds }) => {
  const events = recentEventsMap.get(String(postId)) || [];
  return {
    liked: likedPostSet.has(String(postId)) ? 1 : 0,
    saved: events.some((item) => item.eventType === "save") ? 1 : 0,
    shared: events.some((item) => item.eventType === "share") ? 1 : 0,
    commented: events.some((item) => item.eventType === "comment") ? 1 : 0,
    followAfterView: events.some((item) => item.eventType === "follow") ? 1 : 0,
    profileVisitAfterView: events.some((item) => item.eventType === "profile_visit") ? 1 : 0,
    fastSkipped: events.some((item) => item.eventType === "fast_skip") ? 1 : 0,
    hidden: hiddenPostIds.has(String(postId)) ? 1 : 0,
    notInterested: events.some((item) => item.eventType === "not_interested") ? 1 : 0,
    reported: events.some((item) => item.eventType === "report") ? 1 : 0,
    blocked: events.some((item) => item.eventType === "block") ? 1 : 0,
  };
};

const persistSnapshot = async ({ userId, surface, page, limit, ranked }) => {
  await RecommendationSnapshot.create({
    user: userId,
    surface,
    page,
    limit,
    candidates: ranked.slice(0, limit).map((item) => ({
      itemId: item.post._id,
      score: item.score,
      reasons: item.reasons,
      exploration: item.exploration,
    })),
    expiresAt: new Date(Date.now() + 10 * 60 * 1000),
  });
};

const buildMeta = (page, limit, total) => ({
  page,
  limit,
  total,
  totalPages: Math.max(Math.ceil(total / limit), 1),
  strategy: "weighted-ranking-v1",
  explorationRatio: EXPLORATION_RATIO,
});

const pickExplorationCandidates = (ranked, limit) => {
  const target = Math.max(1, Math.round(limit * EXPLORATION_RATIO));
  const explorationPool = ranked
    .filter((item) => item.exploration)
    .sort((left, right) => right.score - left.score)
    .slice(0, target);

  const selectedIds = new Set(explorationPool.map((item) => String(item.post._id)));
  const mainPool = ranked.filter((item) => !selectedIds.has(String(item.post._id)));

  return [...mainPool.slice(0, Math.max(limit - explorationPool.length, 0)), ...explorationPool].sort((left, right) => right.score - left.score);
};

const buildReasons = ({ components, contentFeature, creatorAffinity, exploration }) => {
  const reasons = [];
  if (components.topicMatchScore >= 0.55) reasons.push("topic_match");
  if (components.watchTimeScore >= 0.55) reasons.push("watch_retention");
  if (components.creatorAffinityScore >= 0.55) reasons.push("creator_affinity");
  if (components.freshnessScore >= 0.7) reasons.push("fresh");
  if ((contentFeature?.popularityScore || 0) >= 0.6) reasons.push("trending");
  if (exploration) reasons.push("exploration");
  if ((creatorAffinity?.followScore || 0) > 0.5) reasons.push("follow_graph");
  return reasons;
};

const hydrateCandidatePoolItems = async (candidateItems = []) => {
  if (!candidateItems.length) return [];

  const posts = await Post.find({
    _id: { $in: candidateItems.map((item) => item.postId) },
  }).populate("author", "username fullName avatar followersCount");

  const postMap = new Map(posts.map((post) => [String(post._id), post]));
  return candidateItems
    .map((item) => {
      const post = postMap.get(String(item.postId));
      if (!post) return null;

      return {
        post,
        candidateSources: item.sources || [],
      };
    })
    .filter(Boolean);
};

const computeCreatorQuality = async (creatorIds) => {
  const distinctCreatorIds = [...new Set(toObjectIdStrings(creatorIds))];
  if (!distinctCreatorIds.length) return new Map();

  const [reports, hides, suspiciousActivity, users] = await Promise.all([
    UserReport.aggregate([
      { $match: { reportedUser: { $in: distinctCreatorIds.map((id) => new mongoose.Types.ObjectId(id)) } } },
      { $group: { _id: "$reportedUser", count: { $sum: 1 } } },
    ]),
    RecommendationEvent.aggregate([
      { $match: { creator: { $in: distinctCreatorIds.map((id) => new mongoose.Types.ObjectId(id)) }, eventType: { $in: ["hide", "not_interested"] } } },
      { $group: { _id: "$creator", count: { $sum: 1 } } },
    ]),
    RecommendationEvent.aggregate([
      { $match: { creator: { $in: distinctCreatorIds.map((id) => new mongoose.Types.ObjectId(id)) } } },
      {
        $group: {
          _id: "$creator",
          impressions: {
            $sum: {
              $cond: [{ $in: ["$eventType", ["feed_impression", "reel_impression"]] }, 1, 0],
            },
          },
          strongActions: {
            $sum: {
              $cond: [{ $in: ["$eventType", ["like", "save", "share", "comment", "follow"]] }, 1, 0],
            },
          },
        },
      },
    ]),
    User.find({ _id: { $in: distinctCreatorIds } }).select("followersCount"),
  ]);

  const reportMap = new Map(reports.map((row) => [String(row._id), row.count]));
  const hideMap = new Map(hides.map((row) => [String(row._id), row.count]));
  const suspiciousMap = new Map(
    suspiciousActivity.map((row) => [
      String(row._id),
      row.impressions > 50 && row.strongActions / row.impressions > 0.85 ? 0.2 : 0,
    ])
  );

  const qualityMap = new Map();
  for (const user of users) {
    const creatorId = String(user._id);
    const followerScale = clamp(Math.log1p(user.followersCount || 0) / Math.log(10000));
    const reportRate = clamp((reportMap.get(creatorId) || 0) / Math.max(user.followersCount || 1, 10));
    const hideRate = clamp((hideMap.get(creatorId) || 0) / Math.max(user.followersCount || 1, 10));
    const suspiciousEngagementPenalty = suspiciousMap.get(creatorId) || 0;
    const qualityScore = clamp(0.55 + followerScale * 0.2 - reportRate * 0.5 - hideRate * 0.4 - suspiciousEngagementPenalty);

    qualityMap.set(creatorId, {
      creator: user._id,
      qualityScore,
      spamPenalty: hideRate * 0.25,
      abusePenalty: reportRate * 0.5,
      suspiciousEngagementPenalty,
      reportRate,
      hideRate,
      lastComputedAt: new Date(),
    });
  }

  await Promise.all(
    [...qualityMap.values()].map((entry) =>
      CreatorQuality.findOneAndUpdate({ creator: entry.creator }, entry, { upsert: true, new: true })
    )
  );

  return qualityMap;
};

const getPersonalizedFeed = async (userId, query, { surface = "feed", bypassCache = false, expectedVersion = null } = {}) => {
  const { page, limit } = getPagination(query);
  const liveVersion = await ensureUserRecommendationStateVersion(userId);
  const version = expectedVersion && Number(expectedVersion) > 0 ? Number(expectedVersion) : liveVersion;

  if (!bypassCache) {
    const cachedPage = await getRankedPageCache({ surface, userId, page, limit, version });
    if (cachedPage?.status === "fresh") return cachedPage.payload;
    if (cachedPage?.status === "stale") {
      await enqueueAsyncRecommendationRefresh({
        userId,
        surfaces: [surface],
        reason: "stale_ranked_page",
        version,
      });
      return cachedPage.payload;
    }
  }

  const [interestProfile, followIds] = await Promise.all([
    getOrCreateInterestProfile(userId),
    Follow.find({ follower: userId }).distinct("following"),
  ]);

  let candidatePool = await getCandidatePoolCache({ surface, userId, version });
  if (!bypassCache && candidatePool?.status === "stale") {
    await enqueueAsyncRecommendationRefresh({
      userId,
      surfaces: [surface],
      reason: "stale_candidate_pool",
      version,
    });
  }

  if (!candidatePool || candidatePool.status === "expired" || (bypassCache && candidatePool.status !== "fresh")) {
    const generatedPool = await generateCandidatePool({ userId, surface, interestProfile });
    await setCandidatePoolCache({
      surface,
      userId,
      version,
      payload: generatedPool,
    });
    candidatePool = {
      status: "fresh",
      payload: generatedPool,
    };
  }

  const hydratedCandidateEntries = await hydrateCandidatePoolItems(candidatePool.payload?.items || []);
  const candidatePosts = hydratedCandidateEntries.map((entry) => entry.post);
  const candidateSourceMap = new Map(
    hydratedCandidateEntries.map((entry) => [String(entry.post._id), entry.candidateSources || []])
  );

  if (!candidatePosts.length) {
    return { items: [], meta: buildMeta(page, limit, 0) };
  }

  const [contentFeatures, creatorAffinityMap, watchMetricMap, likedPostSet, recentEventsMap] = await Promise.all([
    Promise.all(candidatePosts.map((post) => ensureContentFeature(post))),
    loadCreatorAffinityMap(userId, candidatePosts.map((post) => post.author._id || post.author)),
    loadWatchMetricMap(userId, candidatePosts.map((post) => post._id)),
    loadLikedPostSet(userId, candidatePosts.map((post) => post._id)),
    loadRecentEvents(userId, candidatePosts.map((post) => post._id)),
  ]);

  const contentFeatureMap = new Map(contentFeatures.map((feature) => [String(feature.post), feature]));
  let creatorQualityMap = await loadCreatorQualityMap(candidatePosts.map((post) => post.author._id || post.author));
  if (!creatorQualityMap.size) {
    creatorQualityMap = await computeCreatorQuality(candidatePosts.map((post) => post.author._id || post.author));
  }

  const diversityContext = { seenCreators: new Set(), seenTopics: new Set() };
  const hiddenPostIds = new Set();
  const ranked = [];

  for (const post of candidatePosts) {
    const contentFeature = contentFeatureMap.get(String(post._id));
    const creatorId = String(post.author._id || post.author);
    const creatorAffinity = creatorAffinityMap.get(creatorId);
    const watchMetric = watchMetricMap.get(String(post._id));
    const candidateSignals = deriveCandidateSignals({
      postId: post._id,
      recentEventsMap,
      likedPostSet,
      hiddenPostIds,
    });
    const exploration =
      !followIds.some((id) => String(id) === creatorId) &&
      (contentFeature?.topics || []).some((topic) => !(interestProfile.onboardingTopics || []).includes(topic));
    const explorationBoost = exploration ? 1 : 0;

    const { score, components } = computeFeedScore({
      interestProfile,
      contentFeature,
      creatorAffinity,
      creatorQuality: creatorQualityMap.get(creatorId),
      watchMetric,
      candidateSignals,
      post,
      diversityContext,
      explorationBoost,
    });

    ranked.push({
      post,
      score,
      exploration,
      reasons: buildReasons({ components, contentFeature, creatorAffinity, exploration }),
      debug: components,
      candidateSources: candidateSourceMap.get(String(post._id)) || [],
    });

    diversityContext.seenCreators.add(creatorId);
    (contentFeature?.topics || []).forEach((topic) => diversityContext.seenTopics.add(topic));
  }

  ranked.sort((left, right) => right.score - left.score);

  const diversified = pickExplorationCandidates(ranked, page * limit);
  const start = (page - 1) * limit;
  const selected = diversified.slice(start, start + limit);

  const response = {
    items: selected.map((item) => ({
      ...item.post.toObject(),
      recommendation: {
        score: item.score,
        reasons: item.reasons,
        exploration: item.exploration,
        candidateSources: item.candidateSources,
      },
    })),
    meta: buildMeta(page, limit, ranked.length),
  };

  await Promise.all([
    setRankedPageCache({ surface, userId, page, limit, version, payload: response }),
    persistSnapshot({ userId, surface, page, limit, ranked: selected }),
  ]);

  return response;
};

module.exports = {
  ensureContentFeature,
  getOrCreateInterestProfile,
  getPersonalizedFeed,
  computeCreatorQuality,
};
