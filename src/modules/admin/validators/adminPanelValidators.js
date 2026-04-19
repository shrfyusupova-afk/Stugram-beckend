const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("../../../validators/commonValidators");

const adminUsersQuerySchema = {
  query: paginationQuerySchema.extend({
    search: z.string().trim().min(1).max(60).optional(),
    status: z.enum(["active", "banned", "admin"]).optional(),
  }),
};

const adminUserIdParamSchema = {
  params: z.object({
    id: objectIdSchema,
  }),
  body: z
    .object({
      reason: z.string().trim().min(3).max(500).optional(),
    })
    .optional(),
};

const adminPostsQuerySchema = {
  query: paginationQuerySchema.extend({
    search: z.string().trim().min(1).max(120).optional(),
    visibility: z.enum(["hidden", "visible"]).optional(),
  }),
};

const adminPostIdParamSchema = {
  params: z.object({
    id: objectIdSchema,
  }),
};

const adminHidePostSchema = {
  params: z.object({
    id: objectIdSchema,
  }),
  body: z.object({
    reason: z.string().trim().min(3).max(300).optional(),
  }),
};

const adminReportsQuerySchema = {
  query: paginationQuerySchema.extend({
    status: z.enum(["open", "resolved"]).optional(),
    targetType: z.enum(["post", "comment", "user"]).optional(),
  }),
};

const adminReportResolveSchema = {
  params: z.object({
    id: objectIdSchema,
  }),
  body: z.object({
    resolutionNote: z.string().trim().max(1000).optional(),
  }),
};

const adminAuditLogsQuerySchema = {
  query: paginationQuerySchema.extend({
    search: z.string().trim().min(1).max(80).optional(),
    category: z.enum(["auth", "chat", "security", "abuse", "call", "support"]).optional(),
    status: z.enum(["success", "failure", "warning"]).optional(),
  }),
};

module.exports = {
  adminHidePostSchema,
  adminPostIdParamSchema,
  adminPostsQuerySchema,
  adminAuditLogsQuerySchema,
  adminReportResolveSchema,
  adminReportsQuerySchema,
  adminUserIdParamSchema,
  adminUsersQuerySchema,
};
