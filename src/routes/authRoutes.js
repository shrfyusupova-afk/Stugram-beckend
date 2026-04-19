const express = require("express");

const authController = require("../controllers/authController");
const { requireAuth } = require("../middlewares/auth");
const { authLimiter } = require("../middlewares/rateLimiter");
const validate = require("../middlewares/validate");
const {
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
} = require("../validators/authValidators");

const router = express.Router();

router.post("/send-otp", authLimiter, validate(sendOtpSchema), authController.sendOtp);
router.post("/verify-otp", authLimiter, validate(verifyOtpSchema), authController.verifyOtp);
router.post("/register", authLimiter, validate(registerSchema), authController.register);
router.post("/login", authLimiter, validate(loginSchema), authController.login);
router.post("/forgot-password", authLimiter, validate(forgotPasswordSchema), authController.forgotPassword);
router.post("/reset-password", authLimiter, validate(resetPasswordSchema), authController.resetPassword);
router.post("/change-password", requireAuth, authLimiter, validate(changePasswordSchema), authController.changePassword);
router.post("/logout", requireAuth, authLimiter, validate(logoutSchema), authController.logout);
router.post("/logout-all", requireAuth, authController.logoutAllDevices);
router.post("/revoke-refresh-token", requireAuth, validate(refreshTokenSchema), authController.revokeRefreshToken);
router.post("/refresh-token", authLimiter, validate(refreshTokenSchema), authController.refreshToken);
router.get("/sessions", requireAuth, authController.listSessions);
router.delete("/sessions/:sessionId", requireAuth, validate(sessionIdParamSchema), authController.revokeSession);
router.post("/switch-profile", requireAuth, validate(switchProfileSchema), authController.switchProfile);
router.post("/google", authLimiter, validate(googleLoginSchema), authController.googleLogin);

module.exports = router;
