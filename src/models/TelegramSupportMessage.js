const mongoose = require("mongoose");

const telegramSupportMessageSchema = new mongoose.Schema(
  {
    chatId: {
      type: String,
      required: true,
      index: true,
    },
    telegramUsername: {
      type: String,
      default: null,
    },
    firstName: {
      type: String,
      default: null,
    },
    message: {
      type: String,
      required: true,
      trim: true,
      maxlength: 4000,
    },
    status: {
      type: String,
      enum: ["open", "answered"],
      default: "open",
      index: true,
    },
  },
  { timestamps: true }
);

module.exports = mongoose.model("TelegramSupportMessage", telegramSupportMessageSchema);
