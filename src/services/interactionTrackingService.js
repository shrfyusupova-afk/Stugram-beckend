const ApiError = require("../utils/ApiError");
const Post = require("../models/Post");
const RecommendationEvent = require("../models/RecommendationEvent");
const ContentWatchMetric = require("../models/ContentWatchMetric");
const UserContentPreference = require("../models/UserContentPreference");
const UserInterestProfile = require("../models/UserInterestProfile");
const CreatorAffinity = require("../models/CreatorAffinity");
const { createAuditLog } = require("./auditLogService");
const {
  bumpUserRecommendationStateVersion,
  enqueueAsyncRecommendationRefresh,
  shouldBumpRecommendationStateVersion,
} = require("./recommendationCacheService");
const { ensureContentFeature, getOrCreateInterestProfile } = require("./recommendationService");

const EVENT_WEIGHTS = {
  like: 1.6,
  save: 2,
  share: 2.2,
  comment: 1.8,
  follow: 2.4,
  profile_visit: 1.2,
  watch_complete: 1.5,
  rewatch: 1.7,
  watch_progress: 0.8,
  caption_open: 0.4,
  profile_open: 0.6,
  sound_on: 0.3,
  hide: -2.2,
  not_interested: -3,
  report: -3.5,
  block: -4,
  fast_skip: -1.4,
};

const STRONG_SIGNAL_SURFACES = {
  like: ["feed", "reels"],
  follow: ["feed", "reels", "profiles"],
  save: ["feed", "reels"],
  share: ["feed", "reels"],
  hide: ["feed", "reels"],
  not_interested: ["feed", "reels"],
};

const toMapObject = (value) => {
  if (!value) return {};
  if (value instanceof Map) return Object.fromEntries(value.entries());
  return value;
};

const applyTopicDelta = async ({ userId, topics = [], delta, isNegative = false, lastEventAt = new Date() }) => {
  const profile = await getOrCreateInterestProfile(userId);
  const scoreMap = { ...(isNegative ? toMapObject(profile.negativeTopicScores) : toMapObject(profile.interestScores)) };
  const normalizedDelta = isNegative ? Math.abs(delta) : delta;

  topics.forEach((topic, index) => {
    const multiplier = Math.max(1 - index * 0.1, 0.6);
    scoreMap[topic] = Number(((scoreMap[topic] || 0) + normalizedDelta * multiplier).toFixed(4));
  });

  const confidenceScore = Math.min((profile.confidenceScore || 0.1) + 0.02, 1);
  const update = {
    lastEventAt,
    confidenceScore,
  };
  if (isNegative) {
    update.negativeTopicScores = scoreMap;
  } else {
    update.interestScores = scoreMap;
  }

  await UserInterestProfile.findOneAndUpdate({ user: userId }, update, { upsert: true });
};

const applyCreatorAffinityDelta = async ({ userId, creatorId, eventType, delta, lastInteractionAt = new Date() }) => {
  if (!creatorId) return;

  const fieldMap = {
    watch_complete: "watchScore",
    watch_progress: "watchScore",
    rewatch: "watchScore",
    like: "engagementScore",
    save: "engagementScore",
    share: "engagementScore",
    comment: "engagementScore",
    profile_visit: "visitScore",
    profile_open: "visitScore",
    follow: "followScore",
    hide: "negativeScore",
    not_interested: "negativeScore",
    report: "negativeScore",
    block: "negativeScore",
    fast_skip: "negativeScore",
  };

  const field = fieldMap[eventType];
  const fieldDelta = field === "negativeScore" ? Math.abs(delta) : delta;
  const update = {
    $setOnInsert: { user: userId, creator: creatorId },
    $set: { lastInteractionAt },
    $inc: {
      score: delta,
    },
  };

  if (field) {
    update.$inc[field] = fieldDelta;
  }

  await CreatorAffinity.findOneAndUpdate({ user: userId, creator: creatorId }, update, { upsert: true, new: true });
};

const recordEvent = async (userId, payload) => {
  const post = payload.contentId ? await Post.findById(payload.contentId).populate("author", "_id") : null;
  if (payload.contentId && !post) throw new ApiError(404, "Content not found");

  const contentFeature = post ? await ensureContentFeature(post) : null;
  const topics = payload.topics?.length ? payload.topics : contentFeature?.topics || [];
  const creatorId = payload.creatorId || post?.author?._id || null;
  const eventType = payload.eventType;
  const delta = EVENT_WEIGHTS[eventType] || 0;
  const isNegative = delta < 0;

  const event = await RecommendationEvent.create({
    user: userId,
    eventType,
    surface: payload.surface || "system",
    content: payload.contentId || null,
    creator: creatorId,
    targetProfile: payload.targetProfileId || null,
    sessionId: payload.sessionId || null,
    requestId: payload.requestId || null,
    sourceImpressionId: payload.sourceImpressionId || null,
    topics,
    watchMetrics: payload.watchMetrics || undefined,
    metadata: payload.metadata || null,
  });

  if (topics.length && delta !== 0) {
    await applyTopicDelta({ userId, topics, delta, isNegative, lastEventAt: event.createdAt });
  }
  if (creatorId && delta !== 0) {
    await applyCreatorAffinityDelta({ userId, creatorId, eventType, delta, lastInteractionAt: event.createdAt });
  }

  await createAuditLog({
    actor: userId,
    action: `recommendation.${eventType}`,
    category: "abuse",
    status: "success",
    targetUser: creatorId || payload.targetProfileId || null,
    details: {
      contentId: payload.contentId || null,
      surface: payload.surface || "system",
      sourceImpressionId: payload.sourceImpressionId || null,
    },
  });

  if (shouldBumpRecommendationStateVersion(eventType)) {
    const version = await bumpUserRecommendationStateVersion({ userId, reason: eventType });
    await enqueueAsyncRecommendationRefresh({
      userId,
      surfaces: STRONG_SIGNAL_SURFACES[eventType] || [payload.surface || "feed"],
      reason: eventType,
      version,
    });
  }

  return event;
};

