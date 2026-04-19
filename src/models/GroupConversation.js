const mongoose = require("mongoose");

const groupMemberSchema = new mongoose.Schema(
  {
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
    },
    joinedAt: {
      type: Date,
      default: Date.now,
    },
  },
  { _id: false }
);

const groupConversationSchema = new mongoose.Schema(
  {
    name: {
      type: String,
      required: true,
      trim: true,
      maxlength: 120,
    },
    avatar: {
      type: String,
      default: null,
    },
    avatarPublicId: {
      type: String,
      default: null,
    },
    owner: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    members: {
      type: [groupMemberSchema],
      default: [],
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
      ref: "GroupMessage",
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
  },
  { timestamps: true }
);

groupConversationSchema.index({ "members.user": 1, lastMessageAt: -1 });

module.exports = mongoose.model("GroupConversation", groupConversationSchema);
