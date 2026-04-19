const bcrypt = require("bcryptjs");
const crypto = require("crypto");
const { OAuth2Client } = require("google-auth-library");

const ApiError = require("../utils/ApiError");
const OtpCode = require("../models/OtpCode");
const PasswordResetToken = require("../models/PasswordResetToken");
const Session = require("../models/Session");
const User = require("../models/User");
const {
  hashOtp,
  hashToken,
  signAccessToken,
  signRefreshToken,
  verifyRefreshToken,
} = require("../utils/token");
const { env } = require("../config/env");
const { getOrCreateSettings } = require("./settingsService");
const { createAuditLog } = require("./auditLogService");
const logger = require("../utils/logger");
const { denylistToken, isTokenDenied } = require("./redisSecurityService");
const { sendOtpForIdentity, maskIdentity } = require("./otpDeliveryService");
const { sendPasswordResetEmail } = require("./passwordResetDeliveryService");

const googleClient = env.googleClientId ? new OAuth2Client(env.googleClientId) : null;
const PASSWORD_RESET_TOKEN_TTL_MS = 15 * 60 * 1000;
const shouldExposeNonProductionSecrets = () => env.nodeEnv !== "production";

const generateOtp = () => String(Math.floor(100000 + Math.random() * 900000));
const buildPasswordResetToken = () => crypto.randomBytes(32).toString("hex");

const normalizeIdentity = (identity) => String(identity || "").trim().toLowerCase();

const buildOtpSendMeta = (meta = {}) => ({
  ipAddress: meta.ipAddress || null,
  userAgent: meta.userAgent || null,
  deviceId: meta.deviceId || null,
});

const buildForgotPasswordResponse = (identity, expiresAt = null, resetToken = undefined) => ({
  identity,
  expiresAt,
  resetToken,
});

const enforceOtpSendLimits = async (identity, purpose) => {
  const cooldownStartedAt = new Date(Date.now() - env.otpResendCooldownSeconds * 1000);
  const latestOtp = await OtpCode.findOne({ identity, purpose }).sort({ createdAt: -1 }).select("createdAt");

  if (latestOtp && latestOtp.createdAt > cooldownStartedAt) {
    const secondsRemaining = Math.max(
      1,
      Math.ceil((latestOtp.createdAt.getTime() + env.otpResendCooldownSeconds * 1000 - Date.now()) / 1000)
    );

    throw new ApiError(429, `Please wait ${secondsRemaining} seconds before requesting another OTP`);
  }

  const sendWindowStartedAt = new Date(Date.now() - env.otpSendWindowMinutes * 60 * 1000);
  const sendCount = await OtpCode.countDocuments({
    identity,
    purpose,
    createdAt: { $gte: sendWindowStartedAt },
  });

  if (sendCount >= env.otpMaxSendsPerWindow) {
    throw new ApiError(429, "Too many OTP requests. Please try again later.");
  }
};

const buildSessionExpiryDate = () => new Date(Date.now() + resolveDurationToMs(env.jwtRefreshExpiresIn));
const buildAccessExpiryDate = () => new Date(Date.now() + resolveDurationToMs(env.jwtAccessExpiresIn));

function resolveDurationToMs(input) {
  if (!input) return 15 * 60 * 1000;
  if (/^\d+$/.test(input)) return Number(input) * 1000;

  const match = /^(\d+)([smhd])$/i.exec(String(input).trim());
  if (!match) {
    return 15 * 60 * 1000;
  }

  const value = Number(match[1]);
  const unit = match[2].toLowerCase();

  const multiplier = {
    s: 1000,
    m: 60 * 1000,
    h: 60 * 60 * 1000,
    d: 24 * 60 * 60 * 1000,
  }[unit];

  return value * multiplier;
}

