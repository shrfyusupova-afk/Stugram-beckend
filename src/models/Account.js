const mongoose = require("mongoose");

const accountSchema = new mongoose.Schema(
  {
    identity: {
      type: String,
      required: true,
      unique: true,
      trim: true,
      lowercase: true,
    },
    passwordHash: {
      type: String,
      default: null,
    },
    googleId: {
      type: String,
      default: null,
      index: true,
    },
    lastLoginAt: {
      type: Date,
      default: null,
    },
    tokenInvalidBefore: {
      type: Date,
      default: null,
    },
    isSuspended: {
      type: Boolean,
      default: false,
    },
    suspendedUntil: {
      type: Date,
      default: null,
    },
    suspensionReason: {
      type: String,
      default: null,
    },
  },
  { timestamps: true }
);

module.exports = mongoose.model("Account", accountSchema);

