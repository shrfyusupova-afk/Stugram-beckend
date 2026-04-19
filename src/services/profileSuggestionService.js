const mongoose = require("mongoose");

const Follow = require("../models/Follow");
const User = require("../models/User");
const Block = require("../models/Block");
const CreatorAffinity = require("../models/CreatorAffinity");
const UserInterestProfile = require("../models/UserInterestProfile");
const ProfileSuggestionFeature = require("../models/ProfileSuggestionFeature");
const CreatorQuality = require("../models/CreatorQuality");
const RecommendationEvent = require("../models/RecommendationEvent");
const { getPagination } = require("../utils/pagination");
const { cacheRankedResult, getCachedRankedResult } = require("./recommendationCacheService");
const { computeProfileSuggestionScore, computeTopicMatch, clamp } = require("./recommendationScoringService");
const { computeCreatorQuality } = require("./recommendationService");
const { getFollowStatusesForUsers } = require("./followService");

const getBlockedIds = async (userId) => {
  const blocks = await Block.find({ $or: [{ blocker: userId }, { blocked: userId }] }).select("blocker blocked");
  const ids = new Set();
  blocks.forEach((item) => {
    ids.add(String(item.blocker));
    ids.add(String(item.blocked));
  });
  ids.delete(String(userId));
  return [...ids];
};

const arrayIntersectionCount = (left = [], right = []) => {
  const set = new Set(right.map(String));
  return left.filter((value) => set.has(String(value))).length;
};