const sanitizeUser = (user) => ({
  _id: user._id,
  identity: user.identity,
  fullName: user.fullName,
  username: user.username,
  bio: user.bio,
  avatar: user.avatar,
  banner: user.banner,
  birthday: user.birthday,
  location: user.location,
  school: user.school,
  region: user.region,
  district: user.district,
  grade: user.grade,
  group: user.group,
  type: user.type,
  role: user.role,
  isPrivateAccount: user.isPrivateAccount,
  followersCount: user.followersCount,
  followingCount: user.followingCount,
  postsCount: user.postsCount,
  googleId: user.googleId,
  lastLoginAt: user.lastLoginAt,
  createdAt: user.createdAt,
});

const sendOtp = async ({ identity, purpose }, meta = {}) => {
  const normalizedIdentity = normalizeIdentity(identity);
  const safeMeta = buildOtpSendMeta(meta);

  if (purpose === "register") {
    const existingUser = await User.findOne({ identity: normalizedIdentity }).select("_id");
    const existingAccount = await Account.findOne({ identity: normalizedIdentity }).select("_id");
    if (existingUser || existingAccount) {
      throw new ApiError(
        409,
        "Bu email yoki telefon raqam ro'yxatdan o'tgan. Agar parolni unutgan bo'lsangiz, parolni almashtirish (Forgot password) tugmasini bosing."
      );
    }
  }

  await enforceOtpSendLimits(normalizedIdentity, purpose);
  await OtpCode.deleteMany({ identity: normalizedIdentity, purpose });
  const otp = generateOtp();
  const expiresAt = new Date(Date.now() + env.otpExpiresMinutes * 60 * 1000);

  await OtpCode.create({
    identity: normalizedIdentity,
    purpose,
    codeHash: hashOtp(normalizedIdentity, otp),
    expiresAt,
  });

  try {
    await sendOtpForIdentity(normalizedIdentity, otp);
  } catch (error) {
    logger.error("OTP delivery failed", {
      identity: maskIdentity(normalizedIdentity),
      purpose,
      ipAddress: safeMeta.ipAddress,
      userAgent: safeMeta.userAgent,
      message: error.message,
    });

    if (shouldExposeNonProductionSecrets()) {
      logger.warn("otp_send_failed", {
        identity: normalizedIdentity,
        purpose,
        fallback: "non-production-response",
      });
    } else {
      await OtpCode.deleteMany({ identity: normalizedIdentity, purpose });
      throw new ApiError(502, "OTP delivery failed. Please try again later.");
    }
  }

  return {
    identity: normalizedIdentity,
    purpose,
    expiresAt,
    otp: shouldExposeNonProductionSecrets() ? otp : undefined,
  };
};

const verifyOtp = async ({ identity, otp, purpose }) => {
  const record = await OtpCode.findOne({ identity, purpose }).sort({ createdAt: -1 });
  if (!record || record.expiresAt < new Date()) {
    throw new ApiError(400, "OTP expired or not found");
  }

  if (record.codeHash !== hashOtp(identity, otp)) {
    throw new ApiError(400, "Invalid OTP");
  }

  record.isVerified = true;
  await record.save();

  return { identity, purpose, verified: true };
};

const ensureUniqueIdentityAndUsername = async ({ identity, username }) => {
  const normalizedIdentity = String(identity || "").trim().toLowerCase();
  if (normalizedIdentity) {
    const existingIdentity = await User.findOne({ identity: normalizedIdentity });
    if (existingIdentity) {
      throw new ApiError(409, "Identity already exists");
    }
    const existingAccount = await Account.findOne({ identity: normalizedIdentity });
    if (existingAccount) {
      throw new ApiError(409, "Identity already exists");
    }
  }

  const existingUsername = await User.findOne({ username: username.toLowerCase() });
  if (existingUsername) {
    throw new ApiError(409, "Username already taken");
  }
};

const buildAuthContext = (meta = {}) => ({
  userAgent: meta.userAgent || null,
  ipAddress: meta.ipAddress || null,
  deviceId: meta.deviceId || null,
});

const Account = require("../models/Account");
// ... existing requires remain above ...

