const createInMemoryRedis = () => {
  const store = new Map();
  const expiry = new Map();

  const isExpired = (key) => {
    const expiresAt = expiry.get(key);
    if (expiresAt && expiresAt <= Date.now()) {
      store.delete(key);
      expiry.delete(key);
      return true;
    }
    return false;
  };

  const ensureAlive = (key) => {
    isExpired(key);
    return store.get(key);
  };

  const ensureHash = (key) => {
    const current = ensureAlive(key);
    if (current && typeof current === "object" && !Array.isArray(current)) {
      return current;
    }

    const hash = {};
    store.set(key, hash);
    return hash;
  };

  return {
    on: jest.fn(),
    quit: jest.fn().mockResolvedValue(undefined),
    duplicate() {
      return this;
    },
    async incr(key) {
      const current = Number(ensureAlive(key) || 0) + 1;
      store.set(key, String(current));
      return current;
    },
    async pexpire(key, ttlMs) {
      expiry.set(key, Date.now() + ttlMs);
      return 1;
    },
    async pttl(key) {
      if (isExpired(key)) return -2;
      const expiresAt = expiry.get(key);
      if (!expiresAt) return -1;
      return Math.max(expiresAt - Date.now(), 0);
    },
    async set(key, value, ...args) {
      let ttlMs = null;
      let onlyIfAbsent = false;

      for (let index = 0; index < args.length; index += 1) {
        const arg = args[index];
        if (arg === "PX") {
          ttlMs = Number(args[index + 1] || 0);
          index += 1;
        } else if (arg === "EX") {
          ttlMs = Number(args[index + 1] || 0) * 1000;
          index += 1;
        } else if (arg === "NX") {
          onlyIfAbsent = true;
        }
      }

      if (onlyIfAbsent && ensureAlive(key) !== undefined) {
        return null;
      }

      store.set(key, String(value));
      if (ttlMs) {
        expiry.set(key, Date.now() + ttlMs);
      } else {
        expiry.delete(key);
      }
      return "OK";
    },
    async get(key) {
      return ensureAlive(key) || null;
    },
    async exists(key) {
      return ensureAlive(key) ? 1 : 0;
    },
    async del(...keys) {
      let deleted = 0;
      keys.flat().forEach((key) => {
        if (store.delete(key)) deleted += 1;
        expiry.delete(key);
      });
      return deleted;
    },
    async lpush(key, ...values) {
      const current = ensureAlive(key);
      const list = Array.isArray(current) ? current : [];
      list.unshift(...values.map(String));
      store.set(key, list);
      return list.length;
    },
    async lrange(key, start, end) {
      const current = ensureAlive(key);
      const list = Array.isArray(current) ? current : [];
      const normalizedEnd = end < 0 ? list.length + end + 1 : end + 1;
      return list.slice(start, normalizedEnd);
    },
    async ltrim(key, start, end) {
      const current = ensureAlive(key);
      const list = Array.isArray(current) ? current : [];
      const normalizedEnd = end < 0 ? list.length + end + 1 : end + 1;
      store.set(key, list.slice(start, normalizedEnd));
      return "OK";
    },
    async hset(key, fieldOrValues, maybeValue) {
      const hash = ensureHash(key);

      if (fieldOrValues && typeof fieldOrValues === "object" && !Array.isArray(fieldOrValues)) {
        Object.assign(hash, fieldOrValues);
      } else if (fieldOrValues !== undefined) {
        hash[fieldOrValues] = String(maybeValue);
      }

      store.set(key, hash);
      return 1;
    },
    async hgetall(key) {
      const current = ensureAlive(key);
      return current && typeof current === "object" && !Array.isArray(current) ? current : {};
    },
    async hincrby(key, field, by = 1) {
      const hash = ensureHash(key);
      const nextValue = Number(hash[field] || 0) + Number(by || 0);
      hash[field] = String(nextValue);
      store.set(key, hash);
      return nextValue;
    },
    async hincrbyfloat(key, field, by = 0) {
      const hash = ensureHash(key);
      const nextValue = Number(hash[field] || 0) + Number(by || 0);
      hash[field] = String(nextValue);
      store.set(key, hash);
      return nextValue;
    },
    async zadd() {
      return 1;
    },
    async zrevrange() {
      return [];
    },
    pipeline() {
      const operations = [];
      const redisClient = this;
      const pipelineApi = {
        lpush: (...args) => {
          operations.push(() => redisClient.lpush(...args));
          return pipelineApi;
        },
        ltrim: (...args) => {
          operations.push(() => redisClient.ltrim(...args));
          return pipelineApi;
        },
        hincrby: (...args) => {
          operations.push(() => redisClient.hincrby(...args));
          return pipelineApi;
        },
        hset: (...args) => {
          operations.push(() => redisClient.hset(...args));
          return pipelineApi;
        },
        exec: async () => Promise.all(operations.map((run) => run().then((result) => [null, result]))),
      };

      return pipelineApi;
    },
    async flushall() {
      store.clear();
      expiry.clear();
      return "OK";
    },
  };
};

