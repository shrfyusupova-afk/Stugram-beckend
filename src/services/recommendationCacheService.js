const { redis } = require("../config/redis");
const { env } = require("../config/env");
const logger = require("../utils/logger");
const { enqueueRecommendationRefreshJobs } = require("./recommendationRefreshService");

const STATE_VERSION_TTL_SECONDS = 7 * 24 * 60 * 60;
const PAGE_FRESH_TTL_SECONDS = 45;
const PAGE_STALE_TTL_SECONDS = 3 * 60;
const CANDIDATE_FRESH_TTL_SECONDS = 3 * 60;
const CANDIDATE_STALE_TTL_SECONDS = 10 * 60;
const REFRESH_HINT_TTL_SECONDS = 60;
const REFRESH_LOCK_TTL_SECONDS = 20;

const STRONG_SIGNAL_EVENTS = new Set(["like", "follow", "save", "share", "hide", "not_interested"]);

const buildStateVersionKey = (userId) => `rec:user:${userId}:state_version`;
const buildRankedPageKey = ({ surface, userId, page, limit, version }) =>
  `rec:user:${userId}:surface:${surface}:page:${page}:limit:${limit}:v:${version}`;
const buildCandidatePoolKey = ({ surface, userId, version }) => `rec:user:${userId}:surface:${surface}:candidates:v:${version}`;
const buildRefreshLockKey = ({ surface, userId }) => `rec:user:${userId}:surface:${surface}:stale_refresh_lock`;
const buildRefreshHintKey = ({ surface, userId }) => `rec:user:${userId}:surface:${surface}:warm_hint`;
const isOptionalRedisMode = () => !env.redisRequired;

const withOptionalRedisFallback = async ({ operationName, fallback, executor }) => {
  try {
    return await executor();
  } catch (error) {
    if (!isOptionalRedisMode()) {
      throw error;
    }

    logger.warn("Redis unavailable, bypassing recommendation cache", {
      operationName,
      message: error.message,
    });

    return typeof fallback === "function" ? fallback(error) : fallback;
  }
};

const buildCacheEnvelope = ({ payload, version, cacheType, freshTtlSeconds, staleTtlSeconds }) => {
  const now = Date.now();

  return {
    payload,
    cache: {
      cacheType,
      version,
      generatedAt: new Date(now).toISOString(),
      freshUntil: new Date(now + freshTtlSeconds * 1000).toISOString(),
      staleUntil: new Date(now + staleTtlSeconds * 1000).toISOString(),
    },
  };
};

const parseCacheEnvelope = (rawValue) => {
  if (!rawValue) return null;

  const parsed = JSON.parse(rawValue);
  const freshUntil = parsed?.cache?.freshUntil ? new Date(parsed.cache.freshUntil).getTime() : 0;
  const staleUntil = parsed?.cache?.staleUntil ? new Date(parsed.cache.staleUntil).getTime() : 0;
  const now = Date.now();

  if (!staleUntil || staleUntil <= now) {
    return { status: "expired", payload: parsed?.payload || null, cache: parsed?.cache || null };
  }

  return {
    status: freshUntil > now ? "fresh" : "stale",
    payload: parsed.payload,
    cache: parsed.cache,
  };
};

const ensureUserRecommendationStateVersion = async (userId) => {
  const key = buildStateVersionKey(userId);
  const existing = await withOptionalRedisFallback({
    operationName: "ensureUserRecommendationStateVersion:get",
    fallback: null,
    executor: () => redis.get(key),
  });

  if (existing) return Number(existing);

  await withOptionalRedisFallback({
    operationName: "ensureUserRecommendationStateVersion:set",
    fallback: null,
    executor: () => redis.set(key, 1, "EX", STATE_VERSION_TTL_SECONDS),
  });
  return 1;
};

const bumpUserRecommendationStateVersion = async ({ userId, reason = "strong_signal" }) => {
  const key = buildStateVersionKey(userId);
  const version = await withOptionalRedisFallback({
    operationName: "bumpUserRecommendationStateVersion:incr",
    fallback: 1,
    executor: () => redis.incr(key),
  });
  await withOptionalRedisFallback({
    operationName: "bumpUserRecommendationStateVersion:expire",
    fallback: null,
    executor: () => redis.expire(key, STATE_VERSION_TTL_SECONDS),
  });

  logger.info("Recommendation state version bumped", { userId, version, reason });
  return version;
};

const shouldBumpRecommendationStateVersion = (eventType) => STRONG_SIGNAL_EVENTS.has(eventType);

