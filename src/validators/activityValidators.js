const { paginationQuerySchema } = require("./commonValidators");

const activityQuerySchema = {
  query: paginationQuerySchema,
};

module.exports = {
  activityQuerySchema,
};
