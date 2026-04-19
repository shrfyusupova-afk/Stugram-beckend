const DAY_IN_MS = 24 * 60 * 60 * 1000;

const FEED_WEIGHTS = {
  watchTime: 0.22,
  strongEngagement: 0.23,
  topicMatch: 0.18,
  creatorAffinity: 0.14,
  freshness: 0.08,
  popularity: 0.07,
  quality: 0.06,
  diversity: 0.04,
  exploration: 0.03,
  negativePenalty: 0.28,
};

const PROFILE_WEIGHTS = {
  mutualFollows: 0.24,
  interestSimilarity: 0.2,
  audienceOverlap: 0.14,
  visitIntent: 0.12,
  creatorSimilarity: 0.1,
  locality: 0.06,
  popularity: 0.08,
  quality: 0.06,
};

const clamp = (value, min = 0, max = 1) => Math.min(Math.max(value, min), max);

const mapToObject = (input) => {
  if (!input) return {};
  if (input instanceof Map) return Object.fromEntries(input.entries());
  return input;
};

const computeTopicMatch = (interestScores, negativeTopicScores, topicWeights) => {
  const positive = mapToObject(interestScores);
  const negative = mapToObject(negativeTopicScores);
  const contentTopics = mapToObject(topicWeights);

  const topics = Object.keys(contentTopics);
  if (!topics.length) return 0;

  let score = 0;
  let totalWeight = 0;
  for (const topic of topics) {
    const weight = Number(contentTopics[topic] || 0);
    totalWeight += weight;
    const topicPositive = Number(positive[topic] || 0);
    const topicNegative = Number(negative[topic] || 0);
    score += weight * Math.max(topicPositive - topicNegative, -3);
  }

  if (!totalWeight) return 0;
  return clamp((score / totalWeight + 4) / 8);
};

const computeFreshnessScore = (createdAt, contentType) => {
  const ageMs = Math.max(Date.now() - new Date(createdAt).getTime(), 0);
  const halfLifeDays = contentType === "reel" ? 3 : 5;
  return clamp(Math.exp((-Math.log(2) * ageMs) / (halfLifeDays * DAY_IN_MS)));
};

const computeWatchTimeScore = (watchMetric = {}) => {
  const ratio = Number(watchMetric.maxWatchRatio || 0);
  const completions = Number(watchMetric.completions || 0);
  const rewatches = Number(watchMetric.rewatches || 0);
  const starts = Number(watchMetric.watchStarts || 0);
  const fastSkips = Number(watchMetric.fastSkips || 0);

  const base = ratio * 0.7 + clamp(completions / Math.max(starts, 1)) * 0.2 + clamp(rewatches / 3) * 0.1;
  return clamp(base - clamp(fastSkips / Math.max(starts, 1)) * 0.5);
};

const computeEngagementScore = (candidateSignals = {}) => {
  const strongPositive =
    Number(candidateSignals.liked || 0) * 0.25 +
    Number(candidateSignals.saved || 0) * 0.22 +
    Number(candidateSignals.shared || 0) * 0.18 +
    Number(candidateSignals.commented || 0) * 0.15 +
    Number(candidateSignals.followAfterView || 0) * 0.12 +
    Number(candidateSignals.profileVisitAfterView || 0) * 0.08;

  return clamp(strongPositive);
};

const computeNegativePenalty = (candidateSignals = {}) =>
  clamp(
    Number(candidateSignals.fastSkipped || 0) * 0.35 +
      Number(candidateSignals.hidden || 0) * 0.4 +
      Number(candidateSignals.notInterested || 0) * 0.5 +
      Number(candidateSignals.reported || 0) * 0.7 +
      Number(candidateSignals.blocked || 0) * 1
  );

const computeDiversityBoost = ({ seenCreators = new Set(), creatorId, seenTopics = new Set(), topics = [] }) => {
  const repeatedCreatorPenalty = seenCreators.has(String(creatorId)) ? 0.2 : 0;
  const newTopicBoost = topics.some((topic) => !seenTopics.has(topic)) ? 0.2 : 0;
  return clamp(0.5 + newTopicBoost - repeatedCreatorPenalty);
};

const computeFeedScore = ({
  interestProfile,
  contentFeature,
  creatorAffinity,
  creatorQuality,
  watchMetric,
  candidateSignals,
  post,
  diversityContext,
  explorationBoost = 0,
}) => {
  const watchTimeScore = computeWatchTimeScore(watchMetric);
  const strongEngagementScore = computeEngagementScore(candidateSignals);
  const topicMatchScore = computeTopicMatch(
    interestProfile?.interestScores,
    interestProfile?.negativeTopicScores,
    contentFeature?.topicWeights
  );
  const creatorAffinityScore = clamp((Number(creatorAffinity?.score || 0) + 2) / 6);
  const freshnessScore = computeFreshnessScore(post.createdAt, contentFeature?.contentType);
  const popularityScore = clamp(Number(contentFeature?.popularityScore || 0));
  const qualityScore = clamp(Number(creatorQuality?.qualityScore || contentFeature?.qualityScore || 0.7));
  const diversityScore = computeDiversityBoost({
    seenCreators: diversityContext.seenCreators,
    creatorId: post.author,
    seenTopics: diversityContext.seenTopics,
    topics: contentFeature?.topics || [],
  });
  const negativePenalty = computeNegativePenalty(candidateSignals);

  const rawScore =
    watchTimeScore * FEED_WEIGHTS.watchTime +
    strongEngagementScore * FEED_WEIGHTS.strongEngagement +
    topicMatchScore * FEED_WEIGHTS.topicMatch +
    creatorAffinityScore * FEED_WEIGHTS.creatorAffinity +
    freshnessScore * FEED_WEIGHTS.freshness +
    popularityScore * FEED_WEIGHTS.popularity +
    qualityScore * FEED_WEIGHTS.quality +
    diversityScore * FEED_WEIGHTS.diversity +
    explorationBoost * FEED_WEIGHTS.exploration -
    negativePenalty * FEED_WEIGHTS.negativePenalty;

  return {
    score: Number(rawScore.toFixed(6)),
    components: {
      watchTimeScore,
      strongEngagementScore,
      topicMatchScore,
      creatorAffinityScore,
      freshnessScore,
      popularityScore,
      qualityScore,
      diversityScore,
      explorationBoost,
      negativePenalty,
    },
  };
};

const computeProfileSuggestionScore = (features) => {
  const finalScore =
    clamp(features.mutualFollowScore) * PROFILE_WEIGHTS.mutualFollows +
    clamp(features.interestSimilarityScore) * PROFILE_WEIGHTS.interestSimilarity +
    clamp(features.audienceOverlapScore) * PROFILE_WEIGHTS.audienceOverlap +
    clamp(features.visitIntentScore) * PROFILE_WEIGHTS.visitIntent +
    clamp(features.creatorSimilarityScore) * PROFILE_WEIGHTS.creatorSimilarity +
    clamp(features.localityScore) * PROFILE_WEIGHTS.locality +
    clamp(features.popularityScore) * PROFILE_WEIGHTS.popularity +
    clamp(features.qualityScore) * PROFILE_WEIGHTS.quality;

  return Number(finalScore.toFixed(6));
};

module.exports = {
  FEED_WEIGHTS,
  PROFILE_WEIGHTS,
  clamp,
  computeFeedScore,
  computeProfileSuggestionScore,
  computeTopicMatch,
};
