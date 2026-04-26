const { createDistributedRateLimiter, keyGenerator } = require("../../src/middlewares/rateLimiter");

jest.mock("../../src/services/redisSecurityService", () => ({
  consumeRateLimit: jest.fn(),
}));
jest.mock("../../src/utils/logger", () => ({
  info: jest.fn(),
  warn: jest.fn(),
  error: jest.fn(),
}));
jest.mock("../../src/services/chatMetricsService", () => ({
  incrementCounter: jest.fn(),
}));

const { consumeRateLimit } = require("../../src/services/redisSecurityService");
const logger = require("../../src/utils/logger");
const { incrementCounter } = require("../../src/services/chatMetricsService");

const buildResponse = () => {
  const headers = {};
  return {
    headers,
    statusCode: null,
    body: null,
    setHeader: jest.fn((key, value) => {
      headers[key] = value;
    }),
    status: jest.fn(function status(code) {
      this.statusCode = code;
      return this;
    }),
    json: jest.fn(function json(payload) {
      this.body = payload;
      return this;
    }),
  };
};

describe("rate limiter middleware", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("keys bearer-authenticated requests by token fingerprint instead of proxy IP", () => {
    const first = keyGenerator({
      headers: { authorization: "Bearer user-token-a" },
      ip: "203.0.113.10",
    });
    const second = keyGenerator({
      headers: { authorization: "Bearer user-token-b" },
      ip: "203.0.113.10",
    });
    const anonymous = keyGenerator({
      headers: {},
      ip: "203.0.113.10",
    });

    expect(first).toMatch(/^token:/);
    expect(second).toMatch(/^token:/);
    expect(first).not.toEqual(second);
    expect(anonymous).toBe("ip:203.0.113.10");
  });

  it("uses the higher authenticated limit and allows normal authenticated traffic", async () => {
    consumeRateLimit.mockResolvedValue({
      allowed: true,
      remaining: 999,
      retryAfterMs: 0,
    });

    const limiter = createDistributedRateLimiter({
      keyPrefix: "api",
      windowMs: 15 * 60 * 1000,
      limit: (req) => (req.headers.authorization?.startsWith("Bearer ") ? 1000 : 100),
      message: "Too many requests. Please try again later.",
    });
    const req = {
      headers: { authorization: "Bearer real-user-token" },
      ip: "203.0.113.10",
    };
    const res = buildResponse();
    const next = jest.fn();

    await limiter(req, res, next);

    expect(consumeRateLimit).toHaveBeenCalledWith(
      expect.objectContaining({
        key: expect.stringMatching(/^api:token:/),
        limit: 1000,
        windowMs: 15 * 60 * 1000,
      })
    );
    expect(res.setHeader).toHaveBeenCalledWith("X-RateLimit-Limit", "1000");
    expect(next).toHaveBeenCalledTimes(1);
    expect(res.status).not.toHaveBeenCalled();
  });

  it("returns the production JSON 429 response when the unauthenticated bucket is exceeded", async () => {
    consumeRateLimit.mockResolvedValue({
      allowed: false,
      remaining: 0,
      retryAfterMs: 30_000,
    });

    const limiter = createDistributedRateLimiter({
      keyPrefix: "api",
      windowMs: 15 * 60 * 1000,
      limit: (req) => (req.headers.authorization?.startsWith("Bearer ") ? 1000 : 100),
      message: "Too many requests. Please try again later.",
    });
    const req = {
      headers: {},
      ip: "198.51.100.20",
    };
    const res = buildResponse();
    const next = jest.fn();

    await limiter(req, res, next);

    expect(consumeRateLimit).toHaveBeenCalledWith(
      expect.objectContaining({
        key: "api:ip:198.51.100.20",
        limit: 100,
      })
    );
    expect(res.setHeader).toHaveBeenCalledWith("Retry-After", "30");
    expect(res.status).toHaveBeenCalledWith(429);
    expect(res.body).toEqual({
      success: false,
      message: "Too many requests. Please try again later.",
      data: null,
      meta: null,
    });
    expect(logger.warn).toHaveBeenCalledWith(
      "rate_limit_hit",
      expect.objectContaining({
        route: expect.any(String),
        authenticated: false,
        httpStatus: 429,
      })
    );
    expect(incrementCounter).toHaveBeenCalledWith(
      "chat_rate_limit_hit_total",
      expect.objectContaining({ authenticated: "false" })
    );
    expect(next).not.toHaveBeenCalled();
  });
});
