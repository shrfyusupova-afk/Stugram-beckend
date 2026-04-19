const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("./commonValidators");

const callHistoryQuerySchema = {
  query: paginationQuerySchema,
};

const callIdParamSchema = {
  params: z.object({
    callId: objectIdSchema,
  }),
};

module.exports = {
  callHistoryQuerySchema,
  callIdParamSchema,
};