const ensureAccountForUser = async (user) => {
  if (user.accountId) {
    const existing = await Account.findById(user.accountId);
    if (existing) return existing;
  }
  if (!user.identity) {
    throw new ApiError(500, "User identity missing for account migration");
  }
  let account = await Account.findOne({ identity: user.identity.toLowerCase() });
  if (!account) {
    account = await Account.create({
      identity: user.identity.toLowerCase(),
      passwordHash: user.passwordHash || null,
      googleId: user.googleId || null,
      lastLoginAt: user.lastLoginAt || null,
      tokenInvalidBefore: user.tokenInvalidBefore || null,
      isSuspended: user.isSuspended || false,
      suspendedUntil: user.suspendedUntil || null,
      suspensionReason: user.suspensionReason || null,
    });
  }
  user.accountId = account.id;
  await user.save();
  return account;
};

const resolveRefreshPayload = async (payload) => {
  if (payload?.pid) {
    return {
      accountId: payload.sub,
      profileId: payload.pid,
      sessionId: payload.sid,
      jti: payload.jti,
      exp: payload.exp,
    };
  }

  // Legacy: sub is profileId
  const user = await User.findById(payload.sub);
  if (!user) throw new ApiError(401, "User not found");
  const account = await ensureAccountForUser(user);
  return {
    accountId: account.id,
    profileId: user.id,
    sessionId: payload.sid,
    jti: payload.jti,
    exp: payload.exp,
  };
};

const createSessionTokens = async (user, meta = {}, options = {}) => {
  const sessionId = crypto.randomUUID();
  const familyId = options.familyId || crypto.randomUUID();
  const accessJti = crypto.randomUUID();
  const refreshJti = crypto.randomUUID();
  const account = await ensureAccountForUser(user);
  const accessToken = signAccessToken(account.id, user.id, sessionId, accessJti);
  const refreshToken = signRefreshToken(sessionId, account.id, user.id, refreshJti);
  const authContext = buildAuthContext(meta);

  await Session.create({
    sessionId,
    familyId,
    user: user.id,
    account: account.id,
    tokenHash: hashToken(refreshToken),
    refreshJti,
    lastAccessJti: accessJti,
    expiresAt: buildSessionExpiryDate(),
    userAgent: authContext.userAgent,
    ipAddress: authContext.ipAddress,
    deviceId: authContext.deviceId,
    isSuspicious: options.isSuspicious || false,
  });

  return { accessToken, refreshToken, sessionId, familyId, accessJti, refreshJti };
};

const register = async (payload, meta = {}) => {
  const {
    identity,
    otp,
    fullName,
    username,
    password,
    type = "student",
    region,
    district,
    school,
    birthday = null,
    grade = "",
    group = "",
    bio = "",
    isPrivateAccount = false,
  } = payload;

  const verifiedOtp = await OtpCode.findOne({ identity, purpose: "register", isVerified: true }).sort({ createdAt: -1 });
  if (!verifiedOtp || verifiedOtp.codeHash !== hashOtp(identity, otp)) {
    throw new ApiError(400, "OTP verification required");
  }

  await ensureUniqueIdentityAndUsername({ identity, username });

  const passwordHash = await bcrypt.hash(password, 12);
  const user = await User.create({
    identity,
    fullName,
    username: username.toLowerCase(),
    bio,
    passwordHash,
    isPrivateAccount,
    type,
    region: type === "student" ? (region || "") : "",
    district: type === "student" ? (district || "") : "",
    school: type === "student" ? (school || "") : "",
    birthday: birthday || null,
    grade: type === "student" ? (grade || "") : "",
    group: type === "student" ? (group || "") : "",
    lastLoginAt: new Date(),
  });

  await getOrCreateSettings(user.id);
  await OtpCode.deleteMany({ identity });

  const tokens = await createSessionTokens(user, meta);
  await createAuditLog({
    actor: user.id,
    action: "auth.register",
    category: "auth",
    status: "success",
    targetUser: user.id,
    sessionId: tokens.sessionId,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
  });

  return { user: sanitizeUser(user), ...tokens };
};

