const { z } = require("zod");
const { objectIdSchema } = require("./commonValidators");

const identitySchema = z
  .string()
  .trim()
  .refine((value) => /(^\+998\d{9}$)|(^[^\s@]+@[^\s@]+\.[^\s@]+$)/.test(value), "Invalid identity");

const sendOtpSchema = {
  body: z.object({
    identity: identitySchema,
    purpose: z.enum(["register", "login"]).default("register"),
  }),
};

const verifyOtpSchema = {
  body: z.object({
    identity: identitySchema,
    otp: z.string().length(6),
    purpose: z.enum(["register", "login"]).default("register"),
  }),
};

const registerSchema = {
  body: z.object({
    identity: identitySchema,
    otp: z.string().length(6),
    fullName: z.string().trim().min(2).max(100),
    username: z.string().trim().min(3).max(30).regex(/^[a-zA-Z0-9._]+$/),
    password: z.string().min(8).max(64),
    type: z.enum(["student", "blogger"]).optional(),
    region: z.string().trim().max(100).optional(),
    district: z.string().trim().max(100).optional(),
    school: z.string().trim().max(150).optional(),
    birthday: z.coerce.date().optional(),
    grade: z.string().trim().max(50).optional(),
    group: z.string().trim().max(50).optional(),
    bio: z.string().max(160).optional(),
    isPrivateAccount: z.boolean().optional(),
  }),
};

const loginSchema = {
  body: z.object({
    identityOrUsername: z.string().trim().min(3),
    password: z.string().min(8).max(64),
  }),
};

const refreshTokenSchema = {
  body: z.object({
    refreshToken: z.string().min(10),
  }),
};

const logoutSchema = refreshTokenSchema;

const googleLoginSchema = {
  body: z.object({
    idToken: z.string().min(20),
  }),
};

const forgotPasswordSchema = {
  body: z.object({
    identity: identitySchema,
  }),
};

const resetPasswordSchema = {
  body: z.object({
    token: z.string().min(20),
    password: z.string().min(8).max(64),
  }),
};

const changePasswordSchema = {
  body: z.object({
    currentPassword: z.string().min(8).max(64),
    newPassword: z.string().min(8).max(64),
  }),
};

const sessionIdParamSchema = {
  params: z.object({
    sessionId: z.string().min(8).max(128),
  }),
};

const switchProfileSchema = {
  body: z.object({
    profileId: objectIdSchema,
  }),
};

module.exports = {
  sendOtpSchema,
  verifyOtpSchema,
  registerSchema,
  loginSchema,
  refreshTokenSchema,
  logoutSchema,
  googleLoginSchema,
  forgotPasswordSchema,
  resetPasswordSchema,
  changePasswordSchema,
  sessionIdParamSchema,
  switchProfileSchema,
};
