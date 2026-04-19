const mongoose = require("mongoose");

const recommendationSnapshotSchema = new mongoose.Schema(
  {
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    surface: {
      type: String,
      enum: ["feed", "reels", "profiles"],
      required: true,
      index: true,
    },
    page: {
      type: Number,
      default: 1,
    },
    limit: {
      type: Number,
      default: 20,
    },
    candidates: {
      type: [
        {
          itemId: { type: mongoose.Schema.Types.ObjectId, default: null },
          score: { type: Number, default: 0 },
          reasons: { type: [String], default: [] },
          exploration: { type: Boolean, default: false },
        },
      ],
      default: [],
    },
    expiresAt: {
      type: Date,
      default: null,
      index: { expires: 0 },
    },
  },
  { timestamps: true }
);

recommendationSnapshotSchema.index({ user: 1, surface: 1, page: 1, createdAt: -1 });

module.exports = mongoose.model("RecommendationSnapshot", recommendationSnapshotSchema);
