const logger = require("../utils/logger");
const { env } = require("../config/env");

const isEmailIdentity = (identity) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(identity);
const isPhoneIdentity = (identity) => /^\+998\d{9}$/.test(identity);

const getIdentityChannel = (identity) => {
  if (isEmailIdentity(identity)) return "email";
  if (isPhoneIdentity(identity)) return "sms";
  return null;
};

const maskIdentity = (identity) => {
  if (isEmailIdentity(identity)) {
    const [localPart, domain] = identity.split("@");
    const maskedLocalPart =
      localPart.length <= 2 ? `${localPart[0] || "*"}*` : `${localPart.slice(0, 2)}***`;
    return `${maskedLocalPart}@${domain}`;
  }

  if (isPhoneIdentity(identity)) {
    return `${identity.slice(0, 4)}***${identity.slice(-2)}`;
  }

  return "unknown";
};

const buildOtpMessage = (otp) => `Your verification code is ${otp}. It expires in ${env.otpExpiresMinutes} minutes.`;

const buildOtpEmailHtml = (otp) =>
  `<div style="font-family:Arial,sans-serif;line-height:1.5">
    <h2>Your verification code</h2>
    <p>Use the code below to continue:</p>
    <p style="font-size:28px;font-weight:700;letter-spacing:4px">${otp}</p>
    <p>This code expires in ${env.otpExpiresMinutes} minutes.</p>
  </div>`;

const buildDeliveryError = (publicMessage, error) => {
  const normalizedError = new Error(publicMessage);
  normalizedError.cause = error;
  return normalizedError;
};

const logDeliveryAttempt = ({ eventName, identity, channel, provider }) => {
  logger.info(eventName, {
    identity: maskIdentity(identity),
    channel,
    provider,
  });
};

const logDeliverySuccess = ({ eventName, identity, channel, provider }) => {
  logger.info(eventName, {
    identity: maskIdentity(identity),
    channel,
    provider,
  });
};

const logDeliveryFailure = ({ eventName, identity, channel, provider, error, status = null }) => {
  logger.error(eventName, {
    identity: maskIdentity(identity),
    channel,
    provider,
    status,
    message: error?.message || "Unknown delivery error",
  });
};

const sendEmailThroughConfiguredProvider = async ({ identity, subject, text, html, errorLabel = "Email delivery failed" }) => {
  if (env.emailProvider === "resend") {
    if (!env.resendApiKey || !env.resendFromEmail) {
      throw new Error("Resend email delivery is not configured");
    }

    const response = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${env.resendApiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        from: env.resendFromEmail,
        to: [identity],
        subject,
        text,
        html,
      }),
    });

    if (!response.ok) {
      const errorText = await response.text().catch(() => "");
      logDeliveryFailure({
        eventName: "email_delivery_failed",
        identity,
        channel: "email",
        provider: "resend",
        status: response.status,
        error: new Error(errorText.slice(0, 300) || errorLabel),
      });
      throw buildDeliveryError(errorLabel, new Error(errorText.slice(0, 300) || errorLabel));
    }

    return {
      channel: "email",
      provider: "resend",
      delivered: true,
    };
  }

  if (env.emailProvider === "brevo") {
    if (!env.brevoApiKey || !env.brevoFromEmail || !env.brevoFromName) {
      throw new Error("Brevo email delivery is not configured");
    }

    const response = await fetch("https://api.brevo.com/v3/smtp/email", {
      method: "POST",
      headers: {
        "api-key": env.brevoApiKey,
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify({
        sender: {
          email: env.brevoFromEmail,
          name: env.brevoFromName,
        },
        to: [{ email: identity }],
        subject,
        textContent: text || undefined,
        htmlContent: html || undefined,
      }),
    });

    if (!response.ok) {
      const errorText = await response.text().catch(() => "");
      logDeliveryFailure({
        eventName: "email_delivery_failed",
        identity,
        channel: "email",
        provider: "brevo",
        status: response.status,
        error: new Error(errorText.slice(0, 300) || errorLabel),
      });
      throw buildDeliveryError(errorLabel, new Error(errorText.slice(0, 300) || errorLabel));
    }

    return {
      channel: "email",
      provider: "brevo",
      delivered: true,
    };
  }

  throw new Error("Unsupported email delivery provider");
};

const sendWithTwilio = async (identity, otp) => {
  if (!env.twilioAccountSid || !env.twilioAuthToken || !env.twilioFromPhone) {
    throw buildDeliveryError("SMS OTP delivery failed", new Error("Twilio OTP delivery is not configured"));
  }

  const endpoint = `https://api.twilio.com/2010-04-01/Accounts/${encodeURIComponent(env.twilioAccountSid)}/Messages.json`;
  const authToken = Buffer.from(`${env.twilioAccountSid}:${env.twilioAuthToken}`).toString("base64");
  const body = new URLSearchParams({
    To: identity,
    From: env.twilioFromPhone,
    Body: buildOtpMessage(otp),
  });

  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      Authorization: `Basic ${authToken}`,
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body,
  });

  if (!response.ok) {
    const errorText = await response.text().catch(() => "");
    logDeliveryFailure({
      eventName: "otp_send_failed",
      identity,
      channel: "sms",
      provider: "twilio",
      status: response.status,
      error: new Error(errorText.slice(0, 300) || "SMS OTP delivery failed"),
    });
    throw buildDeliveryError("SMS OTP delivery failed", new Error(errorText.slice(0, 300) || "SMS OTP delivery failed"));
  }

  return {
    channel: "sms",
    provider: "twilio",
    delivered: true,
  };
};