const trackImpression = async (userId, payload) => {
  const post = await Post.findById(payload.contentId).populate("author", "_id");
  if (!post) throw new ApiError(404, "Content not found");

  await ContentWatchMetric.findOneAndUpdate(
    { user: userId, post: payload.contentId },
    {
      $setOnInsert: {
        user: userId,
        post: payload.contentId,
        creator: post.author._id,
      },
      $inc: {
        impressions: 1,
      },
    },
    { upsert: true, new: true }
  );

  const eventType = payload.surface === "reels" ? "reel_impression" : "feed_impression";
  return recordEvent(userId, { ...payload, eventType, creatorId: post.author._id });
};

const trackWatchProgress = async (userId, payload) => {
  const post = await Post.findById(payload.contentId).populate("author", "_id");
  if (!post) throw new ApiError(404, "Content not found");

  const totalDurationMs = Math.max(Number(payload.totalDurationMs || post.media?.[0]?.duration * 1000 || 0), 1);
  const watchedMs = Math.max(Number(payload.watchedMs || 0), 0);
  const watchRatio = Math.min(watchedMs / totalDurationMs, 1);
  const fastSkip = watchRatio < 0.15;
  const completed = watchRatio >= 0.95;
  const rewatchCount = Number(payload.rewatchCount || 0);

  await ContentWatchMetric.findOneAndUpdate(
    { user: userId, post: payload.contentId },
    {
      $setOnInsert: {
        user: userId,
        post: payload.contentId,
        creator: post.author._id,
      },
      $inc: {
        watchStarts: payload.started ? 1 : 0,
        totalWatchMs: watchedMs,
        completions: completed ? 1 : 0,
        rewatches: rewatchCount > 0 ? rewatchCount : 0,
        fastSkips: fastSkip ? 1 : 0,
      },
      $max: {
        maxWatchRatio: watchRatio,
      },
      $set: {
        lastWatchedAt: new Date(),
      },
    },
    { upsert: true, new: true }
  );

  const eventType = completed ? "watch_complete" : fastSkip ? "fast_skip" : "watch_progress";
  await recordEvent(userId, {
    ...payload,
    eventType,
    creatorId: post.author._id,
    watchMetrics: {
      durationMs: totalDurationMs,
      progressMs: watchedMs,
      watchRatio,
      rewatchCount,
      dwellMs: Number(payload.dwellMs || watchedMs),
      completed,
      soundOn: Boolean(payload.soundOn),
    },
  });

  if (rewatchCount > 0) {
    await recordEvent(userId, {
      ...payload,
      eventType: "rewatch",
      creatorId: post.author._id,
      watchMetrics: {
        durationMs: totalDurationMs,
        progressMs: watchedMs,
        watchRatio,
        rewatchCount,
        dwellMs: Number(payload.dwellMs || watchedMs),
        completed,
        soundOn: Boolean(payload.soundOn),
      },
    });
  }

  return {
    tracked: true,
    watchRatio: Number(watchRatio.toFixed(4)),
    completed,
    fastSkip,
  };
};

const markNotInterested = async (userId, payload) => {
  const post = await Post.findById(payload.contentId).populate("author", "_id");
  if (!post) throw new ApiError(404, "Content not found");

  await UserContentPreference.findOneAndUpdate(
    { user: userId, post: payload.contentId, preferenceType: "not_interested" },
    {
      user: userId,
      post: payload.contentId,
      creator: post.author._id,
      preferenceType: "not_interested",
      source: payload.surface || "feed",
      metadata: { reason: payload.reason || "manual_feedback" },
    },
    { upsert: true, new: true }
  );

  await recordEvent(userId, {
    ...payload,
    eventType: "not_interested",
    creatorId: post.author._id,
  });

  return { hidden: true, contentId: payload.contentId };
};

const seedOnboardingTopics = async (userId, topics = []) => {
  const normalized = [...new Set(topics.map((topic) => String(topic).trim().toLowerCase()).filter(Boolean))].slice(0, 12);
  await UserInterestProfile.findOneAndUpdate(
    { user: userId },
    {
      $setOnInsert: { user: userId },
      $set: {
        onboardingTopics: normalized,
        confidenceScore: normalized.length ? 0.2 : 0.1,
      },
    },
    { upsert: true, new: true }
  );

  const version = await bumpUserRecommendationStateVersion({ userId, reason: "onboarding_topics" });
  await enqueueAsyncRecommendationRefresh({
    userId,
    surfaces: ["feed", "reels", "profiles"],
    reason: "onboarding_topics",
    version,
  });

  return normalized;
};

module.exports = {
  recordEvent,
  trackImpression,
  trackWatchProgress,
  markNotInterested,
  seedOnboardingTopics,
};
