const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("./commonValidators");

const feedQuerySchema = {
  query: paginationQuerySchema,
};

const impressionSchema = {
  body: z.object({
    contentId: objectIdSchema,
    surface: z.enum(["feed", "reels"]),
    sessionId: z.string().trim().min(8).max(120).optional(),
    requestId: z.string().trim().min(8).max(120).optional(),
    sourceImpressionId: z.string().trim().min(8).max(120).optional(),
    topics: z.array(z.string().trim().min(1).max(50)).max(12).optional(),
    metadata: z.record(z.any()).optional(),
  }),
};

const watchProgressSchema = {
  body: z.object({
    contentId: objectIdSchema,
    surface: z.enum(["feed", "reels"]).default("reels"),
    watchedMs: z.coerce.number().min(0),
    totalDurationMs: z.coerce.number().min(1).optional(),
    dwellMs: z.coerce.number().min(0).optional(),
    rewatchCount: z.coerce.number().min(0).max(20).optional(),
    started: z.boolean().optional(),
    soundOn: z.boolean().optional(),
    sessionId: z.string().trim().min(8).max(120).optional(),
    requestId: z.string().trim().min(8).max(120).optional(),
    sourceImpressionId: z.string().trim().min(8).max(120).optional(),
    topics: z.array(z.string().trim().min(1).max(50)).max(12).optional(),
  }),
};

const notInterestedSchema = {
  body: z.object({
    contentId: objectIdSchema,
    surface: z.enum(["feed", "reels"]).default("feed"),
    reason: z.enum(["manual_feedback", "irrelevant", "too_repetitive", "spam", "sensitive"]).optional(),
    sessionId: z.string().trim().min(8).max(120).optional(),
    requestId: z.string().trim().min(8).max(120).optional(),
    sourceImpressionId: z.string().trim().min(8).max(120).optional(),
    topics: z.array(z.string().trim().min(1).max(50)).max(12).optional(),
  }),
};

const onboardingTopicsSchema = {
  body: z.object({
    topics: z.array(z.string().trim().min(1).max(50)).max(12),
  }),
};

module.exports = {
  feedQuerySchema,
  impressionSchema,
  watchProgressSchema,
  notInterestedSchema,
  onboardingTopicsSchema,
};