const sendWithBrevoSms = async (identity, otp) => {
  if (!env.brevoApiKey || !env.brevoSmsSender) {
    throw buildDeliveryError("SMS OTP delivery failed", new Error("Brevo SMS delivery is not configured"));
  }

  const response = await fetch("https://api.brevo.com/v3/transactionalSMS/sms", {
    method: "POST",
    headers: {
      "api-key": env.brevoApiKey,
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({
      sender: env.brevoSmsSender,
      recipient: identity,
      content: buildOtpMessage(otp),
      type: "transactional",
    }),
  });

  if (!response.ok) {
    const errorText = await response.text().catch(() => "");
    logDeliveryFailure({
      eventName: "otp_send_failed",
      identity,
      channel: "sms",
      provider: "brevo",
      status: response.status,
      error: new Error(errorText.slice(0, 300) || "SMS OTP delivery failed"),
    });
    throw buildDeliveryError("SMS OTP delivery failed", new Error(errorText.slice(0, 300) || "SMS OTP delivery failed"));
  }

  return {
    channel: "sms",
    provider: "brevo",
    delivered: true,
  };
};

const sendOtpSms = async (identity, otp) => {
  if (env.otpProvider === "mock") {
    logDeliveryAttempt({ eventName: "otp_send_attempted", identity, channel: "sms", provider: "mock" });
    console.log(`[MOCK OTP] SMS to ${identity}: ${otp}`);
    logDeliverySuccess({ eventName: "otp_send_success", identity, channel: "sms", provider: "mock" });

    return {
      channel: "sms",
      provider: "mock",
      delivered: false,
    };
  }

  logDeliveryAttempt({ eventName: "otp_send_attempted", identity, channel: "sms", provider: env.smsProvider });

  if (env.smsProvider === "twilio") {
    const result = await sendWithTwilio(identity, otp);
    logDeliverySuccess({ eventName: "otp_send_success", identity, channel: "sms", provider: result.provider });
    return result;
  }

  if (env.smsProvider === "brevo") {
    const result = await sendWithBrevoSms(identity, otp);
    logDeliverySuccess({ eventName: "otp_send_success", identity, channel: "sms", provider: result.provider });
    return result;
  }

  throw buildDeliveryError("SMS OTP delivery failed", new Error("Unsupported SMS OTP provider"));
};

const sendOtpEmail = async (identity, otp) => {
  if (env.otpProvider === "mock") {
    logDeliveryAttempt({ eventName: "otp_send_attempted", identity, channel: "email", provider: "mock" });
    console.log(`[MOCK OTP] Email to ${identity}: ${otp}`);
    logDeliverySuccess({ eventName: "otp_send_success", identity, channel: "email", provider: "mock" });

    return {
      channel: "email",
      provider: "mock",
      delivered: false,
    };
  }

  logDeliveryAttempt({ eventName: "otp_send_attempted", identity, channel: "email", provider: env.emailProvider });
  const result = await sendEmailThroughConfiguredProvider({
    identity,
    subject: "Your verification code",
    text: buildOtpMessage(otp),
    html: buildOtpEmailHtml(otp),
    errorLabel: "Email OTP delivery failed",
  });
  logDeliverySuccess({ eventName: "otp_send_success", identity, channel: "email", provider: result.provider });
  return result;
};

const sendOtpForIdentity = async (identity, otp) => {
  const channel = getIdentityChannel(identity);

  if (!channel) {
    throw buildDeliveryError("OTP delivery failed", new Error("Unsupported OTP identity format"));
  }

  if (env.otpProvider === "sms" && channel !== "sms") {
    throw buildDeliveryError("OTP delivery failed", new Error("OTP delivery is configured only for phone identities"));
  }

  if (env.otpProvider === "email" && channel !== "email") {
    throw buildDeliveryError("OTP delivery failed", new Error("OTP delivery is configured only for email identities"));
  }

  if (channel === "sms") {
    return sendOtpSms(identity, otp);
  }

  return sendOtpEmail(identity, otp);
};

module.exports = {
  isEmailIdentity,
  isPhoneIdentity,
  getIdentityChannel,
  maskIdentity,
  buildDeliveryError,
  sendEmailThroughConfiguredProvider,
  sendOtpSms,
  sendOtpEmail,
  sendOtpForIdentity,
};
