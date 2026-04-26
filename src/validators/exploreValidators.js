const { z } = require("zod");

const { paginationQuerySchema } = require("./commonValidators");

const trendingExploreQuerySchema = {
  query: z.object({
    limit: z.coerce.number().min(1).max(80).optional(),
  }),
};

const creatorsDiscoveryQuerySchema = {
  query: paginationQuerySchema,
};

module.exports = {
  trendingExploreQuerySchema,
  creatorsDiscoveryQuerySchema,
};
