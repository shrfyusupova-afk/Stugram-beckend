const mongoose = require("mongoose");

const otpCodeSchema = new mongoose.Schema(
  {
    identity: {
      type: String,
      required: true,
      index: true,
      lowercase: true,
      trim: true,
    },
    codeHash: {
      type: String,
      required: true,
    },
    purpose: {
      type: String,
      enum: ["register", "login"],
      default: "register",
    },
    isVerified: {
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

module.exports = mongoose.model("OtpCode", otpCodeSchema);
