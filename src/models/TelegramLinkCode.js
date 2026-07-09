const mongoose = require("mongoose");

const telegramLinkCodeSchema = new mongoose.Schema(
  {
    code: {
      type: String,
      required: true,
      unique: true,
      index: true,
    },
    chatId: {
      type: String,
      default: null,
    },
    telegramUsername: {
      type: String,
      default: null,
    },
    firstName: {
      type: String,
      default: null,
    },
    phoneNumber: {
      type: String,
      default: null,
    },
    // Short-lived plaintext register OTP handed back to the app instance that
    // created this link code (the code itself is the bearer secret). The
    // authoritative copy is the hashed OtpCode record; this whole document
    // expires with the TTL index below.
    otp: {
      type: String,
      default: null,
    },
    linked: {
      type: Boolean,
      default: false,
    },
    expiresAt: {
      type: Date,
      required: true,
      index: { expires: 0 },
    },
  },
  { timestamps: true }
);

module.exports = mongoose.model("TelegramLinkCode", telegramLinkCodeSchema);
