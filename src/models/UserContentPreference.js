const mongoose = require("mongoose");

const userContentPreferenceSchema = new mongoose.Schema(
  {
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    post: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Post",
      required: true,
      index: true,
    },
    creator: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      default: null,
      index: true,
    },
    preferenceType: {
      type: String,
      enum: ["hide", "not_interested", "seen", "saved", "liked"],
      required: true,
      index: true,
    },
    source: {
      type: String,
      enum: ["feed", "reels", "profiles", "system"],
      default: "system",
    },
    expiresAt: {
      type: Date,
      default: null,
    },
    metadata: {
      type: mongoose.Schema.Types.Mixed,
      default: null,
    },
  },
  { timestamps: true }
);

userContentPreferenceSchema.index({ user: 1, post: 1, preferenceType: 1 }, { unique: true });
userContentPreferenceSchema.index({ user: 1, preferenceType: 1, createdAt: -1 });

module.exports = mongoose.model("UserContentPreference", userContentPreferenceSchema);
