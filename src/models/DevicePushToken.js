const mongoose = require("mongoose");

const devicePushTokenSchema = new mongoose.Schema(
  {
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    token: {
      type: String,
      required: true,
      trim: true,
    },
    platform: {
      type: String,
      enum: ["android", "ios", "web"],
      required: true,
      index: true,
    },
    deviceId: {
      type: String,
      required: true,
      trim: true,
      index: true,
    },
    appVersion: {
      type: String,
      default: null,
      trim: true,
    },
    isActive: {
      type: Boolean,
      default: true,
      index: true,
    },
    lastSeenAt: {
      type: Date,
      default: Date.now,
    },
    lastFailureReason: {
      type: String,
      default: null,
      trim: true,
      maxlength: 255,
    },
    lastFailureAt: {
      type: Date,
      default: null,
    },
  },
  { timestamps: true }
);

devicePushTokenSchema.index({ token: 1 }, { unique: true });
devicePushTokenSchema.index({ user: 1, deviceId: 1 }, { unique: true });
devicePushTokenSchema.index({ user: 1, isActive: 1, updatedAt: -1 });

module.exports = mongoose.model("DevicePushToken", devicePushTokenSchema);
