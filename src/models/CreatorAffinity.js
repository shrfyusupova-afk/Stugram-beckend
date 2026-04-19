const mongoose = require("mongoose");

const creatorAffinitySchema = new mongoose.Schema(
  {
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    creator: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    score: {
      type: Number,
      default: 0,
    },
    watchScore: {
      type: Number,
      default: 0,
    },
    engagementScore: {
      type: Number,
      default: 0,
    },
    visitScore: {
      type: Number,
      default: 0,
    },
    followScore: {
      type: Number,
      default: 0,
    },
    negativeScore: {
      type: Number,
      default: 0,
    },
    lastInteractionAt: {
      type: Date,
      default: null,
      index: true,
    },
  },
  { timestamps: true }
);

creatorAffinitySchema.index({ user: 1, creator: 1 }, { unique: true });
creatorAffinitySchema.index({ user: 1, score: -1 });

module.exports = mongoose.model("CreatorAffinity", creatorAffinitySchema);
