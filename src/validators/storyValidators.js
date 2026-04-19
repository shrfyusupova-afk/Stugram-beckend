const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("./commonValidators");

const createStorySchema = {
  body: z.object({
    caption: z.string().max(300).optional(),
  }),
};

const storyIdParamSchema = {
  params: z.object({
    storyId: objectIdSchema,
  }),
};

const storyViewersSchema = {
  params: z.object({
    storyId: objectIdSchema,
  }),
  query: paginationQuerySchema,
};

const storyCommentsSchema = {
  params: z.object({
    storyId: objectIdSchema,
  }),
  query: paginationQuerySchema,
};

const storyInsightsSchema = {
  params: z.object({
    storyId: objectIdSchema,
  }),
};

const storyCommentCreateSchema = {
  params: z.object({
    storyId: objectIdSchema,
  }),
  body: z.object({
    content: z.string().trim().min(1).max(1000),
  }),
};

const storyReplySchema = {
  params: z.object({
    storyId: objectIdSchema,
  }),
  body: z.object({
    text: z.string().trim().min(1).max(1000),
  }),
};

const storyCommentDeleteSchema = {
  params: z.object({
    storyId: objectIdSchema,
    commentId: objectIdSchema,
  }),
};

const userStoriesSchema = {
  params: z.object({
    username: z.string().trim().min(3).max(30),
  }),
  query: paginationQuerySchema,
};

const storiesFeedSchema = {
  query: paginationQuerySchema,
};

module.exports = {
  createStorySchema,
  storyIdParamSchema,
  storyViewersSchema,
  storyInsightsSchema,
  storyCommentsSchema,
  storyCommentCreateSchema,
  storyCommentDeleteSchema,
  storyReplySchema,
  userStoriesSchema,
  storiesFeedSchema,
};
