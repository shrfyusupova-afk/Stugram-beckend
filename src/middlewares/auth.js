const ApiError = require("../utils/ApiError");
const { verifyAccessToken } = require("../utils/token");
const User = require("../models/User");
const Account = require("../models/Account");
const { isTokenDenied } = require("../services/redisSecurityService");

const isTokenIssuedBeforeInvalidation = (payloadIat, tokenInvalidBefore) => {
  if (!payloadIat || !tokenInvalidBefore) {
    return false;
  }

  const tokenIssuedAtMs = payloadIat * 1000;
  const invalidatedAtMs = Math.floor(tokenInvalidBefore.getTime() / 1000) * 1000;
  return tokenIssuedAtMs < invalidatedAtMs;
};

const authenticateRequest = async (req, { allowMissingToken = false } = {}) => {
  const authHeader = req.headers.authorization || "";
  const token = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : null;

  if (!token) {
    if (allowMissingToken) {
      return false;
    }
    throw new ApiError(401, "Authentication required");
  }

  const payload = verifyAccessToken(token);
  const isDenied = await isTokenDenied({ jti: payload.jti, tokenType: "access" });
  if (isDenied) {
    throw new ApiError(401, "Token has been revoked");
  }

  // Multi-profile tokens carry:
  // - sub: accountId
  // - pid: profileId
  // Legacy tokens carry:
  // - sub: profileId
  const isMultiProfileToken = Boolean(payload.pid);

  let account = null;
  let user = null;

  if (isMultiProfileToken) {
    account = await Account.findById(payload.sub);
    if (!account) throw new ApiError(401, "Account not found");
    user = await User.findById(payload.pid).select("-passwordHash");
    if (!user) throw new ApiError(401, "Profile not found");
    if (user.accountId && user.accountId.toString() !== account.id.toString()) {
      throw new ApiError(401, "Profile does not belong to account");
    }
  } else {
    user = await User.findById(payload.sub).select("-passwordHash");
    if (!user) throw new ApiError(401, "User not found");
    // Auto-migrate: attach an Account if missing
    if (!user.accountId) {
      if (!user.identity) {
        throw new ApiError(401, "Account identity is missing");
      }
      account = await Account.findOne({ identity: user.identity.toLowerCase() });
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
    } else {
      account = await Account.findById(user.accountId);
    }
  }

  if (!account) {
    throw new ApiError(401, "Account not found");
  }

  if (account.isSuspended && (!account.suspendedUntil || account.suspendedUntil > new Date())) {
    throw new ApiError(403, "User account is suspended");
  }

  if (isTokenIssuedBeforeInvalidation(payload.iat, account.tokenInvalidBefore)) {
    throw new ApiError(401, "Token has been invalidated");
  }

  req.user = user;
  req.account = account;
  req.auth = payload;
  return true;
};

const requireAuth = async (req, _res, next) => {
  try {
    await authenticateRequest(req);
    next();
  } catch (error) {
    next(error instanceof ApiError ? error : new ApiError(401, "Invalid or expired token"));
  }
};

const optionalAuth = async (req, _res, next) => {
  try {
    await authenticateRequest(req, { allowMissingToken: true });
    next();
  } catch (error) {
    next(error instanceof ApiError ? error : new ApiError(401, "Invalid or expired token"));
  }
};

module.exports = { requireAuth, optionalAuth };
