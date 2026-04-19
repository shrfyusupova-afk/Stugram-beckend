const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("./commonValidators");

const baseSearchQuery = paginationQuerySchema.extend({
  q: z.string().trim().min(1),
});

const searchQuerySchema = {
  query: baseSearchQuery,
};

const advancedUsersSearchQuery = paginationQuerySchema.extend({
  region: z.string().trim().min(1).max(100).optional(),
  district: z.string().trim().min(1).max(100).optional(),
  school: z.string().trim().min(1).max(150).optional(),
  grade: z.string().trim().min(1).max(50).optional(),
  group: z.string().trim().min(1).max(50).optional(),
  location: z.string().trim().min(1).max(120).optional(),
  username: z.string().trim().min(1).max(30).optional(),
  fullName: z.string().trim().min(1).max(100).optional(),
});

const advancedUserSearchQuerySchema = {
  query: advancedUsersSearchQuery,
};

const searchHistoryCreateSchema = {
  body: z.object({
    queryText: z.string().trim().min(1).max(120),
    searchType: z.enum(["user", "keyword", "hashtag"]),
    targetId: objectIdSchema.optional(),
  }),
};

const searchHistoryListSchema = {
  query: paginationQuerySchema,
};

const searchSuggestionsQuerySchema = {
  query: z.object({
    q: z.string().trim().min(1).max(60),
  }),
};

const searchHistoryIdParamSchema = {
  params: z.object({
    historyId: objectIdSchema,
  }),
};

module.exports = {
  searchQuerySchema,
  advancedUserSearchQuerySchema,
  searchHistoryCreateSchema,
  searchHistoryListSchema,
  searchHistoryIdParamSchema,
  searchSuggestionsQuerySchema,
};
