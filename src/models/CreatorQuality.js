const mongoose = require("mongoose");

const creatorQualitySchema = new mongoose.Schema(
  {
    creator: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      unique: true,
      index: true,
    },
    qualityScore: {
      type: Number,
      default: 0.8,
      index: true,
    },
    spamPenalty: {
      type: Number,
      default: 0,
    },
    abusePenalty: {
      type: Number,
      default: 0,
    },
    suspiciousEngagementPenalty: {
      type: Number,
      default: 0,
    },
    reportRate: {
      type: Number,
      default: 0,
    },
    hideRate: {
      type: Number,
      default: 0,
    },
    lastComputedAt: {
      type: Date,
      default: Date.now,
    },
  },
  { timestamps: true }
);

module.exports = mongoose.model("CreatorQuality", creatorQualitySchema);
