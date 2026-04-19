const { z } = require("zod");

const registerPushTokenSchema = {
  body: z.object({
    token: z.string().trim().min(16).max(4096).optional(),
    pushToken: z.string().trim().min(16).max(4096).optional(),
    platform: z.enum(["android", "ios", "web"]),
    deviceId: z.string().trim().min(1).max(255),
    appVersion: z.string().trim().min(1).max(50).optional(),
  }).refine((value) => Boolean(value.token || value.pushToken), {
    message: "token or pushToken is required",
    path: ["token"],
  }),
};

const deletePushTokenSchema = {
  body: z
    .object({
      token: z.string().trim().min(16).max(4096).optional(),
      pushToken: z.string().trim().min(16).max(4096).optional(),
      deviceId: z.string().trim().min(1).max(255).optional(),
    })
    .refine((value) => Boolean(value.token || value.pushToken || value.deviceId), {
      message: "token, pushToken or deviceId is required",
      path: ["token"],
    }),
};

module.exports = {
  registerPushTokenSchema,
  deletePushTokenSchema,
};
