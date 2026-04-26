const { z } = require("zod");

// Production configuration is intentionally strict: Render/host env vars must
// provide real providers/secrets, and mock OTP delivery is blocked in production.
const parseBooleanEnv = (value, fallback = undefined) => {
  if (value === undefined || value === null || value === "") {
    return fallback;
  }

  const normalized = String(value).trim().toLowerCase();
  if (["true", "1", "yes", "on"].includes(normalized)) return true;
  if (["false", "0", "no", "off"].includes(normalized)) return false;
  return fallback;
};

const envSchema = z.object({
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  PORT: z.coerce.number().default(5001),
  MONGODB_URI: z.string().min(1, "MONGODB_URI is required"),
  MONGO_URI: z.string().min(1).optional(),
  ENABLE_BOOTSTRAP_USER: z.coerce.boolean().optional(),
  BOOTSTRAP_USER_IDENTITY: z.string().optional(),
  BOOTSTRAP_USER_PASSWORD: z.string().optional(),
  BOOTSTRAP_USER_USERNAME: z.string().optional(),
  BOOTSTRAP_USER_FULL_NAME: z.string().optional(),
  BOOTSTRAP_USER_ROLE: z.enum(["user", "moderator", "admin"]).optional(),
  CLIENT_URL: z.string().default("*"),
  JWT_ACCESS_SECRET: z.string().min(16),
  JWT_REFRESH_SECRET: z.string().min(16),
  JWT_ACCESS_EXPIRES_IN: z.string().default("15m"),
  JWT_REFRESH_EXPIRES_IN: z.string().default("30d"),
  GOOGLE_CLIENT_ID: z.string().optional(),
  FIREBASE_PROJECT_ID: z.string().optional(),
  FIREBASE_CLIENT_EMAIL: z.string().optional(),
  FIREBASE_PRIVATE_KEY: z.string().optional(),
  FIREBASE_SERVICE_ACCOUNT_JSON: z.string().optional(),
  FIREBASE_SERVICE_ACCOUNT_PATH: z.string().optional(),
  OTP_SECRET: z.string().min(8),
  OTP_EXPIRES_MINUTES: z.coerce.number().default(10),
  OTP_PROVIDER: z.enum(["sms", "email", "mock"]).default("mock"),
  SMS_PROVIDER: z.string().default("twilio"),
  EMAIL_PROVIDER: z.string().default("resend"),
  OTP_RESEND_COOLDOWN_SECONDS: z.coerce.number().default(60),
  OTP_MAX_SENDS_PER_WINDOW: z.coerce.number().default(5),
  OTP_SEND_WINDOW_MINUTES: z.coerce.number().default(60),
  TWILIO_ACCOUNT_SID: z.string().optional(),
  TWILIO_AUTH_TOKEN: z.string().optional(),
  TWILIO_FROM_PHONE: z.string().optional(),
  RESEND_API_KEY: z.string().optional(),
  RESEND_FROM_EMAIL: z.string().email().optional(),
  BREVO_API_KEY: z.string().optional(),
  BREVO_FROM_EMAIL: z.string().email().optional(),
  BREVO_FROM_NAME: z.string().optional(),
  BREVO_SMS_SENDER: z.string().optional(),
  PASSWORD_RESET_BASE_URL: z.string().optional(),
  APP_DEEP_LINK_RESET_URL: z.string().optional(),
  REQUEST_SLOW_MS: z.coerce.number().default(800),
  MONGO_SLOW_QUERY_MS: z.coerce.number().default(250),
  MONGO_QUERY_DEBUG: z.coerce.boolean().default(false),
  ALLOW_MEMORY_DB_FALLBACK: z.coerce.boolean().default(false),
  MONGO_SERVER_SELECTION_TIMEOUT_MS: z.coerce.number().default(15000),
  MONGO_CONNECT_TIMEOUT_MS: z.coerce.number().default(15000),
  MONGO_SOCKET_TIMEOUT_MS: z.coerce.number().default(45000),
  MONGO_FORCE_IPV4: z.coerce.boolean().default(true),
  MONGO_STARTUP_DIAGNOSTICS: z.coerce.boolean().default(true),
  MONGO_DNS_SERVERS: z.string().optional(),
  RATE_LIMIT_WINDOW_MS: z.coerce.number().default(15 * 60 * 1000),
  RATE_LIMIT_MAX: z.coerce.number().default(500),
  AUTHENTICATED_RATE_LIMIT_MAX: z.coerce.number().default(5000),
  REDIS_URL: z.string().optional(),
  REDIS_HOST: z.string().default("127.0.0.1"),
  REDIS_PORT: z.coerce.number().default(6379),
  REDIS_PASSWORD: z.string().optional(),
  REDIS_TLS: z.coerce.boolean().default(false),
  REDIS_TLS_REJECT_UNAUTHORIZED: z.coerce.boolean().default(true),
  REDIS_PREFIX: z.string().default("stugram"),
  REDIS_REQUIRED: z.coerce.boolean().default(true),
  QUEUE_ENABLED: z.coerce.boolean().default(true),
  WORKER_REQUIRED: z.coerce.boolean().default(false),
  RECOMMENDATION_WORKER_ENABLED: z.coerce.boolean().default(true),
  RECOMMENDATION_MODE: z.enum(["weighted-cache", "db-direct"]).default("weighted-cache"),
  CACHE_MODE: z.enum(["enabled", "redis-optional", "disabled"]).default("enabled"),
  ENABLE_MAINTENANCE_CLEANUP_SCHEDULER: z.coerce.boolean().default(false),
  MAINTENANCE_CLEANUP_INTERVAL_MINUTES: z.coerce.number().default(60),
  EXPIRED_STORY_RETENTION_HOURS: z.coerce.number().default(6),
  INACTIVE_PUSH_TOKEN_RETENTION_DAYS: z.coerce.number().default(30),
  REVOKED_SESSION_RETENTION_DAYS: z.coerce.number().default(90),
  PASSWORD_RESET_RETENTION_DAYS: z.coerce.number().default(7),
  CLOUDINARY_CLOUD_NAME: z.string().min(1),
  CLOUDINARY_API_KEY: z.string().min(1),
  CLOUDINARY_API_SECRET: z.string().min(1),
  MEDIA_MAX_FILE_SIZE_MB: z.coerce.number().default(100),
  MEDIA_MAX_VIDEO_DURATION_SECONDS: z.coerce.number().default(180),
  CHAT_MAX_AUDIO_DURATION_SECONDS: z.coerce.number().default(60),
  CHAT_MAX_ROUND_VIDEO_DURATION_SECONDS: z.coerce.number().default(60),
  CHAT_GROUP_SEND_ENABLED: z.coerce.boolean().default(true),
  CHAT_MEDIA_SEND_ENABLED: z.coerce.boolean().default(true),
  CHAT_REPLAY_SYNC_ENABLED: z.coerce.boolean().default(true),
  CHAT_REALTIME_ENABLED: z.coerce.boolean().default(true),
  CHAT_RATE_LIMIT_STRICT_MODE: z.coerce.boolean().default(false),
});

