const { setupIntegrationTestSuite } = require("../helpers/integration");
const { authHeader, createUser } = require("../helpers/factories");
const { env } = require("../../src/config/env");
const PasswordResetToken = require("../../src/models/PasswordResetToken");

const { getClient } = setupIntegrationTestSuite();

const registerUserViaOtp = async ({
  identity = "register@example.com",
  fullName = "Register User",
  username = "register_user",
  password = "Password123",
} = {}) => {
  const client = getClient();

  const otpResponse = await client.post("/api/v1/auth/send-otp").send({
    identity,
    purpose: "register",
  });

  const otp = otpResponse.body.data.otp;

  await client.post("/api/v1/auth/verify-otp").send({
    identity,
    otp,
    purpose: "register",
  });

  return client.post("/api/v1/auth/register").send({
    identity,
    otp,
    fullName,
    username,
    password,
  });
};

describe("Auth integration", () => {
  const originalEnvSnapshot = {
    nodeEnv: env.nodeEnv,
    otpProvider: env.otpProvider,
    emailProvider: env.emailProvider,
    resendApiKey: env.resendApiKey,
    resendFromEmail: env.resendFromEmail,
  };

  afterEach(() => {
    env.nodeEnv = originalEnvSnapshot.nodeEnv;
    env.otpProvider = originalEnvSnapshot.otpProvider;
    env.emailProvider = originalEnvSnapshot.emailProvider;
    env.resendApiKey = originalEnvSnapshot.resendApiKey;
    env.resendFromEmail = originalEnvSnapshot.resendFromEmail;
    if (global.fetch && global.fetch.mockRestore) {
      global.fetch.mockRestore();
    }
  });

  it("registers, logs in, refreshes token, and logs out", async () => {
    const client = getClient();

    const registerResponse = await registerUserViaOtp();
    expect(registerResponse.statusCode).toBe(201);
    expect(registerResponse.body.data.user.username).toBe("register_user");

    const loginResponse = await client
      .post("/api/v1/auth/login")
      .set("x-device-id", "device-login-1")
      .send({
        identityOrUsername: "register_user",
        password: "Password123",
      });

    expect(loginResponse.statusCode).toBe(200);
    expect(loginResponse.body.data.accessToken).toBeTruthy();
    expect(loginResponse.body.data.refreshToken).toBeTruthy();

    const refreshResponse = await client.post("/api/v1/auth/refresh-token").send({
      refreshToken: loginResponse.body.data.refreshToken,
    });

    expect(refreshResponse.statusCode).toBe(200);
    expect(refreshResponse.body.data.accessToken).toBeTruthy();
    expect(refreshResponse.body.data.refreshToken).toBeTruthy();

    const logoutResponse = await client
      .post("/api/v1/auth/logout")
      .set(authHeader(loginResponse.body.data.accessToken))
      .send({
        refreshToken: loginResponse.body.data.refreshToken,
      });

    expect(logoutResponse.statusCode).toBe(200);
    expect(logoutResponse.body.data.loggedOut).toBe(true);
  });

  it("supports sessions, revoke specific session, forgot/reset password, and change password", async () => {
    const client = getClient();
    await createUser({
      identity: "security@example.com",
      username: "security_user",
      fullName: "Security User",
      password: "Password123",
    });

    const loginOne = await client
      .post("/api/v1/auth/login")
      .set("x-device-id", "device-a")
      .send({
        identityOrUsername: "security_user",
        password: "Password123",
      });

    const loginTwo = await client
      .post("/api/v1/auth/login")
      .set("x-device-id", "device-b")
      .send({
        identityOrUsername: "security_user",
        password: "Password123",
      });

    const sessionsResponse = await client
      .get("/api/v1/auth/sessions")
      .set(authHeader(loginTwo.body.data.accessToken));

    expect(sessionsResponse.statusCode).toBe(200);
    expect(sessionsResponse.body.data.length).toBe(2);

    const otherSession = sessionsResponse.body.data.find(
      (session) => session.sessionId !== loginTwo.body.data.sessionId
    );

    const revokeResponse = await client
      .delete(`/api/v1/auth/sessions/${otherSession.sessionId}`)
      .set(authHeader(loginTwo.body.data.accessToken));

    expect(revokeResponse.statusCode).toBe(200);
    expect(revokeResponse.body.data.revoked).toBe(true);

    const forgotPasswordResponse = await client.post("/api/v1/auth/forgot-password").send({
      identity: "security@example.com",
    });

    expect(forgotPasswordResponse.statusCode).toBe(200);
    expect(forgotPasswordResponse.body.data.resetToken).toBeTruthy();

    const resetPasswordResponse = await client.post("/api/v1/auth/reset-password").send({
      token: forgotPasswordResponse.body.data.resetToken,
      password: "Password456",
    });

    expect(resetPasswordResponse.statusCode).toBe(200);

    const oldPasswordLogin = await client.post("/api/v1/auth/login").send({
      identityOrUsername: "security_user",
      password: "Password123",
    });
    expect(oldPasswordLogin.statusCode).toBe(401);

    const newPasswordLogin = await client.post("/api/v1/auth/login").send({
      identityOrUsername: "security_user",
      password: "Password456",
    });
    expect(newPasswordLogin.statusCode).toBe(200);

    const changePasswordResponse = await client
      .post("/api/v1/auth/change-password")
      .set(authHeader(newPasswordLogin.body.data.accessToken))
      .send({
        currentPassword: "Password456",
        newPassword: "Password789",
      });

    expect(changePasswordResponse.statusCode).toBe(200);

    const afterChangeOldPasswordLogin = await client.post("/api/v1/auth/login").send({
      identityOrUsername: "security_user",
      password: "Password456",
    });
    expect(afterChangeOldPasswordLogin.statusCode).toBe(401);

    const newestPasswordLogin = await client.post("/api/v1/auth/login").send({
      identityOrUsername: "security_user",
      password: "Password789",
    });
    expect(newestPasswordLogin.statusCode).toBe(200);
  });

  it("supports mocked google login", async () => {
    const client = getClient();

    const response = await client.post("/api/v1/auth/google").send({
      idToken: "valid_google_token_12345",
    });

    expect(response.statusCode).toBe(200);
    expect(response.body.data.user.identity).toBe("google@example.com");
    expect(response.body.data.accessToken).toBeTruthy();
  });

  it("returns OTP in non-production mock delivery mode", async () => {
    const client = getClient();
    env.nodeEnv = "test";
    env.otpProvider = "mock";

    const response = await client.post("/api/v1/auth/send-otp").send({
      identity: "otp-mock@example.com",
      purpose: "register",
    });

    expect(response.statusCode).toBe(200);
    expect(response.body.data.otp).toMatch(/^\d{6}$/);
  });

  it("falls back safely in non-production when OTP provider fails", async () => {
    const client = getClient();
    env.nodeEnv = "test";
    env.otpProvider = "email";
    env.emailProvider = "resend";
    env.resendApiKey = "test-resend-key";
    env.resendFromEmail = "no-reply@example.com";
    jest.spyOn(global, "fetch").mockRejectedValue(new Error("provider down"));

    const response = await client.post("/api/v1/auth/send-otp").send({
      identity: "otp-fail@example.com",
      purpose: "register",
    });

    expect(response.statusCode).toBe(200);
    expect(response.body.data.otp).toMatch(/^\d{6}$/);
    expect(global.fetch).toHaveBeenCalled();
  });

  it("returns a safe production error when OTP delivery fails", async () => {
    const client = getClient();
    env.nodeEnv = "production";
    env.otpProvider = "email";
    env.emailProvider = "resend";
    env.resendApiKey = "test-resend-key";
    env.resendFromEmail = "no-reply@example.com";
    jest.spyOn(global, "fetch").mockRejectedValue(new Error("resend provider exploded"));

    const response = await client.post("/api/v1/auth/send-otp").send({
      identity: "otp-prod@example.com",
      purpose: "register",
    });

    expect(response.statusCode).toBe(502);
    expect(response.body.message).toBe("OTP delivery failed. Please try again later.");
    expect(JSON.stringify(response.body)).not.toContain("resend");
    expect(JSON.stringify(response.body)).not.toContain("provider exploded");
  });

  it("falls back to token return in non-production when password reset email delivery fails", async () => {
    const client = getClient();
    env.nodeEnv = "test";
    env.resendApiKey = "test-resend-key";
    env.resendFromEmail = "no-reply@example.com";
    jest.spyOn(global, "fetch").mockRejectedValue(new Error("reset mail down"));

    await createUser({
      identity: "forgot-fallback@example.com",
      username: "forgot_fallback",
      fullName: "Forgot Fallback",
      password: "Password123",
    });

    const response = await client.post("/api/v1/auth/forgot-password").send({
      identity: "forgot-fallback@example.com",
    });

    expect(response.statusCode).toBe(200);
    expect(response.body.data.resetToken).toBeTruthy();
  });

  it("keeps forgot-password production responses generic when delivery fails", async () => {
    const client = getClient();
    env.nodeEnv = "production";
    env.resendApiKey = "test-resend-key";
    env.resendFromEmail = "no-reply@example.com";
    jest.spyOn(global, "fetch").mockRejectedValue(new Error("mail provider outage"));

    await createUser({
      identity: "forgot-prod@example.com",
      username: "forgot_prod",
      fullName: "Forgot Prod",
      password: "Password123",
    });

    const existingUserResponse = await client.post("/api/v1/auth/forgot-password").send({
      identity: "forgot-prod@example.com",
    });
    const missingUserResponse = await client.post("/api/v1/auth/forgot-password").send({
      identity: "missing-prod@example.com",
    });

    expect(existingUserResponse.statusCode).toBe(200);
    expect(existingUserResponse.body.data.resetToken).toBeUndefined();
    expect(missingUserResponse.statusCode).toBe(200);
    expect(missingUserResponse.body.data.resetToken).toBeUndefined();
    expect(existingUserResponse.body.message).toBe("Password reset instructions created");
    expect(missingUserResponse.body.message).toBe("Password reset instructions created");
    expect(await PasswordResetToken.countDocuments({ identity: "forgot-prod@example.com", usedAt: null })).toBe(0);
  });
});
