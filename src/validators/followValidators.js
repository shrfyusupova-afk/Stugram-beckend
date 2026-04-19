const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("./commonValidators");

const targetUserSchema = {
  params: z.object({
    userId: objectIdSchema,
  }),
};

const followRequestDecisionSchema = {
  params: z.object({
    requestId: objectIdSchema,
  }),
};

const followListSchema = {
  params: z.object({
    username: z.string().trim().min(3).max(30),
  }),
  query: paginationQuerySchema,
};

module.exports = { targetUserSchema, followRequestDecisionSchema, followListSchema };