const setRankedPageCache = async ({ surface, userId, page, limit, version, payload }) => {
  const key = buildRankedPageKey({ surface, userId, page, limit, version });
  const envelope = buildCacheEnvelope({
    payload,
    version,
    cacheType: "ranked_page",
    freshTtlSeconds: PAGE_FRESH_TTL_SECONDS,
    staleTtlSeconds: PAGE_STALE_TTL_SECONDS,
  });

  await withOptionalRedisFallback({
    operationName: "setRankedPageCache",
    fallback: null,
    executor: () => redis.set(key, JSON.stringify(envelope), "EX", PAGE_STALE_TTL_SECONDS),
  });
  return key;
};

const getRankedPageCache = async ({ surface, userId, page, limit, version }) => {
  const key = buildRankedPageKey({ surface, userId, page, limit, version });
  const raw = await withOptionalRedisFallback({
    operationName: "getRankedPageCache",
    fallback: null,
    executor: () => redis.get(key),
  });
  const parsed = parseCacheEnvelope(raw);

  return parsed ? { ...parsed, key } : null;
};

const setCandidatePoolCache = async ({ surface, userId, version, payload }) => {
  const key = buildCandidatePoolKey({ surface, userId, version });
  const envelope = buildCacheEnvelope({
    payload,
    version,
    cacheType: "candidate_pool",
    freshTtlSeconds: CANDIDATE_FRESH_TTL_SECONDS,
    staleTtlSeconds: CANDIDATE_STALE_TTL_SECONDS,
  });

  await withOptionalRedisFallback({
    operationName: "setCandidatePoolCache",
    fallback: null,
    executor: () => redis.set(key, JSON.stringify(envelope), "EX", CANDIDATE_STALE_TTL_SECONDS),
  });
  return key;
};

const getCandidatePoolCache = async ({ surface, userId, version }) => {
  const key = buildCandidatePoolKey({ surface, userId, version });
  const raw = await withOptionalRedisFallback({
    operationName: "getCandidatePoolCache",
    fallback: null,
    executor: () => redis.get(key),
  });
  const parsed = parseCacheEnvelope(raw);

  return parsed ? { ...parsed, key } : null;
};

const cacheRankedResult = async ({ surface, userId, page, limit, payload }) => {
  const version = await ensureUserRecommendationStateVersion(userId);
  return setRankedPageCache({ surface, userId, page, limit, version, payload });
};

const getCachedRankedResult = async ({ surface, userId, page, limit, version = null }) => {
  const effectiveVersion = version || (await ensureUserRecommendationStateVersion(userId));
  const cached = await getRankedPageCache({ surface, userId, page, limit, version: effectiveVersion });
  return cached?.status === "expired" ? null : cached?.payload || null;
};

const enqueueAsyncRecommendationRefresh = async ({ userId, surfaces = [], reason = "stale_read", version = null }) => {
  const uniqueSurfaces = [...new Set(surfaces.filter(Boolean))];
  if (!uniqueSurfaces.length) return [];

  const queued = [];

  for (const surface of uniqueSurfaces) {
    const lockKey = buildRefreshLockKey({ surface, userId });
    const hintKey = buildRefreshHintKey({ surface, userId });
    const acquired = await withOptionalRedisFallback({
      operationName: "enqueueAsyncRecommendationRefresh:lock",
      fallback: null,
      executor: () => redis.set(lockKey, "1", "EX", REFRESH_LOCK_TTL_SECONDS, "NX"),
    });

    if (!acquired) continue;

    await withOptionalRedisFallback({
      operationName: "enqueueAsyncRecommendationRefresh:hint",
      fallback: null,
      executor: () =>
        redis.set(
          hintKey,
          JSON.stringify({
            userId: String(userId),
            surface,
            reason,
            version,
            requestedAt: new Date().toISOString(),
          }),
          "EX",
          REFRESH_HINT_TTL_SECONDS
        ),
    });

    queued.push(surface);
  }

  if (!queued.length) return [];

  const jobs = await enqueueRecommendationRefreshJobs({
    userId,
    surfaces: queued,
    reason,
    version,
  });

  logger.info("Recommendation async refresh queued", {
    userId,
    surfaces: queued,
    reason,
    version,
    jobs,
  });

  return jobs;
};

module.exports = {
  STRONG_SIGNAL_EVENTS,
  buildCandidatePoolKey,
  buildRankedPageKey,
  buildRefreshHintKey,
  buildStateVersionKey,
  bumpUserRecommendationStateVersion,
  cacheRankedResult,
  enqueueAsyncRecommendationRefresh,
  ensureUserRecommendationStateVersion,
  getCachedRankedResult,
  getCandidatePoolCache,
  getRankedPageCache,
  setCandidatePoolCache,
  setRankedPageCache,
  shouldBumpRecommendationStateVersion,
};