jest.mock("google-auth-library", () => ({
  OAuth2Client: class OAuth2Client {
    async verifyIdToken({ idToken }) {
      if (!String(idToken).startsWith("valid_google_token")) {
        throw new Error("Invalid Google token");
      }

      return {
        getPayload: () => ({
          sub: "google-user-1",
          email: "google@example.com",
          email_verified: true,
          name: "Google User",
          picture: "https://example.test/google-user.jpg",
        }),
      };
    }
  },
}));

jest.mock("../../src/config/redis", () => ({
  redis: createInMemoryRedis(),
}));

jest.mock("../../src/queues/recommendationRefreshQueue", () => ({
  RECOMMENDATION_REFRESH_QUEUE_NAME: "recommendation-refresh",
  recommendationQueueEnabled: true,
  recommendationRefreshConnection: {
    on: jest.fn(),
    quit: jest.fn().mockResolvedValue(undefined),
    duplicate: jest.fn(() => ({
      on: jest.fn(),
      quit: jest.fn().mockResolvedValue(undefined),
    })),
  },
  recommendationRefreshQueue: {
    add: jest.fn().mockResolvedValue({ id: "job_1" }),
    getJobCounts: jest.fn().mockResolvedValue({
      waiting: 0,
      active: 0,
      completed: 0,
      failed: 0,
      delayed: 0,
    }),
    getJobs: jest.fn().mockResolvedValue([]),
    close: jest.fn().mockResolvedValue(undefined),
  },
  recommendationRefreshQueueEvents: {
    on: jest.fn(),
    close: jest.fn().mockResolvedValue(undefined),
  },
  closeRecommendationQueueResources: jest.fn().mockResolvedValue(undefined),
}));

jest.mock("../../src/utils/media", () => {
  let uploadCounter = 0;

  return {
    uploadBufferToCloudinary: jest.fn(async (_buffer, folder, resourceType = "image") => {
      uploadCounter += 1;
      const normalizedResourceType =
        resourceType === "raw" ? "raw" : resourceType === "video" ? "video" : "image";
      return {
        url: `https://example.test/${folder}/asset-${uploadCounter}`,
        publicId: `${folder}/asset-${uploadCounter}`,
        resourceType: normalizedResourceType,
        width: 1080,
        height: 1920,
        duration: resourceType === "video" ? 15 : null,
      };
    }),
    destroyCloudinaryAsset: jest.fn(async () => ({ result: "ok" })),
    validateUploadedMedia: jest.fn(async ({ uploaded, expectedType }) => {
      let resolvedType = uploaded.resourceType === "video" ? "video" : "image";

      if (expectedType === "voice") resolvedType = "voice";
      else if (expectedType === "round_video") resolvedType = "round_video";
      else if (expectedType === "file" || uploaded.resourceType === "raw") resolvedType = "file";

      if (expectedType && expectedType !== resolvedType) {
        throw new Error("Uploaded file type does not match messageType");
      }
      return resolvedType;
    }),
  };
});

jest.mock("../../src/services/pushNotificationService", () => ({
  sendPushToUser: jest.fn().mockResolvedValue({
    attempted: 1,
    successCount: 1,
    failureCount: 0,
    invalidatedTokens: 0,
  }),
  sendPushToTokens: jest.fn().mockResolvedValue({
    attempted: 1,
    successCount: 1,
    failureCount: 0,
    invalidatedTokens: 0,
  }),
  buildNotificationPushPayload: jest.fn().mockResolvedValue({
    attempted: 1,
    successCount: 1,
    failureCount: 0,
    invalidatedTokens: 0,
  }),
}));

afterEach(() => {
  jest.clearAllMocks();
});