const normalizedEnv = {
  ...process.env,
  MONGODB_URI: process.env.MONGODB_URI || process.env.MONGO_URI,
  ENABLE_BOOTSTRAP_USER: process.env.ENABLE_BOOTSTRAP_USER === "true",
  REDIS_REQUIRED:
    process.env.REDIS_REQUIRED === undefined
      ? process.env.NODE_ENV === "production"
      : parseBooleanEnv(process.env.REDIS_REQUIRED, process.env.NODE_ENV === "production"),
  REDIS_TLS: parseBooleanEnv(process.env.REDIS_TLS, false),
  REDIS_TLS_REJECT_UNAUTHORIZED: parseBooleanEnv(process.env.REDIS_TLS_REJECT_UNAUTHORIZED, true),
  QUEUE_ENABLED: parseBooleanEnv(process.env.QUEUE_ENABLED, true),
  WORKER_REQUIRED: parseBooleanEnv(process.env.WORKER_REQUIRED, false),
  RECOMMENDATION_WORKER_ENABLED: parseBooleanEnv(process.env.RECOMMENDATION_WORKER_ENABLED, true),
  RECOMMENDATION_MODE:
    process.env.RECOMMENDATION_MODE ||
    (parseBooleanEnv(process.env.QUEUE_ENABLED, true) === false ? "db-direct" : "weighted-cache"),
  ALLOW_MEMORY_DB_FALLBACK: process.env.ALLOW_MEMORY_DB_FALLBACK === "true",
};

