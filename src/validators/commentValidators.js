const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("./commonValidators");

const addCommentSchema = {
  params: z.object({
    postId: objectIdSchema,
  }),
  body: z.object({
    content: z.string().trim().min(1).max(500),
    parentCommentId: objectIdSchema.optional(),
  }),
};

const commentIdParamSchema = {
  params: z.object({
    commentId: objectIdSchema,
  }),
};

const commentsByPostSchema = {
  params: z.object({
    postId: objectIdSchema,
  }),
  query: paginationQuerySchema,
};

module.exports = { addCommentSchema, commentIdParamSchema, commentsByPostSchema };
