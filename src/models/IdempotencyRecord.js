const mongoose = require("mongoose");

const idempotencyRecordSchema = new mongoose.Schema(
  {
    keyHash: {
      type: String,
      required: true,
      unique: true,
      index: true,
    },
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    method: {
      type: String,
      required: true,
      uppercase: true,
    },
    path: {
      type: String,
      required: true,
    },
    status: {
      type: String,
      enum: ["processing", "completed"],
      default: "processing",
      index: true,
    },
    responseStatus: {
      type: Number,
      default: null,
    },
    responseBody: {
      type: mongoose.Schema.Types.Mixed,
      default: null,
    },
    expiresAt: {
      type: Date,
      required: true,
      index: { expires: 0 },
    },
  },
  { timestamps: true }
);

module.exports = mongoose.model("IdempotencyRecord", idempotencyRecordSchema);