const parsed = envSchema.safeParse(normalizedEnv);

if (!parsed.success) {
  console.error("Invalid environment variables", parsed.error.flatten().fieldErrors);
  process.exit(1);
}

const providerValidationErrors = [];
const parsedEnv = parsed.data;

if (parsedEnv.OTP_PROVIDER === "sms") {
  const smsProvider = String(parsedEnv.SMS_PROVIDER || "").toLowerCase();

  if (parsedEnv.NODE_ENV === "production" && !["twilio", "brevo"].includes(smsProvider)) {
    providerValidationErrors.push("SMS_PROVIDER must be 'twilio' or 'brevo' in production when OTP_PROVIDER=sms");
  }

  if (smsProvider === "twilio") {
    const missingTwilioFields = [
      ["TWILIO_ACCOUNT_SID", parsedEnv.TWILIO_ACCOUNT_SID],
      ["TWILIO_AUTH_TOKEN", parsedEnv.TWILIO_AUTH_TOKEN],
      ["TWILIO_FROM_PHONE", parsedEnv.TWILIO_FROM_PHONE],
    ].filter(([, value]) => !value);

    if (missingTwilioFields.length) {
      providerValidationErrors.push(
        `Missing SMS provider configuration: ${missingTwilioFields.map(([key]) => key).join(", ")}`
      );
    }
  } else if (smsProvider === "brevo") {
    const missingBrevoSmsFields = [
      ["BREVO_API_KEY", parsedEnv.BREVO_API_KEY],
      ["BREVO_SMS_SENDER", parsedEnv.BREVO_SMS_SENDER],
    ].filter(([, value]) => !value);

    if (missingBrevoSmsFields.length) {
      providerValidationErrors.push(
        `Missing SMS provider configuration: ${missingBrevoSmsFields.map(([key]) => key).join(", ")}`
      );
    }
  } else {
    providerValidationErrors.push("Unsupported SMS_PROVIDER. Use 'twilio' or 'brevo'.");
  }
}

const hasResendConfig = Boolean(parsedEnv.RESEND_API_KEY && parsedEnv.RESEND_FROM_EMAIL);
const hasBrevoConfig = Boolean(parsedEnv.BREVO_API_KEY && parsedEnv.BREVO_FROM_EMAIL && parsedEnv.BREVO_FROM_NAME);

const emailProvider = String(parsedEnv.EMAIL_PROVIDER || "").toLowerCase();
const hasEmailProviderConfig = emailProvider === "brevo" ? hasBrevoConfig : hasResendConfig;
const hasFirebaseEnvConfig = Boolean(
  parsedEnv.FIREBASE_PROJECT_ID &&
    parsedEnv.FIREBASE_CLIENT_EMAIL &&
    parsedEnv.FIREBASE_PRIVATE_KEY
);
const hasFirebaseServiceAccountConfig = Boolean(
  parsedEnv.FIREBASE_SERVICE_ACCOUNT_JSON || parsedEnv.FIREBASE_SERVICE_ACCOUNT_PATH
);
const cloudinaryPlaceholderValues = new Set(["your_cloud_name", "your_api_key", "your_api_secret"]);
const hasCloudinaryConfig = Boolean(
  parsedEnv.CLOUDINARY_CLOUD_NAME &&
    parsedEnv.CLOUDINARY_API_KEY &&
    parsedEnv.CLOUDINARY_API_SECRET &&
    !cloudinaryPlaceholderValues.has(parsedEnv.CLOUDINARY_CLOUD_NAME) &&
    !cloudinaryPlaceholderValues.has(parsedEnv.CLOUDINARY_API_KEY) &&
    !cloudinaryPlaceholderValues.has(parsedEnv.CLOUDINARY_API_SECRET)
);

if (parsedEnv.OTP_PROVIDER === "email") {
  if (parsedEnv.NODE_ENV === "production" && !["resend", "brevo"].includes(emailProvider)) {
    providerValidationErrors.push("EMAIL_PROVIDER must be 'resend' or 'brevo' in production when OTP_PROVIDER=email");
  }

  if (!hasEmailProviderConfig) {
    providerValidationErrors.push(
      emailProvider === "brevo"
        ? "Missing email provider configuration: BREVO_API_KEY, BREVO_FROM_EMAIL, BREVO_FROM_NAME"
        : "Missing email provider configuration: RESEND_API_KEY, RESEND_FROM_EMAIL"
    );
  }
}

