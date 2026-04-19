const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const authService = require("../services/authService");

const getRequestMeta = (req) => ({
  userAgent: req.headers["user-agent"],
  ipAddress: req.ip,
  deviceId: req.headers["x-device-id"] || null,
  accessTokenJti: req.auth?.jti || null,
  accessTokenExp: req.auth?.exp || null,
});

const sendOtp = catchAsync(async (req, res) => {
  const result = await authService.sendOtp(req.body, getRequestMeta(req));
  sendResponse(res, { statusCode: 200, message: "OTP sent successfully", data: result });
});

const verifyOtp = catchAsync(async (req, res) => {
  const result = await authService.verifyOtp(req.body);
  sendResponse(res, { statusCode: 200, message: "OTP verified successfully", data: result });
});

const register = catchAsync(async (req, res) => {
  const result = await authService.register(req.body, getRequestMeta(req));
  sendResponse(res, { statusCode: 201, message: "User registered successfully", data: result });
});

const login = catchAsync(async (req, res) => {
  const result = await authService.login(req.body, getRequestMeta(req));
  sendResponse(res, { statusCode: 200, message: "Login successful", data: result });
});

const forgotPassword = catchAsync(async (req, res) => {
  const result = await authService.forgotPassword(req.body.identity, getRequestMeta(req));
  sendResponse(res, { statusCode: 200, message: "Password reset instructions created", data: result });
});

const resetPassword = catchAsync(async (req, res) => {
  const result = await authService.resetPassword(req.body, getRequestMeta(req));
  sendResponse(res, { statusCode: 200, message: "Password reset successful", data: result });
});

const changePassword = catchAsync(async (req, res) => {
  const result = await authService.changePassword(req.account.id, req.user.id, req.body, getRequestMeta(req));
  sendResponse(res, { statusCode: 200, message: "Password changed successfully", data: result });
});

const logout = catchAsync(async (req, res) => {
  const result = await authService.logout(req.body.refreshToken, getRequestMeta(req));
  sendResponse(res, { statusCode: 200, message: "Logout successful", data: result });
});

const logoutAllDevices = catchAsync(async (req, res) => {
  const result = await authService.logoutAllDevices(req.account.id, req.auth?.sid, getRequestMeta(req));
  sendResponse(res, { statusCode: 200, message: "Logged out from all devices", data: result });
});

const revokeRefreshToken = catchAsync(async (req, res) => {
  const result = await authService.revokeCompromisedRefreshToken(req.account.id, req.body.refreshToken, getRequestMeta(req));
  sendResponse(res, { statusCode: 200, message: "Refresh token revoked", data: result });
});

const refreshToken = catchAsync(async (req, res) => {
  const result = await authService.refreshAuthToken(req.body.refreshToken, getRequestMeta(req));
  sendResponse(res, { statusCode: 200, message: "Token refreshed", data: result });
});

const listSessions = catchAsync(async (req, res) => {
  const result = await authService.listSessions(req.account.id, req.auth?.sid || null);
  sendResponse(res, { statusCode: 200, message: "Sessions fetched successfully", data: result });
});

const revokeSession = catchAsync(async (req, res) => {
  const result = await authService.revokeSession(req.account.id, req.params.sessionId, getRequestMeta(req));
  sendResponse(res, { statusCode: 200, message: "Session revoked successfully", data: result });
});

const googleLogin = catchAsync(async (req, res) => {
  const result = await authService.googleLogin(req.body.idToken || "", getRequestMeta(req));
  sendResponse(res, { statusCode: 200, message: "Google login successful", data: result });
});

const switchProfile = catchAsync(async (req, res) => {
  const result = await authService.switchProfile(req.account.id, req.body.profileId, getRequestMeta(req));
  sendResponse(res, { statusCode: 200, message: "Profile switched successfully", data: result });
});

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
  revokeRefreshToken,
  refreshToken,
  listSessions,
  revokeSession,
  googleLogin,
  switchProfile,
};