const login = async ({ identityOrUsername, password }, meta = {}) => {
  const query =
    identityOrUsername.includes("@") || identityOrUsername.startsWith("+")
      ? { identity: identityOrUsername.toLowerCase() }
      : { username: identityOrUsername.toLowerCase() };

  const user = await User.findOne(query).select(
    "identity fullName username bio avatar banner birthday location school region district grade group type role isPrivateAccount followersCount followingCount postsCount googleId lastLoginAt createdAt accountId passwordHash tokenInvalidBefore isSuspended suspendedUntil suspensionReason"
  );
  if (!user || !user.passwordHash) {
    await createAuditLog({
      action: "auth.login",
      category: "auth",
      status: "failure",
      ipAddress: meta.ipAddress || null,
      userAgent: meta.userAgent || null,
      details: { identityOrUsername, reason: "user_not_found" },
    });
    throw new ApiError(401, "Invalid credentials");
  }

  const isMatch = await bcrypt.compare(password, user.passwordHash);
  if (!isMatch) {
    await createAuditLog({
      actor: user.id,
      action: "auth.login",
      category: "auth",
      status: "failure",
      targetUser: user.id,
      ipAddress: meta.ipAddress || null,
      userAgent: meta.userAgent || null,
      details: { reason: "invalid_password" },
    });
    throw new ApiError(401, "Invalid credentials");
  }

  const previousSession = await Session.findOne({ user: user.id, isRevoked: false }).sort({ createdAt: -1 });
  const isSuspicious =
    Boolean(previousSession) &&
    ((meta.deviceId && previousSession.deviceId && previousSession.deviceId !== meta.deviceId) ||
      (meta.userAgent && previousSession.userAgent && previousSession.userAgent !== meta.userAgent) ||
      (meta.ipAddress && previousSession.ipAddress && previousSession.ipAddress !== meta.ipAddress));

  user.lastLoginAt = new Date();
  await user.save();

  const tokens = await createSessionTokens(user, meta, { isSuspicious });
  if (isSuspicious) {
    logger.warn("Suspicious login detected", {
      userId: user.id,
      ipAddress: meta.ipAddress,
      userAgent: meta.userAgent,
    });
    await createAuditLog({
      actor: user.id,
      action: "auth.suspicious_login",
      category: "security",
      status: "warning",
      targetUser: user.id,
      sessionId: tokens.sessionId,
      ipAddress: meta.ipAddress || null,
      userAgent: meta.userAgent || null,
      details: { previousSessionId: previousSession?.sessionId || null, deviceId: meta.deviceId || null },
    });
  }

  await createAuditLog({
    actor: user.id,
    action: "auth.login",
    category: "auth",
    status: "success",
    targetUser: user.id,
    sessionId: tokens.sessionId,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
    details: { suspicious: isSuspicious },
  });

  return { user: sanitizeUser(user), ...tokens };
};

const forgotPassword = async (identity, meta = {}) => {
  const normalizedIdentity = normalizeIdentity(identity);
  const fallbackExpiresAt = new Date(Date.now() + PASSWORD_RESET_TOKEN_TTL_MS);
  const user = await User.findOne({ identity: normalizedIdentity }).select("_id identity");

  if (!user) {
    await createAuditLog({
      action: "auth.forgot_password",
      category: "auth",
      status: "failure",
      ipAddress: meta.ipAddress || null,
      userAgent: meta.userAgent || null,
      details: { identity: normalizedIdentity, reason: "user_not_found" },
    });

    return buildForgotPasswordResponse(normalizedIdentity, fallbackExpiresAt, undefined);
  }

  await PasswordResetToken.deleteMany({ user: user.id, usedAt: null });

  const rawToken = buildPasswordResetToken();
  const expiresAt = new Date(Date.now() + PASSWORD_RESET_TOKEN_TTL_MS);

  await PasswordResetToken.create({
    user: user.id,
    identity: normalizedIdentity,
    tokenHash: hashToken(rawToken),
    expiresAt,
    requestedByIp: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
  });

  try {
    await sendPasswordResetEmail(normalizedIdentity, rawToken, { expiresAt });
  } catch (error) {
    logger.error("Password reset delivery failed", {
      identity: normalizedIdentity,
      ipAddress: meta.ipAddress || null,
      userAgent: meta.userAgent || null,
      message: error.message,
    });

    await createAuditLog({
      actor: user.id,
      action: "auth.forgot_password_delivery",
      category: "auth",
      status: "failure",
      targetUser: user.id,
      ipAddress: meta.ipAddress || null,
      userAgent: meta.userAgent || null,
      details: { reason: "delivery_failed" },
    });

    if (shouldExposeNonProductionSecrets()) {
      return buildForgotPasswordResponse(normalizedIdentity, expiresAt, rawToken);
    }

    await PasswordResetToken.deleteMany({ user: user.id, tokenHash: hashToken(rawToken), usedAt: null });
    return buildForgotPasswordResponse(normalizedIdentity, fallbackExpiresAt, undefined);
  }

  await createAuditLog({
    actor: user.id,
    action: "auth.forgot_password",
    category: "auth",
    status: "success",
    targetUser: user.id,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
  });

  return buildForgotPasswordResponse(normalizedIdentity, expiresAt, shouldExposeNonProductionSecrets() ? rawToken : undefined);
};