if (parsedEnv.NODE_ENV === "production") {
  if (parsedEnv.ALLOW_MEMORY_DB_FALLBACK === true) {
    providerValidationErrors.push("ALLOW_MEMORY_DB_FALLBACK must be disabled in production");
  }

  if (parsedEnv.ENABLE_BOOTSTRAP_USER === true) {
    providerValidationErrors.push("ENABLE_BOOTSTRAP_USER must be disabled in production");
  }

  if (parsedEnv.OTP_PROVIDER === "mock") {
    providerValidationErrors.push("OTP_PROVIDER=mock is not allowed in production");
  }

  if (!["sms", "email"].includes(parsedEnv.OTP_PROVIDER)) {
    providerValidationErrors.push("OTP_PROVIDER must be 'sms' or 'email' in production");
  }

  if (!hasFirebaseEnvConfig && !hasFirebaseServiceAccountConfig) {
    providerValidationErrors.push(
      "Firebase Admin configuration is required in production: set FIREBASE_PROJECT_ID, FIREBASE_CLIENT_EMAIL, FIREBASE_PRIVATE_KEY or service account config"
    );
  }

  if (!hasCloudinaryConfig) {
    providerValidationErrors.push("Cloudinary configuration is required in production and cannot use placeholder values");
  }

  if (!hasEmailProviderConfig) {
    providerValidationErrors.push(
      emailProvider === "brevo"
        ? "Password reset email delivery requires BREVO_API_KEY, BREVO_FROM_EMAIL, BREVO_FROM_NAME in production"
        : "Password reset email delivery requires RESEND_API_KEY and RESEND_FROM_EMAIL in production"
    );
  }
}

if (providerValidationErrors.length) {
  console.error("Invalid provider configuration", providerValidationErrors);
  process.exit(1);
}

const buildRedisConnectionDetails = () => {
  const rawRedisUrl = (parsedEnv.REDIS_URL || "").trim();
  const hasRedisUrl = Boolean(rawRedisUrl);
  let redisUrl = rawRedisUrl || null;
  let redisHost = parsedEnv.REDIS_HOST;
  let redisPort = parsedEnv.REDIS_PORT;
  let redisUsernameConfigured = false;
  let redisTlsEnabled = Boolean(parsedEnv.REDIS_TLS);
  let redisTlsRejectUnauthorized = Boolean(parsedEnv.REDIS_TLS_REJECT_UNAUTHORIZED);
  let redisConfigSource = "host-port";

  if (hasRedisUrl) {
    redisConfigSource = "url";

    try {
      const parsedUrl = new URL(rawRedisUrl);
      redisHost = parsedUrl.hostname || redisHost;
      redisPort = parsedUrl.port ? Number(parsedUrl.port) : redisPort;
      redisUsernameConfigured = Boolean(parsedUrl.username);

      const queryTlsFlag = ["true", "1", "yes", "on"].includes(
        String(
          parsedUrl.searchParams.get("tls") ||
            parsedUrl.searchParams.get("ssl") ||
            parsedUrl.searchParams.get("secure") ||
            ""
        )
          .trim()
          .toLowerCase()
      );
      const queryRejectUnauthorized = parsedUrl.searchParams.get("rejectUnauthorized");
      if (queryRejectUnauthorized !== null) {
        redisTlsRejectUnauthorized = queryRejectUnauthorized !== "false";
      }
      redisTlsEnabled = parsedUrl.protocol === "rediss:" || queryTlsFlag || redisTlsEnabled;
    } catch (_error) {
      // Keep the raw URL if it cannot be parsed here; runtime diagnostics will report the issue.
    }
  }

  if (!hasRedisUrl) {
    redisUrl = `${redisTlsEnabled ? "rediss" : "redis"}://${
      parsedEnv.REDIS_PASSWORD ? `:${encodeURIComponent(parsedEnv.REDIS_PASSWORD)}@` : ""
    }${redisHost}:${redisPort}`;
  }

  return {
    redisUrl,
    redisHost,
    redisPort,
    redisConfigSource,
    redisUrlConfigured: hasRedisUrl,
    redisPasswordConfigured: Boolean(parsedEnv.REDIS_PASSWORD),
    redisUsernameConfigured,
    redisTlsEnabled,
    redisTlsRejectUnauthorized,
  };
};

