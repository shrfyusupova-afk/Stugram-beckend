const mongoose = require("mongoose");

const recommendationEventSchema = new mongoose.Schema(
  {
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    eventType: {
      type: String,
      required: true,
      enum: [
        "feed_impression",
        "reel_impression",
        "watch_start",
        "watch_progress",
        "watch_complete",
        "like",
        "save",
        "share",
        "comment",
        "profile_visit",
        "follow",
        "caption_open",
        "profile_open",
        "sound_on",
        "hide",
        "not_interested",
        "block",
        "report",
        "fast_skip",
        "rewatch",
      ],
      index: true,
    },
    surface: {
      type: String,
      enum: ["feed", "reels", "profiles", "profile", "post", "system"],
      default: "system",
      index: true,
    },
    content: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Post",
      default: null,
      index: true,
    },
    creator: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      default: null,
      index: true,
    },
    targetProfile: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      default: null,
      index: true,
    },
    sessionId: {
      type: String,
      default: null,
      index: true,
    },
    requestId: {
      type: String,
      default: null,
      index: true,
    },
    sourceImpressionId: {
      type: String,
      default: null,
      index: true,
    },
    topics: {
      type: [String],
      default: [],
      index: true,
    },
    watchMetrics: {
      durationMs: { type: Number, default: 0 },
      progressMs: { type: Number, default: 0 },
      watchRatio: { type: Number, default: 0 },
      rewatchCount: { type: Number, default: 0 },
      dwellMs: { type: Number, default: 0 },
      completed: { type: Boolean, default: false },
      soundOn: { type: Boolean, default: false },
    },
    metadata: {
      type: mongoose.Schema.Types.Mixed,
      default: null,
    },
  },
  { timestamps: true }
);

recommendationEventSchema.index({ user: 1, createdAt: -1 });
recommendationEventSchema.index({ user: 1, eventType: 1, createdAt: -1 });
recommendationEventSchema.index({ content: 1, eventType: 1, createdAt: -1 });

module.exports = mongoose.model("RecommendationEvent", recommendationEventSchema);
