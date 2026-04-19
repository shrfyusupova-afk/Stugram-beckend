const User = require("../models/User");
const { verifyAccessToken } = require("../utils/token");
const { isTokenDenied } = require("../services/redisSecurityService");
const Account = require("../models/Account");

const isTokenIssuedBeforeInvalidation = (payloadIat, tokenInvalidBefore) => {
  if (!payloadIat || !tokenInvalidBefore) {
    return false;
  }

  const tokenIssuedAtMs = payloadIat * 1000;
  const invalidatedAtMs = Math.floor(tokenInvalidBefore.getTime() / 1000) * 1000;
  return tokenIssuedAtMs < invalidatedAtMs;
};

const authenticateSocket = async (socket, next) => {
  try {
    const authToken = socket.handshake.auth?.token;
    const headerToken = socket.handshake.headers.authorization;
    const bearerToken = typeof headerToken === "string" && headerToken.startsWith("Bearer ")
      ? headerToken.slice(7)
      : null;
    const token = authToken || bearerToken;

    if (!token) {
      return next(new Error("Authentication required"));
    }

    const payload = verifyAccessToken(token);
    const isDenied = await isTokenDenied({ jti: payload.jti, tokenType: "access" });
    if (isDenied) {
      return next(new Error("Token has been revoked"));
    }
    const isMultiProfileToken = Boolean(payload.pid);
    const profileId = isMultiProfileToken ? payload.pid : payload.sub;
    const accountId = isMultiProfileToken ? payload.sub : null;

    const user = await User.findById(profileId).select("-passwordHash");

    if (!user) {
      return next(new Error("User not found"));
    }

    const account = accountId ? await Account.findById(accountId) : null;
    const tokenInvalidBefore = account?.tokenInvalidBefore || user.tokenInvalidBefore;
    const isSuspended = account?.isSuspended || user.isSuspended;
    const suspendedUntil = account?.suspendedUntil || user.suspendedUntil;

    if (isSuspended && (!suspendedUntil || suspendedUntil > new Date())) {
      return next(new Error("User account is suspended"));
    }

    if (isTokenIssuedBeforeInvalidation(payload.iat, tokenInvalidBefore)) {
      return next(new Error("Token has been invalidated"));
    }

    socket.user = user;
    socket.account = account;
    socket.auth = payload;
    socket.join(`user:${user.id}`);
    next();
  } catch (_error) {
    next(new Error("Invalid or expired token"));
  }
};

module.exports = { authenticateSocket };
