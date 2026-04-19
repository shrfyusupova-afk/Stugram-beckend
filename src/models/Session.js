const mongoose = require("mongoose");

const sessionSchema = new mongoose.Schema(
  {
    sessionId: {
      type: String,
      required: true,
      unique: true,
      index: true,
    },
    familyId: {
      type: String,
      required: true,
      index: true,
    },
    refreshJti: {
      type: String,
      required: true,
      index: true,
    },
    lastAccessJti: {
      type: String,
      default: null,
    },
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    account: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Account",
      required: false,
      index: true,
    },
    tokenHash: {
      type: String,
      required: true,
      unique: true,
    },
    expiresAt: {
      type: Date,
      required: true,
      index: { expires: 0 },
    },
    isRevoked: {
      type: Boolean,
      default: false,
    },
    revokedReason: {
      type: String,
      default: null,
    },
    replacedBySessionId: {
      type: String,
      default: null,
    },
    userAgent: {
      type: String,
      default: null,
    },
    ipAddress: {
      type: String,
      default: null,
    },
    deviceId: {
      type: String,
      default: null,
      index: true,
    },
    lastUsedAt: {
      type: Date,
      default: Date.now,
    },
    isCompromised: {
      type: Boolean,
      default: false,
    },
    isSuspicious: {
      type: Boolean,
      default: false,
    },
  },
  { timestamps: true }
);

sessionSchema.index({ user: 1, isRevoked: 1, createdAt: -1 });
sessionSchema.index({ user: 1, isRevoked: 1, lastUsedAt: -1 });
sessionSchema.index({ account: 1, isRevoked: 1, lastUsedAt: -1 });

module.exports = mongoose.model("Session", sessionSchema);