const resetPassword = async ({ token, password }, meta = {}) => {
  const tokenHash = hashToken(token);
  const resetRecord = await PasswordResetToken.findOne({
    tokenHash,
    usedAt: null,
  }).populate("user");

  if (!resetRecord || resetRecord.expiresAt < new Date() || !resetRecord.user) {
    await createAuditLog({
      action: "auth.reset_password",
      category: "auth",
      status: "failure",
      ipAddress: meta.ipAddress || null,
      userAgent: meta.userAgent || null,
      details: { reason: "invalid_or_expired_reset_token" },
    });
    throw new ApiError(400, "Reset token is invalid or expired");
  }

  resetRecord.user.passwordHash = await bcrypt.hash(password, 12);
  const now = new Date();
  resetRecord.user.tokenInvalidBefore = now;
  resetRecord.user.lastLoginAt = null;
  await resetRecord.user.save();

  // If this profile belongs to an account, invalidate account tokens too.
  if (resetRecord.user.accountId) {
    await Account.findByIdAndUpdate(resetRecord.user.accountId, { tokenInvalidBefore: now });
  }

  resetRecord.usedAt = new Date();
  await resetRecord.save();
  await Session.updateMany(
    { user: resetRecord.user.id, isRevoked: false },
    { isRevoked: true, revokedReason: "password_reset" }
  );
  await PasswordResetToken.updateMany(
    { user: resetRecord.user.id, usedAt: null, _id: { $ne: resetRecord._id } },
    { usedAt: new Date() }
  );

  await createAuditLog({
    actor: resetRecord.user.id,
    action: "auth.reset_password",
    category: "auth",
    status: "success",
    targetUser: resetRecord.user.id,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
  });

  return { reset: true };
};

const changePassword = async (accountId, profileId, payload, meta = {}) => {
  const account = await Account.findById(accountId);
  if (!account) {
    throw new ApiError(404, "Account not found");
  }

  const user = await User.findById(profileId);
  if (!user) {
    throw new ApiError(404, "User not found");
  }

  // Account-scoped password is canonical in multi-profile mode.
  // Fallback to profile passwordHash for legacy users.
  const effectiveHash = account.passwordHash || user.passwordHash;
  if (!effectiveHash) {
    throw new ApiError(400, "Password login is not enabled for this account");
  }

  const passwordMatches = await bcrypt.compare(payload.currentPassword, effectiveHash);
  if (!passwordMatches) {
    await createAuditLog({
      actor: profileId,
      action: "auth.change_password",
      category: "auth",
      status: "failure",
      targetUser: profileId,
      ipAddress: meta.ipAddress || null,
      userAgent: meta.userAgent || null,
      details: { reason: "invalid_current_password" },
    });
    throw new ApiError(400, "Current password is incorrect");
  }

  if (payload.currentPassword === payload.newPassword) {
    throw new ApiError(400, "New password must be different from current password");
  }

  const nextHash = await bcrypt.hash(payload.newPassword, 12);
  const now = new Date();
  account.passwordHash = nextHash;
  account.tokenInvalidBefore = now;
  await account.save();

  // Keep profile hash in sync for legacy codepaths.
  user.passwordHash = nextHash;
  user.tokenInvalidBefore = now;
  await user.save();

  await Session.updateMany(
    { account: accountId, isRevoked: false },
    { isRevoked: true, revokedReason: "password_changed" }
  );
  if (meta.accessTokenJti && meta.accessTokenExp) {
    await denylistToken({
      jti: meta.accessTokenJti,
      tokenType: "access",
      expiresAt: meta.accessTokenExp * 1000,
    });
  }

  await createAuditLog({
    actor: profileId,
    action: "auth.change_password",
    category: "auth",
    status: "success",
    targetUser: profileId,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
  });

  return { passwordChanged: true };
};

