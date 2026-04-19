const { z } = require("zod");

const { objectIdSchema } = require("../../../validators/commonValidators");

const createReportSchema = {
  body: z.object({
    targetType: z.enum(["post", "comment", "user"]),
    targetId: objectIdSchema,
    reason: z.enum(["spam", "harassment", "nudity", "violence", "scam", "hate", "misinformation", "other"]),
    details: z.string().trim().max(1000).optional(),
  }),
};

module.exports = {
  createReportSchema,
};