const redisConnectionDetails = buildRedisConnectionDetails();

const env = {
  nodeEnv: parsedEnv.NODE_ENV,
  port: parsedEnv.PORT,
  mongoUri: parsedEnv.MONGODB_URI,
  enableBootstrapUser: parsedEnv.ENABLE_BOOTSTRAP_USER === true,
  bootstrapUserIdentity: parsedEnv.BOOTSTRAP_USER_IDENTITY || null,
  bootstrapUserPassword: parsedEnv.BOOTSTRAP_USER_PASSWORD || null,
  bootstrapUserUsername: parsedEnv.BOOTSTRAP_USER_USERNAME || null,
  bootstrapUserFullName: parsedEnv.BOOTSTRAP_USER_FULL_NAME || null,
  bootstrapUserRole: parsedEnv.BOOTSTRAP_USER_ROLE || "user",
  clientUrl: parsedEnv.CLIENT_URL === "*" ? true : parsedEnv.CLIENT_URL,
  jwtAccessSecret: parsedEnv.JWT_ACCESS_SECRET,
  jwtRefreshSecret: parsedEnv.JWT_REFRESH_SECRET,
  jwtAccessExpiresIn: parsedEnv.JWT_ACCESS_EXPIRES_IN,
  jwtRefreshExpiresIn: parsedEnv.JWT_REFRESH_EXPIRES_IN,
  googleClientId: parsedEnv.GOOGLE_CLIENT_ID || null,
  firebaseProjectId: parsedEnv.FIREBASE_PROJECT_ID || null,
  firebaseClientEmail: parsedEnv.FIREBASE_CLIENT_EMAIL || null,
  firebasePrivateKey: parsedEnv.FIREBASE_PRIVATE_KEY || null,
  firebaseServiceAccountJson: parsedEnv.FIREBASE_SERVICE_ACCOUNT_JSON || null,
  firebaseServiceAccountPath: parsedEnv.FIREBASE_SERVICE_ACCOUNT_PATH || null,
  otpSecret: parsedEnv.OTP_SECRET,
  otpExpiresMinutes: parsedEnv.OTP_EXPIRES_MINUTES,
  otpProvider: parsedEnv.OTP_PROVIDER,
  smsProvider: parsedEnv.SMS_PROVIDER,
  emailProvider: emailProvider || "resend",
  otpResendCooldownSeconds: parsedEnv.OTP_RESEND_COOLDOWN_SECONDS,
  otpMaxSendsPerWindow: parsedEnv.OTP_MAX_SENDS_PER_WINDOW,
  otpSendWindowMinutes: parsedEnv.OTP_SEND_WINDOW_MINUTES,
  twilioAccountSid: parsedEnv.TWILIO_ACCOUNT_SID || null,
  twilioAuthToken: parsedEnv.TWILIO_AUTH_TOKEN || null,
  twilioFromPhone: parsedEnv.TWILIO_FROM_PHONE || null,
  resendApiKey: parsedEnv.RESEND_API_KEY || null,
  resendFromEmail: parsedEnv.RESEND_FROM_EMAIL || null,
  brevoApiKey: parsedEnv.BREVO_API_KEY || null,
  brevoFromEmail: parsedEnv.BREVO_FROM_EMAIL || null,
  brevoFromName: parsedEnv.BREVO_FROM_NAME || null,
  brevoSmsSender: parsedEnv.BREVO_SMS_SENDER || null,
  passwordResetBaseUrl: parsedEnv.PASSWORD_RESET_BASE_URL || null,
  appDeepLinkResetUrl: parsedEnv.APP_DEEP_LINK_RESET_URL || null,
  requestSlowMs: parsedEnv.REQUEST_SLOW_MS,
  mongoSlowQueryMs: parsedEnv.MONGO_SLOW_QUERY_MS,
  mongoQueryDebug: parsedEnv.MONGO_QUERY_DEBUG,
  allowMemoryDbFallback: parsedEnv.ALLOW_MEMORY_DB_FALLBACK,
  mongoServerSelectionTimeoutMs: parsedEnv.MONGO_SERVER_SELECTION_TIMEOUT_MS,
  mongoConnectTimeoutMs: parsedEnv.MONGO_CONNECT_TIMEOUT_MS,
  mongoSocketTimeoutMs: parsedEnv.MONGO_SOCKET_TIMEOUT_MS,
  mongoForceIpv4: parsedEnv.MONGO_FORCE_IPV4,
  mongoStartupDiagnostics: parsedEnv.MONGO_STARTUP_DIAGNOSTICS,
  mongoDnsServers: (parsedEnv.MONGO_DNS_SERVERS || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean),
  rateLimitWindowMs: parsedEnv.RATE_LIMIT_WINDOW_MS,
  rateLimitMax: parsedEnv.RATE_LIMIT_MAX,
  authenticatedRateLimitMax: parsedEnv.AUTHENTICATED_RATE_LIMIT_MAX,
  redisUrl: redisConnectionDetails.redisUrl,
  redisHost: redisConnectionDetails.redisHost,
  redisPort: redisConnectionDetails.redisPort,
  redisUrlConfigured: redisConnectionDetails.redisUrlConfigured,
  redisPasswordConfigured: redisConnectionDetails.redisPasswordConfigured,
  redisUsernameConfigured: redisConnectionDetails.redisUsernameConfigured,
  redisTlsEnabled: redisConnectionDetails.redisTlsEnabled,
  redisTlsRejectUnauthorized: redisConnectionDetails.redisTlsRejectUnauthorized,
  redisConfigSource: redisConnectionDetails.redisConfigSource,
  redisPrefix: parsedEnv.REDIS_PREFIX,
  redisRequired: parsedEnv.REDIS_REQUIRED,
  queueEnabled: parsedEnv.QUEUE_ENABLED,
  workerRequired: parsedEnv.WORKER_REQUIRED,
  recommendationWorkerEnabled: parsedEnv.RECOMMENDATION_WORKER_ENABLED,
  recommendationMode: parsedEnv.RECOMMENDATION_MODE,
  cacheMode: parsedEnv.CACHE_MODE,
  enableMaintenanceCleanupScheduler: parsedEnv.ENABLE_MAINTENANCE_CLEANUP_SCHEDULER,
  maintenanceCleanupIntervalMinutes: parsedEnv.MAINTENANCE_CLEANUP_INTERVAL_MINUTES,
  expiredStoryRetentionHours: parsedEnv.EXPIRED_STORY_RETENTION_HOURS,
  inactivePushTokenRetentionDays: parsedEnv.INACTIVE_PUSH_TOKEN_RETENTION_DAYS,
  revokedSessionRetentionDays: parsedEnv.REVOKED_SESSION_RETENTION_DAYS,
  passwordResetRetentionDays: parsedEnv.PASSWORD_RESET_RETENTION_DAYS,
  cloudinaryName: parsedEnv.CLOUDINARY_CLOUD_NAME,
  cloudinaryKey: parsedEnv.CLOUDINARY_API_KEY,
  cloudinarySecret: parsedEnv.CLOUDINARY_API_SECRET,
  mediaMaxFileSizeBytes: parsedEnv.MEDIA_MAX_FILE_SIZE_MB * 1024 * 1024,
  mediaMaxVideoDurationSeconds: parsedEnv.MEDIA_MAX_VIDEO_DURATION_SECONDS,
  chatMaxAudioDurationSeconds: parsedEnv.CHAT_MAX_AUDIO_DURATION_SECONDS,
  chatMaxRoundVideoDurationSeconds: parsedEnv.CHAT_MAX_ROUND_VIDEO_DURATION_SECONDS,
  chatGroupSendEnabled: parsedEnv.CHAT_GROUP_SEND_ENABLED,
  chatMediaSendEnabled: parsedEnv.CHAT_MEDIA_SEND_ENABLED,
  chatReplaySyncEnabled: parsedEnv.CHAT_REPLAY_SYNC_ENABLED,
  chatRealtimeEnabled: parsedEnv.CHAT_REALTIME_ENABLED,
  chatRateLimitStrictMode: parsedEnv.CHAT_RATE_LIMIT_STRICT_MODE,
};

module.exports = { env };