const getProfileSuggestions = async (userId, query, { bypassCache = false, expectedVersion = null } = {}) => {
  const { page, limit } = getPagination(query);
  const surface = "profiles";
  if (!bypassCache) {
    const cached = await getCachedRankedResult({ surface, userId, page, limit, version: expectedVersion });
    if (cached) return cached;
  }

  const [followingIds, blockedIds, interestProfile, creatorAffinityRows] = await Promise.all([
    Follow.find({ follower: userId }).distinct("following"),
    getBlockedIds(userId),
    UserInterestProfile.findOne({ user: userId }),
    CreatorAffinity.find({ user: userId }).sort({ score: -1 }).limit(20),
  ]);

  const followingSet = new Set(followingIds.map(String));
  const affinityCreatorIds = creatorAffinityRows.map((row) => row.creator);

  const [candidateRows, visitRows] = await Promise.all([
    Follow.aggregate([
      { $match: { following: { $in: followingIds } } },
      { $group: { _id: "$follower", mutualCount: { $sum: 1 } } },
      { $sort: { mutualCount: -1 } },
      { $limit: 300 },
    ]),
    RecommendationEvent.aggregate([
      {
        $match: {
          user: new mongoose.Types.ObjectId(userId),
          targetProfile: { $ne: null },
          eventType: { $in: ["profile_visit", "profile_open", "follow"] },
        },
      },
      { $group: { _id: "$targetProfile", count: { $sum: 1 } } },
      { $sort: { count: -1 } },
      { $limit: 100 },
    ]),
  ]);

  const candidateIds = new Set();
  candidateRows.forEach((row) => candidateIds.add(String(row._id)));
  visitRows.forEach((row) => candidateIds.add(String(row._id)));
  affinityCreatorIds.forEach((id) => candidateIds.add(String(id)));

  candidateIds.delete(String(userId));
  blockedIds.forEach((id) => candidateIds.delete(String(id)));
  followingIds.forEach((id) => candidateIds.delete(String(id)));

  const candidates = await User.find({ _id: { $in: [...candidateIds] }, isSuspended: false })
    .select("username fullName avatar bio followersCount followingCount")
    .limit(250);

  let creatorQualityRows = await CreatorQuality.find({ creator: { $in: candidates.map((item) => item._id) } });
  if (!creatorQualityRows.length) {
    await computeCreatorQuality(candidates.map((item) => item._id));
    creatorQualityRows = await CreatorQuality.find({ creator: { $in: candidates.map((item) => item._id) } });
  }
  const creatorQualityMap = new Map(creatorQualityRows.map((row) => [String(row.creator), row]));

  const [candidateInterestProfiles, candidateAudience] = await Promise.all([
    UserInterestProfile.find({ user: { $in: candidates.map((item) => item._id) } }),
    Follow.find({ following: { $in: candidates.map((item) => item._id) } }).select("follower following"),
  ]);

  const candidateInterestMap = new Map(candidateInterestProfiles.map((row) => [String(row.user), row]));
  const audienceMap = new Map();
  candidateAudience.forEach((row) => {
    const key = String(row.following);
    if (!audienceMap.has(key)) audienceMap.set(key, []);
    audienceMap.get(key).push(String(row.follower));
  });

  const visitMap = new Map(visitRows.map((row) => [String(row._id), row.count]));
  const mutualMap = new Map(candidateRows.map((row) => [String(row._id), row.mutualCount]));
  const affinitySet = new Set(affinityCreatorIds.map(String));

  const ranked = candidates.map((candidate) => {
    const candidateId = String(candidate._id);
    const candidateInterest = candidateInterestMap.get(candidateId);
    const quality = creatorQualityMap.get(candidateId);
    const audienceOverlap = arrayIntersectionCount(audienceMap.get(candidateId) || [], followingIds);

    const features = {
      mutualFollowScore: clamp((mutualMap.get(candidateId) || 0) / 12),
      interestSimilarityScore: computeTopicMatch(
        interestProfile?.interestScores,
        interestProfile?.negativeTopicScores,
        candidateInterest?.interestScores
      ),
      audienceOverlapScore: clamp(audienceOverlap / 15),
      visitIntentScore: clamp((visitMap.get(candidateId) || 0) / 6),
      creatorSimilarityScore: affinitySet.has(candidateId) ? 0.8 : 0.2,
      localityScore: 0.25,
      popularityScore: clamp(Math.log1p(candidate.followersCount || 0) / Math.log(100000)),
      qualityScore: quality?.qualityScore || 0.7,
    };

    const finalScore = computeProfileSuggestionScore(features);

    return {
      candidate,
      finalScore,
      reasons: [
        features.mutualFollowScore > 0.2 ? "mutual_follows" : null,
        features.interestSimilarityScore > 0.4 ? "interest_similarity" : null,
        features.visitIntentScore > 0.2 ? "profile_visit_intent" : null,
        features.creatorSimilarityScore > 0.5 ? "creator_similarity" : null,
      ].filter(Boolean),
      features,
    };
  });

  ranked.sort((left, right) => right.finalScore - left.finalScore);
  const start = (page - 1) * limit;
  const selected = ranked.slice(start, start + limit);
  const followStatuses = await getFollowStatusesForUsers(userId, selected.map((item) => item.candidate._id));

  await Promise.all(
    selected.map((item) =>
      ProfileSuggestionFeature.findOneAndUpdate(
        { user: userId, candidateProfile: item.candidate._id },
        {
          user: userId,
          candidateProfile: item.candidate._id,
          ...item.features,
          finalScore: item.finalScore,
          snapshotAt: new Date(),
        },
        { upsert: true, new: true }
      )
    )
  );

  const response = {
    items: selected.map((item) => ({
      ...item.candidate.toObject(),
      followStatus: followStatuses.get(String(item.candidate._id)) || "not_following",
      recommendation: {
        score: item.finalScore,
        reasons: item.reasons,
      },
    })),
    meta: {
      page,
      limit,
      total: ranked.length,
      totalPages: Math.max(Math.ceil(ranked.length / limit), 1),
      strategy: "people-you-may-follow-v1",
    },
  };

  await cacheRankedResult({ surface, userId, page, limit, payload: response, ttlSeconds: 180 });
  return response;
};

module.exports = { getProfileSuggestions };
