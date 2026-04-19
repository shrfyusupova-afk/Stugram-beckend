const mongoose = require("mongoose");

const settingsSchema = new mongoose.Schema(
  {
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      unique: true,
      index: true,
    },
    isPrivateAccount: { type: Boolean, default: false },
    isDarkMode: { type: Boolean, default: true },
    readReceipts: { type: Boolean, default: true },
    dataSaver: { type: Boolean, default: false },
    videoAutoPlay: { type: Boolean, default: true },
    sensitiveFilter: { type: Boolean, default: false },
    language: { type: String, default: "en" },
    notifications: {
      likes: { type: Boolean, default: true },
      comments: { type: Boolean, default: true },
      replies: { type: Boolean, default: true },
      messages: { type: Boolean, default: true },
      mentions: { type: Boolean, default: true },
      system: { type: Boolean, default: true },
      follows: { type: Boolean, default: true },
      followRequests: { type: Boolean, default: true },
      followAccepts: { type: Boolean, default: true },
    },
    hiddenWords: {
      words: {
        type: [String],
        default: [],
      },
      hideComments: { type: Boolean, default: true },
      hideMessages: { type: Boolean, default: true },
      hideStoryReplies: { type: Boolean, default: true },
    },
  },
  { timestamps: true }
);

module.exports = mongoose.model("Settings", settingsSchema);
