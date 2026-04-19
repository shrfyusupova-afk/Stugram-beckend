const { env } = require("../../src/config/env");

env.redisRequired = true;

const replayAuditRecords = [];

const matchesFilter = (record, filter = {}) =>
  Object.entries(filter).every(([key, value]) => {
    if (value && typeof value === "object" && !Array.isArray(value)) {
      return JSON.stringify(record[key]) === JSON.stringify(value);
    }

    return String(record[key]) === String(value);
  });

jest.mock("../../src/models/RecommendationReplayAudit", () => ({
  create: jest.fn(async (payload) => {
    const record = {
      _id: `audit-${replayAuditRecords.length + 1}`,
      createdAt: new Date(),
      ...payload,
    };
    replayAuditRecords.push(record);
    return record;
  }),
  findOne: jest.fn((filter = {}) => ({
    lean: async () => replayAuditRecords.find((record) => matchesFilter(record, filter)) || null,
  })),
  find: jest.fn(() => ({
    sort: () => ({
      limit: () => ({
        populate: async () => [],
      }),
      skip: () => ({
        limit: () => ({
          populate: async () => [],
        }),
      }),
      populate: async () => [],
    }),
  })),
  countDocuments: jest.fn(async (filter = {}) => replayAuditRecords.filter((record) => matchesFilter(record, filter)).length),
  __reset: () => {
    replayAuditRecords.length = 0;
  },
}));

const { redis } = require("../../src/config/redis");
const RecommendationReplayAudit = require("../../src/models/RecommendationReplayAudit");
const {
  getDeadLetters,
  getRefreshMetricsSnapshot,
  recordDeadLetter,
} = require("../../src/services/recommendationRefreshMetricsService");
const {
  enqueueRecommendationRefreshJobs,
  getRecommendationRefreshQueueHealth,
  replayDeadLetterById,
  replayDeadLetters,
} = require("../../src/services/recommendationRefreshService");
const { recommendationRefreshQueue } = require("../../src/queues/recommendationRefreshQueue");

describe("Recommendation refresh queue integration", () => {
  beforeEach(() => {
    recommendationRefreshQueue.add.mockImplementation(async (name, data, options) => ({
      id: options?.jobId || `${name}:job`,
      name,
      data,
      opts: options || {},
    }));
    recommendationRefreshQueue.getJobCounts.mockResolvedValue({
      waiting: 0,
      active: 0,
      completed: 0,
      failed: 0,
      delayed: 0,
    });
    recommendationRefreshQueue.getJobs.mockResolvedValue([]);
  });

  afterEach(async () => {
    await redis.flushall();
    RecommendationReplayAudit.__reset();
    jest.clearAllMocks();
    env.redisRequired = true;
  });

  it("enqueues recommendation refresh jobs once per surface within the dedupe window and records metrics", async () => {
    const userId = "user-queue-1";

    const firstPass = await enqueueRecommendationRefreshJobs({
      userId,
      surfaces: ["feed", "feed", "reels"],
      reason: "strong_signal",
      version: "v1",
    });

    const secondPass = await enqueueRecommendationRefreshJobs({
      userId,
      surfaces: ["feed", "reels"],
      reason: "strong_signal",
      version: "v1",
    });

    expect(firstPass).toEqual([
      { surface: "feed", jobId: `refresh_feed:${userId}` },
      { surface: "reels", jobId: `refresh_reels:${userId}` },
    ]);
    expect(secondPass).toEqual([]);
    expect(recommendationRefreshQueue.add).toHaveBeenCalledTimes(2);

    const metrics = await getRefreshMetricsSnapshot();
    expect(metrics.global.queuedCount).toBe(2);
    expect(metrics.perJobType.refresh_feed.queuedCount).toBe(1);
    expect(metrics.perJobType.refresh_reels.queuedCount).toBe(1);
  });

  it("replays a dead-letter job, creates replay audit rows, and rejects unknown dead letters safely", async () => {
    const actorId = "actor-queue-1";
    const targetUserId = "target-queue-1";

    await recordDeadLetter({
      job: {
        id: "dead-job-1",
        name: "refresh_feed",
        data: {
          userId: targetUserId,
          surface: "feed",
          version: "v2",
          reason: "test_failure",
        },
        attemptsMade: 4,
        opts: { attempts: 4 },
        stacktrace: ["Error: refresh failed"],
      },
      error: new Error("refresh failed"),
      latencyMs: 321,
    });

    const [deadLetter] = await getDeadLetters(1);
    const replayResult = await replayDeadLetterById({
      deadLetterId: deadLetter.id,
      actorId,
    });

    expect(replayResult.replayed).toBe(true);
    expect(replayResult.jobs).toHaveLength(1);
    expect(recommendationRefreshQueue.add).toHaveBeenCalledWith(
      "refresh_feed",
      expect.objectContaining({
        userId: targetUserId,
        surface: "feed",
        reason: "dead_letter_replay",
        version: "v2",
      }),
      expect.objectContaining({
        jobId: `refresh_feed:${targetUserId}`,
      })
    );

    const singleAudit = await RecommendationReplayAudit.findOne({
      actor: actorId,
      replayType: "single",
    }).lean();

    expect(singleAudit).toBeTruthy();
    expect(singleAudit.status).toBe("success");
    expect(singleAudit.deadLetterIds).toContain(deadLetter.id);

    await expect(
      replayDeadLetterById({
        deadLetterId: "missing-dead-letter",
        actorId,
      })
    ).rejects.toMatchObject({
      statusCode: 404,
    });
  });

  it("supports bulk replay and records a bulk replay audit", async () => {
    const actorId = "actor-queue-2";

    await recordDeadLetter({
      job: {
        id: "dead-job-feed",
        name: "refresh_feed",
        data: {
          userId: "feed-target",
          surface: "feed",
          version: "v1",
          reason: "failed_feed",
        },
        attemptsMade: 4,
        opts: { attempts: 4 },
        stacktrace: [],
      },
      error: new Error("feed fail"),
      latencyMs: 100,
    });

    await recordDeadLetter({
      job: {
        id: "dead-job-reels",
        name: "refresh_reels",
        data: {
          userId: "reels-target",
          surface: "reels",
          version: "v1",
          reason: "failed_reels",
        },
        attemptsMade: 4,
        opts: { attempts: 4 },
        stacktrace: [],
      },
      error: new Error("reels fail"),
      latencyMs: 150,
    });

    const replayResult = await replayDeadLetters({
      actorId,
      limit: 10,
    });

    expect(replayResult.replayed).toHaveLength(2);
    expect(replayResult.skipped).toHaveLength(0);

    const bulkAudit = await RecommendationReplayAudit.findOne({
      actor: actorId,
      replayType: "bulk",
    }).lean();

    expect(bulkAudit).toBeTruthy();
    expect(bulkAudit.status).toBe("success");
    expect(bulkAudit.replayedCount).toBe(2);
    expect(bulkAudit.skippedCount).toBe(0);
  });

  it("returns a disabled queue summary safely when Redis-backed refresh is unavailable", async () => {
    env.redisRequired = false;

    const health = await getRecommendationRefreshQueueHealth();
    const jobs = await enqueueRecommendationRefreshJobs({
      userId: "disabled-user",
      surfaces: ["feed"],
      reason: "strong_signal",
    });

    expect(health.queue.enabled).toBe(false);
    expect(health.queue.counts.waiting).toBe(0);
    expect(jobs).toEqual([]);
    expect(recommendationRefreshQueue.add).not.toHaveBeenCalled();
  });
});
