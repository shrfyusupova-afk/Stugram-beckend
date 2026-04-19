const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("./commonValidators");

const listReportsSchema = {
  query: paginationQuerySchema.extend({
    status: z.enum(["open", "reviewing", "resolved", "rejected"]).optional(),
  }),
};

const reportIdParamSchema = {
  params: z.object({
    reportId: objectIdSchema,
  }),
};

const reviewReportSchema = {
  params: z.object({
    reportId: objectIdSchema,
  }),
  body: z.object({
    status: z.enum(["reviewing", "resolved", "rejected"]),
    reviewNotes: z.string().trim().max(1000).optional(),
    actionTaken: z.enum(["none", "warned", "suspended", "banned"]).optional(),
  }),
};

const moderateUserSchema = {
  params: z.object({
    userId: objectIdSchema,
  }),
  body: z.object({
    reason: z.string().trim().min(3).max(500),
    suspendedUntil: z.string().datetime().optional(),
  }),
};

const replayDeadLetterSchema = {
  params: z.object({
    deadLetterId: z.string().uuid(),
  }),
};

const replayDeadLettersSchema = {
  body: z
    .object({
      surface: z.enum(["feed", "reels", "profiles"]).optional(),
      jobName: z.enum(["refresh_feed", "refresh_reels", "refresh_profiles"]).optional(),
      limit: z.coerce.number().int().min(1).max(50).optional(),
    })
    .refine((value) => value.surface || value.jobName, {
      message: "Either surface or jobName is required",
      path: ["surface"],
    }),
};

const replayAuditHistorySchema = {
  query: paginationQuerySchema
    .extend({
      status: z.enum(["success", "partial", "failed", "skipped"]).optional(),
      replayType: z.enum(["single", "bulk"]).optional(),
      actor: objectIdSchema.optional(),
      surface: z.enum(["feed", "reels", "profiles"]).optional(),
      fromDate: z.string().datetime().optional(),
      toDate: z.string().datetime().optional(),
      deadLetterId: z.string().uuid().optional(),
      jobId: z.string().trim().min(1).max(120).regex(/^[a-zA-Z0-9:_-]+$/, "Invalid jobId").optional(),
    })
    .refine((value) => {
      if (!value.fromDate || !value.toDate) return true;
      return new Date(value.fromDate).getTime() <= new Date(value.toDate).getTime();
    }, {
      message: "fromDate must be before or equal to toDate",
      path: ["fromDate"],
    }),
};

const testPushSchema = {
  body: z.object({
    userId: objectIdSchema,
    title: z.string().trim().min(1).max(120),
    body: z.string().trim().min(1).max(500),
    data: z.record(z.string().trim().max(255)).optional(),
  }),
};

module.exports = {
  listReportsSchema,
  reportIdParamSchema,
  reviewReportSchema,
  moderateUserSchema,
  replayDeadLetterSchema,
  replayDeadLettersSchema,
  replayAuditHistorySchema,
  testPushSchema,
};
