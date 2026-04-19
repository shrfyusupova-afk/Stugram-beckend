const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("./commonValidators");

const createPostSchema = {
  body: z.object({
    caption: z.string().max(2200).optional(),
    hashtags: z.array(z.string().trim().min(1).max(50)).optional(),
    location: z.string().trim().max(150).optional(),
  }),
};

const updatePostSchema = {
  params: z.object({
    postId: objectIdSchema,
  }),
  body: z.object({
    caption: z.string().max(2200).optional(),
    hashtags: z.array(z.string().trim().min(1).max(50)).optional(),
    location: z.string().trim().max(150).optional(),
  }),
};

const postIdParamSchema = {
  params: z.object({
    postId: objectIdSchema,
  }),
};

const userPostsSchema = {
  params: z.object({
    username: z.string().trim().min(3).max(30),
  }),
  query: paginationQuerySchema,
};

const feedQuerySchema = {
  query: paginationQuerySchema,
};

const savedPostsQuerySchema = {
  query: paginationQuerySchema,
};

module.exports = {
  createPostSchema,
  updatePostSchema,
  postIdParamSchema,
  userPostsSchema,
  feedQuerySchema,
  savedPostsQuerySchema,
};
