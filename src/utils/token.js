const jwt = require("jsonwebtoken");
const crypto = require("crypto");

const { env } = require("../config/env");

const signAccessToken = (accountId, profileId, sessionId = null, jti = crypto.randomUUID()) =>
  jwt.sign({ sub: accountId, pid: profileId, sid: sessionId, jti, type: "access" }, env.jwtAccessSecret, {
    expiresIn: env.jwtAccessExpiresIn,
  });

const signRefreshToken = (sessionId, accountId, profileId, jti = crypto.randomUUID()) =>
  jwt.sign({ sub: accountId, pid: profileId, sid: sessionId, jti, type: "refresh" }, env.jwtRefreshSecret, {
    expiresIn: env.jwtRefreshExpiresIn,
  });

const verifyAccessToken = (token) => jwt.verify(token, env.jwtAccessSecret);
const verifyRefreshToken = (token) => jwt.verify(token, env.jwtRefreshSecret);

const hashToken = (token) => crypto.createHash("sha256").update(token).digest("hex");
const hashOtp = (identity, otp) =>
  crypto.createHmac("sha256", env.otpSecret).update(`${identity}:${otp}`).digest("hex");

module.exports = {
  signAccessToken,
  signRefreshToken,
  verifyAccessToken,
  verifyRefreshToken,
  hashToken,
  hashOtp,
};
