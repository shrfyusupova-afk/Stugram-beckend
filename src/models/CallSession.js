const mongoose = require("mongoose");

const callSessionSchema = new mongoose.Schema(
  {
    initiator: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    participants: {
      type: [
        {
          type: mongoose.Schema.Types.ObjectId,
          ref: "User",
          required: true,
        },
      ],
      validate: [
        (value) => Array.isArray(value) && value.length >= 2,
        "Call session must contain at least two participants",
      ],
    },
    conversationId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Conversation",
      default: null,
      index: true,
    },
    groupId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "GroupConversation",
      default: null,
      index: true,
    },
    callType: {
      type: String,
      enum: ["audio", "video"],
      required: true,
      index: true,
    },
    status: {
      type: String,
      enum: ["ringing", "accepted", "declined", "ended", "missed", "cancelled"],
      default: "ringing",
      index: true,
    },
    startedAt: {
      type: Date,
      default: Date.now,
    },
    answeredAt: {
      type: Date,
      default: null,
    },
    endedAt: {
      type: Date,
      default: null,
    },
    lastSignalAt: {
      type: Date,
      default: Date.now,
    },
  },
  { timestamps: true }
);

callSessionSchema.index({ participants: 1, createdAt: -1 });
callSessionSchema.index({ conversationId: 1, createdAt: -1 });
callSessionSchema.index({ groupId: 1, createdAt: -1 });
callSessionSchema.index({ participants: 1, status: 1, createdAt: -1 });

module.exports = mongoose.model("CallSession", callSessionSchema);
