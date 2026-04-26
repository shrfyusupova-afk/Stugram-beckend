const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("./commonValidators");

const normalizeHashtagInput = (value) => {
  if (value === undefined) return undefined;
  if (Array.isArray(value)) return value;
  if (typeof value !== "string") return value;

  const trimmed = value.trim();
  if (!trimmed) return [];

  try {
    const parsed = JSON.parse(trimmed);
    if (Array.isArray(parsed)) return parsed;
  } catch (_error) {
    // Plain multipart fields are expected here; JSON is only a compatibility path.
  }

  return trimmed
    .split(/[\s,]+/)
    .map((tag) => tag.trim())
    .filter(Boolean);
};

const hashtagsSchema = z.preprocess(
  normalizeHashtagInput,
  z.array(z.string().trim().min(1).max(50)).optional()
);

const createPostSchema = {
  body: z.object({
    caption: z.string().max(2200).optional(),
    hashtags: hashtagsSchema,
    location: z.string().trim().max(150).optional(),
  }),
};

const updatePostSchema = {
  params: z.object({
    postId: objectIdSchema,
  }),
  body: z.object({
    caption: z.string().max(2200).optional(),
    hashtags: hashtagsSchema,
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
