const mongoose = require("mongoose");

const userReportSchema = new mongoose.Schema(
  {
    reporter: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    reportedUser: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    conversation: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Conversation",
      default: null,
    },
    message: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Message",
      default: null,
    },
    reason: {
      type: String,
      enum: ["spam", "harassment", "nudity", "violence", "scam", "other"],
      required: true,
    },
    details: {
      type: String,
      default: "",
      maxlength: 1000,
    },
    status: {
      type: String,
      enum: ["open", "reviewing", "resolved", "rejected"],
      default: "open",
    },
    reviewNotes: {
      type: String,
      default: "",
      maxlength: 1000,
    },
    reviewedBy: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      default: null,
    },
    reviewedAt: {
      type: Date,
      default: null,
    },
    actionTaken: {
      type: String,
      enum: ["none", "warned", "suspended", "banned"],
      default: "none",
    },
  },
  { timestamps: true }
);

module.exports = mongoose.model("UserReport", userReportSchema);
