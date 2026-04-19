const EventEmitter = require("events");

const loadWorkerHarness = ({
  queueEnabled = true,
  feedResultCount = 2,
  profileResultCount = 3,
  feedError = null,
  profileError = null,
} = {}) => {
  jest.resetModules();

  const workerInstances = [];
  const mocks = {
    logger: {
      info: jest.fn(),
      warn: jest.fn(),
      error: jest.fn(),
    },
    recommendationService: {
      getPersonalizedFeed: jest.fn(async () => {
        if (feedError) throw feedError;
        return { items: Array.from({ length: feedResultCount }, (_, index) => ({ id: index + 1 })) };
      }),
    },
    profileSuggestionService: {
      getProfileSuggestions: jest.fn(async () => {
        if (profileError) throw profileError;
        return { items: Array.from({ length: profileResultCount }, (_, index) => ({ id: index + 1 })) };
      }),
    },
    metrics: {
      recordCompletedMetric: jest.fn().mockResolvedValue(undefined),
      recordDeadLetter: jest.fn().mockResolvedValue(undefined),
      recordFailedMetric: jest.fn().mockResolvedValue(undefined),
      recordRetryMetric: jest.fn().mockResolvedValue(undefined),
      updateWorkerStatus: jest.fn().mockResolvedValue(undefined),
    },
    queueEvents: {
      on: jest.fn(),
    },
  };

  jest.doMock("bullmq", () => ({
    Worker: class FakeWorker extends EventEmitter {
      constructor(name, processor, options) {
        super();
        this.name = name;
        this.processor = processor;
        this.options = options;
        workerInstances.push(this);
      }
    },
  }));

  jest.doMock("../../src/utils/logger", () => mocks.logger);
  jest.doMock("../../src/services/recommendationService", () => mocks.recommendationService);
  jest.doMock("../../src/services/profileSuggestionService", () => mocks.profileSuggestionService);
  jest.doMock("../../src/services/recommendationRefreshMetricsService", () => mocks.metrics);
  jest.doMock("../../src/queues/recommendationRefreshQueue", () => ({
    RECOMMENDATION_REFRESH_QUEUE_NAME: "recommendation-refresh",
    recommendationQueueConfigured: queueEnabled,
    recommendationRefreshConnection: queueEnabled
      ? {
          duplicate: jest.fn(() => ({})),
        }
      : null,
    recommendationRefreshQueueEvents: queueEnabled ? mocks.queueEvents : null,
    recommendationRefreshQueue: queueEnabled ? { close: jest.fn() } : null,
    initRecommendationRefreshQueueResources: jest.fn(() => ({
      initialized: queueEnabled,
      ready: queueEnabled,
    })),
    getRecommendationRefreshConnection: jest.fn(() =>
      queueEnabled
        ? {
            duplicate: jest.fn(() => ({})),
          }
        : null
    ),
    getRecommendationRefreshQueueEvents: jest.fn(() => (queueEnabled ? mocks.queueEvents : null)),
    getRecommendationRefreshQueue: jest.fn(() => (queueEnabled ? { close: jest.fn() } : null)),
    isRecommendationQueueReady: jest.fn(() => queueEnabled),
  }));

  let workerModule;
  jest.isolateModules(() => {
    workerModule = require("../../src/workers/recommendationRefreshWorker");
  });

  return {
    createRecommendationRefreshWorker: workerModule.createRecommendationRefreshWorker,
    workerInstances,
    mocks,
  };
};

describe("Recommendation refresh worker", () => {
  afterEach(() => {
    jest.clearAllMocks();
    jest.resetModules();
    jest.unmock("bullmq");
  });

  it("processes feed refresh jobs and records completion metrics", async () => {
    const harness = loadWorkerHarness({ feedResultCount: 2 });
    const worker = harness.createRecommendationRefreshWorker();
    const instance = harness.workerInstances[0];

    const job = {
      id: "job-feed-1",
      name: "refresh_feed",
      data: {
        userId: "user-1",
        surface: "feed",
        page: 1,
        limit: 20,
      },
      attemptsMade: 0,
      opts: { attempts: 4 },
      updateData: jest.fn().mockResolvedValue(undefined),
      processedOn: 100,
      finishedOn: 145,
    };

    worker.emit("active", job);
    const result = await instance.processor(job);
    worker.emit("completed", job, result);

    expect(result).toEqual({
      userId: "user-1",
      surface: "feed",
      page: 1,
      limit: 20,
      items: 2,
    });
    expect(job.updateData).toHaveBeenCalled();
    expect(harness.mocks.recommendationService.getPersonalizedFeed).toHaveBeenCalledWith(
      "user-1",
      { page: 1, limit: 20 },
      { surface: "feed", bypassCache: true, expectedVersion: null }
    );
    expect(harness.mocks.metrics.recordCompletedMetric).toHaveBeenCalledWith({
      jobName: "refresh_feed",
      latencyMs: 45,
    });
  });

  it("records retry metrics for retryable worker failures", async () => {
    const error = new Error("temporary failure");
    const harness = loadWorkerHarness({ feedError: error });
    const worker = harness.createRecommendationRefreshWorker();
    const instance = harness.workerInstances[0];

    const job = {
      id: "job-feed-retry",
      name: "refresh_feed",
      data: {
        userId: "user-2",
        surface: "feed",
      },
      attemptsMade: 1,
      opts: { attempts: 4 },
      processedOn: 10,
      finishedOn: 40,
    };

    await expect(instance.processor(job)).rejects.toThrow("temporary failure");
    worker.emit("failed", job, error);

    expect(harness.mocks.metrics.recordFailedMetric).toHaveBeenCalledWith({
      jobName: "refresh_feed",
      latencyMs: 30,
      error,
      finalFailure: false,
    });
    expect(harness.mocks.metrics.recordRetryMetric).toHaveBeenCalledWith({
      jobName: "refresh_feed",
      attempt: 1,
      maxAttempts: 4,
      error,
    });
    expect(harness.mocks.metrics.recordDeadLetter).not.toHaveBeenCalled();
  });

  it("records dead-letter metrics for final worker failures", async () => {
    const error = new Error("permanent failure");
    const harness = loadWorkerHarness({ profileError: error });
    const worker = harness.createRecommendationRefreshWorker();
    const instance = harness.workerInstances[0];

    const job = {
      id: "job-profiles-final",
      name: "refresh_profiles",
      data: {
        userId: "user-3",
        surface: "profiles",
        page: 2,
        limit: 10,
      },
      attemptsMade: 4,
      opts: { attempts: 4 },
      processedOn: 20,
      finishedOn: 75,
    };

    await expect(instance.processor(job)).rejects.toThrow("permanent failure");
    worker.emit("failed", job, error);

    expect(harness.mocks.metrics.recordFailedMetric).toHaveBeenCalledWith({
      jobName: "refresh_profiles",
      latencyMs: 55,
      error,
      finalFailure: true,
    });
    expect(harness.mocks.metrics.recordDeadLetter).toHaveBeenCalledWith({
      job,
      error,
      latencyMs: 55,
    });
    expect(harness.mocks.metrics.recordRetryMetric).not.toHaveBeenCalled();
  });

  it("throws a clear error when queue support is unavailable", () => {
    const harness = loadWorkerHarness({ queueEnabled: false });

    expect(() => harness.createRecommendationRefreshWorker()).toThrow(
      "Recommendation refresh worker is disabled because Redis queue support is unavailable"
    );
  });
});