const logout = async (refreshToken, meta = {}) => {
  const payload = verifyRefreshToken(refreshToken);
  const resolved = await resolveRefreshPayload(payload);
  const session = await Session.findOneAndUpdate(
    {
      tokenHash: hashToken(refreshToken),
      sessionId: resolved.sessionId,
      account: resolved.accountId,
      user: resolved.profileId,
    },
    { isRevoked: true, revokedReason: "logout_current_device" },
    { new: true }
  );

  if (session) {
    await denylistToken({
      jti: resolved.jti,
      tokenType: "refresh",
      expiresAt: resolved.exp * 1000,
    });
    if (meta.accessTokenJti && meta.accessTokenExp) {
      await denylistToken({
        jti: meta.accessTokenJti,
        tokenType: "access",
        expiresAt: meta.accessTokenExp * 1000,
      });
    }
    await createAuditLog({
      actor: session.user,
      action: "auth.logout_current_device",
      category: "auth",
      status: "success",
      targetUser: session.user,
      sessionId: session.sessionId,
      ipAddress: meta.ipAddress || null,
      userAgent: meta.userAgent || null,
    });
  }

  return { loggedOut: true };
};

const logoutAllDevices = async (accountId, currentSessionId, meta = {}) => {
  const now = new Date();
  await Session.updateMany({ account: accountId, isRevoked: false }, { isRevoked: true, revokedReason: "logout_all_devices" });
  await Account.findByIdAndUpdate(accountId, { tokenInvalidBefore: now });

  if (meta.accessTokenJti && meta.accessTokenExp) {
    await denylistToken({
      jti: meta.accessTokenJti,
      tokenType: "access",
      expiresAt: meta.accessTokenExp * 1000,
    });
  }

  await createAuditLog({
    actor: null,
    action: "auth.logout_all_devices",
    category: "auth",
    status: "success",
    targetUser: null,
    sessionId: currentSessionId || null,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
  });

  return { loggedOutAll: true };
};

const revokeCompromisedRefreshToken = async (accountId, refreshToken, meta = {}) => {
  const payload = verifyRefreshToken(refreshToken);
  const resolved = await resolveRefreshPayload(payload);
  if (resolved.accountId !== accountId) {
    throw new ApiError(403, "Not allowed");
  }
  const session = await Session.findOne({
    sessionId: resolved.sessionId,
    account: resolved.accountId,
    user: resolved.profileId,
    tokenHash: hashToken(refreshToken),
  });

  if (!session) {
    throw new ApiError(404, "Session not found");
  }

  await Session.updateMany(
    { account: accountId, familyId: session.familyId, isRevoked: false },
    { isRevoked: true, isCompromised: true, revokedReason: "compromised_refresh_token" }
  );
  await denylistToken({
    jti: resolved.jti,
    tokenType: "refresh",
    expiresAt: resolved.exp * 1000,
  });
  if (meta.accessTokenJti && meta.accessTokenExp) {
    await denylistToken({
      jti: meta.accessTokenJti,
      tokenType: "access",
      expiresAt: meta.accessTokenExp * 1000,
    });
  }

  await createAuditLog({
    actor: resolved.profileId,
    action: "auth.revoke_compromised_refresh_token",
    category: "security",
    status: "warning",
    targetUser: resolved.profileId,
    sessionId: session.sessionId,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
    details: { familyId: session.familyId },
  });

  return { revoked: true };
};

