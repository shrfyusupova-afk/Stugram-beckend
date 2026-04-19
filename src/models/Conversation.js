const mongoose = require("mongoose");

const mutedBySchema = new mongoose.Schema(
  {
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
    },
    mutedUntil: {
      type: Date,
      default: null,
    },
  },
  { _id: false }
);

const conversationSchema = new mongoose.Schema(
  {
    participants: {
      type: [
        {
          type: mongoose.Schema.Types.ObjectId,
          ref: "User",
          required: true,
        },
      ],
      validate: [
        (value) => Array.isArray(value) && value.length === 2,
        "Conversation must contain exactly two participants",
      ],
    },
    participantsKey: {
      type: String,
      required: true,
      unique: true,
      select: false,
    },
    lastMessage: {
      type: String,
      default: "",
      maxlength: 500,
    },
    lastMessageAt: {
      type: Date,
      default: Date.now,
      index: true,
    },
    pinnedMessage: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Message",
      default: null,
      index: true,
    },
    pinnedBy: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      default: null,
    },
    pinnedAt: {
      type: Date,
      default: null,
      index: true,
    },
    createdBy: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
    },
    mutedBy: {
      type: [mutedBySchema],
      default: [],
    },
  },
  { timestamps: true }
);

conversationSchema.pre("validate", function preValidate(next) {
  const uniqueParticipants = [...new Set(this.participants.map((participant) => participant.toString()))].sort();
  this.participants = uniqueParticipants;
  this.participantsKey = uniqueParticipants.join(":");
  next();
});

conversationSchema.index({ participants: 1, lastMessageAt: -1 });

module.exports = mongoose.model("Conversation", conversationSchema);
