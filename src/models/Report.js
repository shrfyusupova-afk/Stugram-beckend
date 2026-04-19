const mongoose = require("mongoose");

const reportSchema = new mongoose.Schema(
  {
    reporterId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    targetType: {
      type: String,
      enum: ["post", "comment", "user"],
      required: true,
      index: true,
    },
    targetId: {
      type: mongoose.Schema.Types.ObjectId,
      required: true,
      index: true,
    },
    reason: {
      type: String,
      enum: ["spam", "harassment", "nudity", "violence", "scam", "hate", "misinformation", "other"],
      required: true,
      index: true,
    },
    details: {
      type: String,
      default: "",
      maxlength: 1000,
    },
    status: {
      type: String,
      enum: ["open", "resolved"],
      default: "open",
      index: true,
    },
    resolvedBy: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      default: null,
    },
    resolvedAt: {
      type: Date,
      default: null,
    },
    resolutionNote: {
      type: String,
      default: "",
      maxlength: 1000,
    },
  },
  { timestamps: true }
);

reportSchema.index({ targetType: 1, targetId: 1, createdAt: -1 });
reportSchema.index({ reporterId: 1, createdAt: -1 });

module.exports = mongoose.model("Report", reportSchema);
