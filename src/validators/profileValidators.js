const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("./commonValidators");

const usernameParamSchema = {
  params: z.object({
    username: z.string().trim().min(3).max(30),
  }),
};

const usernameAvailabilityQuerySchema = {
  query: z.object({
    username: z.string().trim().min(3).max(30).regex(/^[a-zA-Z0-9._]+$/),
  }),
};

const updateProfileSchema = {
  body: z.object({
    fullName: z.string().trim().min(2).max(100).optional(),
    username: z.string().trim().min(3).max(30).regex(/^[a-zA-Z0-9._]+$/).optional(),
    bio: z.string().max(160).optional(),
    birthday: z.coerce.date().optional(),
    location: z.string().trim().max(120).optional(),
    school: z.string().trim().max(150).optional(),
    region: z.string().trim().max(100).optional(),
    district: z.string().trim().max(100).optional(),
    grade: z.string().trim().max(50).optional(),
    group: z.string().trim().max(50).optional(),
    isPrivateAccount: z.boolean().optional(),
  }),
};

const userIdParamSchema = {
  params: z.object({
    userId: objectIdSchema,
  }),
};

const profileFeedSchema = {
  params: usernameParamSchema.params,
  query: paginationQuerySchema,
};

const createProfileSchema = {
  body: z.object({
    username: z.string().trim().min(3).max(30).regex(/^[a-zA-Z0-9._]+$/),
    firstName: z.string().trim().min(1).max(60),
    lastName: z.string().trim().min(1).max(60),
    type: z.enum(["student", "blogger"]),
    region: z.string().trim().max(100).optional(),
    district: z.string().trim().max(100).optional(),
    school: z.string().trim().max(150).optional(),
    password: z.string().min(8).max(64),
  }),
};

module.exports = {
  usernameParamSchema,
  usernameAvailabilityQuerySchema,
  updateProfileSchema,
  userIdParamSchema,
  profileFeedSchema,
  createProfileSchema,
};
