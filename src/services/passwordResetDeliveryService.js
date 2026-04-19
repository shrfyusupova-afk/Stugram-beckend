const { env } = require("../config/env");
const logger = require("../utils/logger");
const { maskIdentity, sendEmailThroughConfiguredProvider } = require("./otpDeliveryService");

const buildPasswordResetLink = (resetToken) => {
  const deepLinkBase = env.appDeepLinkResetUrl;
  const webBase = env.passwordResetBaseUrl;
  const baseUrl = deepLinkBase || webBase || null;

  if (!baseUrl) {
    return null;
  }

  if (baseUrl.includes("{token}")) {
    return baseUrl.replace("{token}", encodeURIComponent(resetToken));
  }

  const separator = baseUrl.includes("?") ? "&" : "?";
  return `${baseUrl}${separator}token=${encodeURIComponent(resetToken)}`;
};

const buildPasswordResetEmail = (resetToken, context = {}) => {
  const resetLink = buildPasswordResetLink(resetToken);
  const expiryMinutes =
    context.expiresAt instanceof Date ? Math.max(1, Math.ceil((context.expiresAt.getTime() - Date.now()) / 60000)) : 15;

  const linkLine = resetLink
    ? `Open this link to reset your password: ${resetLink}`
    : `Use this reset token to continue: ${resetToken}`;

  return {
    subject: "Reset your password",
    text: `We received a request to reset your password.\n\n${linkLine}\n\nThis link or token expires in ${expiryMinutes} minutes.\nIf you did not request this, you can safely ignore this email.`,
    html: `<div style="font-family:Arial,sans-serif;line-height:1.6">
      <h2>Reset your password</h2>
      <p>We received a request to reset your password.</p>
      ${
        resetLink
          ? `<p><a href="${resetLink}" style="display:inline-block;padding:12px 18px;background:#111827;color:#ffffff;text-decoration:none;border-radius:8px">Reset Password</a></p>
             <p>If the button does not work, use this link:</p>
             <p><a href="${resetLink}">${resetLink}</a></p>`
          : `<p>Use this reset token to continue:</p>
             <p style="font-family:monospace;font-size:16px;word-break:break-all">${resetToken}</p>`
      }
      <p>This link or token expires in ${expiryMinutes} minutes.</p>
      <p>If you did not request this, you can safely ignore this email.</p>
    </div>`,
  };
};

const sendPasswordResetEmail = async (identity, resetToken, context = {}) => {
  const { subject, text, html } = buildPasswordResetEmail(resetToken, context);

  const providerConfigured =
    (env.emailProvider === "resend" && env.resendApiKey && env.resendFromEmail) ||
    (env.emailProvider === "brevo" && env.brevoApiKey && env.brevoFromEmail && env.brevoFromName);

  if (env.nodeEnv !== "production" && !providerConfigured) {
    logger.info("Development password reset email fallback", {
      identity: maskIdentity(identity),
      hasLink: Boolean(buildPasswordResetLink(resetToken)),
    });

    return {
      channel: "email",
      provider: "mock",
      delivered: false,
    };
  }

  return sendEmailThroughConfiguredProvider({
    identity,
    subject,
    text,
    html,
    errorLabel: "Password reset email delivery failed",
  });
};

module.exports = {
  buildPasswordResetLink,
  sendPasswordResetEmail,
};
