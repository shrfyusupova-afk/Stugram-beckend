const mongoose = require("mongoose");

const chatEventSchema = new mongoose.Schema(
  {
    targetType: {
      type: String,
      enum: ["direct", "group"],
      required: true,
      index: true,
    },
    targetId: {
      type: mongoose.Schema.Types.ObjectId,
      required: true,
      index: true,
    },
    sequence: {
      type: Number,
      required: true,
      min: 1,
    },
    type: {
      type: String,
      required: true,
      index: true,
    },
    messageId: {
      type: mongoose.Schema.Types.ObjectId,
      default: null,
      index: true,
    },
    clientId: {
      type: String,
      default: null,
      index: true,
    },
    actorId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      default: null,
      index: true,
    },
    payload: {
      type: mongoose.Schema.Types.Mixed,
      default: {},
    },
  },
  {
    timestamps: { createdAt: true, updatedAt: false },
  }
);

chatEventSchema.index({ targetType: 1, targetId: 1, sequence: 1 }, { unique: true });
chatEventSchema.index({ targetType: 1, targetId: 1, createdAt: -1 });

module.exports = mongoose.model("ChatEvent", chatEventSchema);
