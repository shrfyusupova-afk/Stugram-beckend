const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("./commonValidators");

const requestIdParamSchema = {
  params: z.object({
    requestId: objectIdSchema,
  }),
};

const listRequestsSchema = {
  query: paginationQuerySchema,
};

module.exports = {
  requestIdParamSchema,
  listRequestsSchema,
};
