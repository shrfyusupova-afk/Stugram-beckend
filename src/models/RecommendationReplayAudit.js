const mongoose = require("mongoose");

const recommendationReplayAuditSchema = new mongoose.Schema(
  {
    actor: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    replayType: {
      type: String,
      enum: ["single", "bulk"],
      required: true,
      index: true,
    },
    status: {
      type: String,
      enum: ["success", "partial", "failed", "skipped"],
      required: true,
      index: true,
    },
    deadLetterIds: {
      type: [String],
      default: [],
      index: true,
    },
    originalJobIds: {
      type: [String],
      default: [],
      index: true,
    },
    filters: {
      surface: {
        type: String,
        enum: ["feed", "reels", "profiles", null],
        default: null,
      },
      jobName: {
        type: String,
        enum: ["refresh_feed", "refresh_reels", "refresh_profiles", null],
        default: null,
      },
      limit: {
        type: Number,
        default: null,
      },
    },
    affectedSurfaces: {
      type: [String],
      default: [],
    },
    replayedCount: {
      type: Number,
      default: 0,
    },
    skippedCount: {
      type: Number,
      default: 0,
    },
    details: {
      type: mongoose.Schema.Types.Mixed,
      default: null,
    },
    failureReason: {
      type: String,
      default: null,
    },
  },
  { timestamps: true }
);

recommendationReplayAuditSchema.index({ createdAt: -1 });
recommendationReplayAuditSchema.index({ actor: 1, createdAt: -1 });
recommendationReplayAuditSchema.index({ replayType: 1, status: 1, createdAt: -1 });
recommendationReplayAuditSchema.index({ status: 1, createdAt: -1 });
recommendationReplayAuditSchema.index({ replayType: 1, createdAt: -1 });
recommendationReplayAuditSchema.index({ actor: 1, status: 1, createdAt: -1 });
recommendationReplayAuditSchema.index({ actor: 1, replayType: 1, createdAt: -1 });
recommendationReplayAuditSchema.index({ deadLetterIds: 1, createdAt: -1 });
recommendationReplayAuditSchema.index({ originalJobIds: 1, createdAt: -1 });
recommendationReplayAuditSchema.index({ "filters.surface": 1, createdAt: -1 });
recommendationReplayAuditSchema.index({ affectedSurfaces: 1, createdAt: -1 });

module.exports = mongoose.model("RecommendationReplayAudit", recommendationReplayAuditSchema);
