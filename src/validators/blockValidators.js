const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("./commonValidators");

const blockedAccountsListSchema = {
  query: paginationQuerySchema,
};

const blockedUserParamSchema = {
  params: z.object({
    userId: objectIdSchema,
  }),
};

module.exports = {
  blockedAccountsListSchema,
  blockedUserParamSchema,
};