const refreshAuthToken = async (refreshToken, meta = {}) => {
  const payload = verifyRefreshToken(refreshToken);
  const resolved = await resolveRefreshPayload(payload);
  const denied = await isTokenDenied({ jti: payload.jti, tokenType: "refresh" });
  if (denied) {
    throw new ApiError(401, "Refresh token has been revoked");
  }

  const session = await Session.findOne({
    sessionId: resolved.sessionId,
    tokenHash: hashToken(refreshToken),
    account: resolved.accountId,
    user: resolved.profileId,
    isRevoked: false,
    refreshJti: resolved.jti,
  });

  if (!session || session.expiresAt < new Date()) {
    await createAuditLog({
      actor: resolved.profileId,
      action: "auth.refresh_token",
      category: "auth",
      status: "failure",
      targetUser: resolved.profileId,
      sessionId: resolved.sessionId,
      ipAddress: meta.ipAddress || null,
      userAgent: meta.userAgent || null,
      details: { reason: "expired_or_invalid" },
    });
    throw new ApiError(401, "Refresh token expired or invalid");
  }

  session.isRevoked = true;
  session.lastUsedAt = new Date();
  session.revokedReason = "rotated";
  const isSuspiciousRefresh =
    Boolean(session.deviceId && meta.deviceId && session.deviceId !== meta.deviceId) ||
    Boolean(session.userAgent && meta.userAgent && session.userAgent !== meta.userAgent) ||
    Boolean(session.ipAddress && meta.ipAddress && session.ipAddress !== meta.ipAddress);

  if (isSuspiciousRefresh) {
    session.isCompromised = true;
    session.revokedReason = "suspicious_refresh";
    await session.save();
    await Session.updateMany(
      { account: resolved.accountId, familyId: session.familyId, isRevoked: false },
      { isRevoked: true, isCompromised: true, revokedReason: "suspicious_refresh" }
    );
    await createAuditLog({
      actor: resolved.profileId,
      action: "auth.suspicious_refresh",
      category: "security",
      status: "warning",
      targetUser: resolved.profileId,
      sessionId: session.sessionId,
      ipAddress: meta.ipAddress || null,
      userAgent: meta.userAgent || null,
      details: { familyId: session.familyId },
    });
    throw new ApiError(401, "Suspicious refresh token usage detected");
  }

  await session.save();
  await denylistToken({
    jti: resolved.jti,
    tokenType: "refresh",
    expiresAt: resolved.exp * 1000,
  });

  const user = await User.findById(resolved.profileId);
  if (!user) {
    throw new ApiError(401, "User not found");
  }

  const tokens = await createSessionTokens(user, meta, { familyId: session.familyId });
  await Session.findOneAndUpdate({ sessionId: session.sessionId }, { replacedBySessionId: tokens.sessionId });
  await createAuditLog({
    actor: user.id,
    action: "auth.refresh_token",
    category: "auth",
    status: "success",
    targetUser: user.id,
    sessionId: tokens.sessionId,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
  });

  return { user: sanitizeUser(user), ...tokens };
};

const listSessions = async (accountId, currentSessionId = null) => {
  const sessions = await Session.find({ account: accountId })
    .sort({ lastUsedAt: -1, createdAt: -1 })
    .select("sessionId familyId deviceId userAgent ipAddress createdAt lastUsedAt expiresAt isRevoked revokedReason isSuspicious isCompromised");

  return sessions.map((session) => ({
    sessionId: session.sessionId,
    familyId: session.familyId,
    deviceId: session.deviceId,
    userAgent: session.userAgent,
    ipAddress: session.ipAddress,
    createdAt: session.createdAt,
    lastUsedAt: session.lastUsedAt,
    expiresAt: session.expiresAt,
    isRevoked: session.isRevoked,
    revokedReason: session.revokedReason,
    isSuspicious: session.isSuspicious,
    isCompromised: session.isCompromised,
    isCurrent: currentSessionId ? session.sessionId === currentSessionId : false,
  }));
};

