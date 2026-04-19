const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("./commonValidators");

const closeFriendUserIdParamSchema = {
  params: z.object({
    userId: objectIdSchema,
  }),
};

const closeFriendsListQuerySchema = {
  query: paginationQuerySchema,
};

module.exports = {
  closeFriendUserIdParamSchema,
  closeFriendsListQuerySchema,
};
