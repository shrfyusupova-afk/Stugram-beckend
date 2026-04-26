const mongoose = require("mongoose");

const mediaSchema = new mongoose.Schema(
  {
    url: { type: String, required: true },
    publicId: { type: String, default: null },
    type: { type: String, enum: ["image", "video", "voice", "round_video", "file"], required: true },
    fileName: { type: String, default: null },
    fileSize: { type: Number, default: null, min: 0 },
    mimeType: { type: String, default: null },
    durationSeconds: { type: Number, default: null, min: 0 },
  },
  { _id: false }
);

const metadataSchema = new mongoose.Schema(
  {
    kind: {
      type: String,
      enum: ["post_share", "reel_share", "music", "file", "location"],
      default: null,
    },
    payload: {
      type: mongoose.Schema.Types.Mixed,
      default: null,
    },
  },
  { _id: false }
);

const reactionSchema = new mongoose.Schema(
  {
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
    },
    emoji: {
      type: String,
      required: true,
      maxlength: 20,
    },
  },
  { _id: false }
);

const seenByRecordSchema = new mongoose.Schema(
  {
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
    },
    seenAt: {
      type: Date,
      required: true,
      default: Date.now,
    },
  },
  { _id: false }
);

const groupMessageSchema = new mongoose.Schema(
  {
    groupConversation: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "GroupConversation",
      required: true,
      index: true,
    },
    sender: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    clientId: {
      type: String,
      trim: true,
      maxlength: 128,
      default: null,
      index: true,
    },
    text: {
      type: String,
      default: "",
      maxlength: 2000,
    },
    messageType: {
      type: String,
      enum: ["text", "image", "video", "voice", "round_video", "file"],
      default: "text",
      index: true,
    },
    media: {
      type: mediaSchema,
      default: null,
    },
    metadata: {
      type: metadataSchema,
      default: null,
    },
    replyToMessage: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "GroupMessage",
      default: null,
      index: true,
    },
    forwardedFromMessageId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "GroupMessage",
      default: null,
      index: true,
    },
    forwardedFromSenderId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      default: null,
    },
    forwardedFromConversationId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "GroupConversation",
      default: null,
    },
    forwardedAt: {
      type: Date,
      default: null,
      index: true,
    },
    editedAt: {
      type: Date,
      default: null,
      index: true,
    },
    editedBy: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      default: null,
    },
    reactions: {
      type: [reactionSchema],
      default: [],
    },
    seenBy: {
      type: [
        {
          type: mongoose.Schema.Types.ObjectId,
          ref: "User",
        },
      ],
      default: [],
    },
    seenByRecords: {
      type: [seenByRecordSchema],
      default: [],
    },
    deletedFor: {
      type: [
        {
          type: mongoose.Schema.Types.ObjectId,
          ref: "User",
        },
      ],
      default: [],
    },
    isDeletedForEveryone: {
      type: Boolean,
      default: false,
      index: true,
    },
    deletedForEveryoneAt: {
      type: Date,
      default: null,
      index: true,
    },
    deletedForEveryoneBy: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      default: null,
    },
    deliveredAt: {
      type: Date,
      default: null,
      index: true,
    },
  },
  { timestamps: true }
);

groupMessageSchema.index({ groupConversation: 1, createdAt: -1 });
groupMessageSchema.index({ groupConversation: 1, deletedFor: 1, createdAt: -1 });
groupMessageSchema.index(
  { groupConversation: 1, sender: 1, clientId: 1 },
  { unique: true, partialFilterExpression: { clientId: { $type: "string" } } }
);

module.exports = mongoose.model("GroupMessage", groupMessageSchema);