const revokeSession = async (accountId, sessionId, meta = {}) => {
  const session = await Session.findOne({ account: accountId, sessionId });
  if (!session) {
    throw new ApiError(404, "Session not found");
  }

  if (!session.isRevoked) {
    session.isRevoked = true;
    session.revokedReason = "manual_session_revoke";
    await session.save();
  }

  await denylistToken({
    jti: session.refreshJti,
    tokenType: "refresh",
    expiresAt: session.expiresAt,
  });

  if (session.lastAccessJti) {
    await denylistToken({
      jti: session.lastAccessJti,
      tokenType: "access",
      expiresAt: buildAccessExpiryDate(),
    });
  }

  await createAuditLog({
    actor: session.user,
    action: "auth.revoke_session",
    category: "security",
    status: "success",
    targetUser: session.user,
    sessionId,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
  });

  return {
    revoked: true,
    sessionId,
  };
};

const googleLogin = async (idToken, meta = {}) => {
  if (!googleClient || !env.googleClientId) {
    throw new ApiError(500, "Google login is not configured");
  }

  const ticket = await googleClient.verifyIdToken({
    idToken,
    audience: env.googleClientId,
  });
  const payload = ticket.getPayload();

  if (!payload?.sub || !payload?.email || payload.email_verified !== true) {
    throw new ApiError(401, "Invalid Google identity");
  }

  let user = await User.findOne({
    $or: [{ googleId: payload.sub }, { identity: payload.email.toLowerCase() }],
  });

  if (!user) {
    const baseUsername = (payload.email.split("@")[0] || "googleuser")
      .toLowerCase()
      .replace(/[^a-z0-9._]/g, "")
      .slice(0, 24) || "googleuser";

    let candidateUsername = baseUsername;
    let suffix = 1;
    while (await User.findOne({ username: candidateUsername })) {
      candidateUsername = `${baseUsername}${suffix}`.slice(0, 30);
      suffix += 1;
    }

    user = await User.create({
      identity: payload.email.toLowerCase(),
      fullName: payload.name || payload.email.split("@")[0],
      username: candidateUsername,
      bio: "",
      avatar: payload.picture || null,
      googleId: payload.sub,
      lastLoginAt: new Date(),
    });
    await getOrCreateSettings(user.id);
  } else {
    user.googleId = payload.sub;
    user.lastLoginAt = new Date();
    if (!user.avatar && payload.picture) {
      user.avatar = payload.picture;
    }
    await user.save();
  }

  const tokens = await createSessionTokens(user, meta);
  await createAuditLog({
    actor: user.id,
    action: "auth.google_login",
    category: "auth",
    status: "success",
    targetUser: user.id,
    sessionId: tokens.sessionId,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
  });

  return {
    user: sanitizeUser(user),
    ...tokens,
  };
};

module.exports = {
  sendOtp,
  verifyOtp,
  register,
  login,
  forgotPassword,
  resetPassword,
  changePassword,
  logout,
  logoutAllDevices,
  revokeCompromisedRefreshToken,
  refreshAuthToken,
  listSessions,
  revokeSession,
  googleLogin,
  switchProfile: async (accountId, profileId, meta = {}) => {
    const profile = await User.findById(profileId);
    if (!profile) throw new ApiError(404, "Profile not found");
    if (!profile.accountId || profile.accountId.toString() !== accountId.toString()) {
      throw new ApiError(403, "Profile does not belong to account");
    }
    const tokens = await createSessionTokens(profile, meta);
    await createAuditLog({
      actor: profile.id,
      action: "auth.switch_profile",
      category: "auth",
      status: "success",
      targetUser: profile.id,
      sessionId: tokens.sessionId,
      ipAddress: meta.ipAddress || null,
      userAgent: meta.userAgent || null,
      details: { profileId: profile.id },
    });
    return { user: sanitizeUser(profile), ...tokens };
  },
};
