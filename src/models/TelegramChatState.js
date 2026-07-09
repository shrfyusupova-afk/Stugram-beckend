const mongoose = require("mongoose");

// Tiny per-chat flag so the webhook (stateless per request) knows whether the
// next free-text message from a chat is a reply to the "contact admin" prompt
// or just noise to ignore.
const telegramChatStateSchema = new mongoose.Schema(
  {
    chatId: {
      type: String,
      required: true,
      unique: true,
    },
    awaitingSupportMessage: {
      type: Boolean,
      default: false,
    },
  },
  { timestamps: true }
);

module.exports = mongoose.model("TelegramChatState", telegramChatStateSchema);
