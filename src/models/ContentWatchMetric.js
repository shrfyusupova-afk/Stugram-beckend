const mongoose = require("mongoose");

const contentWatchMetricSchema = new mongoose.Schema(
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
    impressions: {
      type: Number,
      default: 0,
    },
    watchStarts: {
      type: Number,
      default: 0,
    },
    totalWatchMs: {
      type: Number,
      default: 0,
    },
    maxWatchRatio: {
      type: Number,
      default: 0,
    },
    completions: {
      type: Number,
      default: 0,
    },
    rewatches: {
      type: Number,
      default: 0,
    },
    fastSkips: {
      type: Number,
      default: 0,
    },
    lastWatchedAt: {
      type: Date,
      default: null,
    },
  },
  { timestamps: true }
);

contentWatchMetricSchema.index({ user: 1, post: 1 }, { unique: true });
contentWatchMetricSchema.index({ user: 1, lastWatchedAt: -1 });

module.exports = mongoose.model("ContentWatchMetric", contentWatchMetricSchema);
