const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("./commonValidators");

const likePostSchema = {
  params: z.object({
    postId: objectIdSchema,
  }),
};

const likedPostsHistorySchema = {
  query: paginationQuerySchema,
};

module.exports = { likePostSchema, likedPostsHistorySchema };
