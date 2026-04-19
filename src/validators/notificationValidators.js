const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("./commonValidators");

const notificationIdSchema = {
  params: z.object({
    notificationId: objectIdSchema,
  }),
};

const notificationQuerySchema = {
  query: paginationQuerySchema,
};

module.exports = { notificationIdSchema, notificationQuerySchema };
