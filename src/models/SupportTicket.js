const mongoose = require("mongoose");

const supportTicketSchema = new mongoose.Schema(
  {
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    category: {
      type: String,
      enum: ["bug", "payment", "account", "chat", "notifications", "content", "other"],
      required: true,
      index: true,
    },
    subject: {
      type: String,
      required: true,
      trim: true,
      maxlength: 160,
    },
    description: {
      type: String,
      required: true,
      trim: true,
      maxlength: 4000,
    },
    screenshot: {
      url: {
        type: String,
        default: null,
      },
      publicId: {
        type: String,
        default: null,
      },
    },
    status: {
      type: String,
      enum: ["open", "reviewing", "resolved", "rejected"],
      default: "open",
      index: true,
    },
    assignedTo: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      default: null,
      index: true,
    },
    assignedAt: {
      type: Date,
      default: null,
    },
    internalNotes: [
      {
        author: {
          type: mongoose.Schema.Types.ObjectId,
          ref: "User",
          required: true,
        },
        note: {
          type: String,
          required: true,
          trim: true,
          maxlength: 2000,
        },
        createdAt: {
          type: Date,
          default: Date.now,
        },
      },
    ],
    appVersion: {
      type: String,
      default: null,
      trim: true,
      maxlength: 50,
    },
    deviceInfo: {
      type: String,
      default: null,
      trim: true,
      maxlength: 2000,
    },
  },
  { timestamps: true }
);

supportTicketSchema.index({ user: 1, createdAt: -1 });
supportTicketSchema.index({ user: 1, status: 1, createdAt: -1 });
supportTicketSchema.index({ status: 1, category: 1, createdAt: -1 });
supportTicketSchema.index({ assignedTo: 1, status: 1, createdAt: -1 });

module.exports = mongoose.model("SupportTicket", supportTicketSchema);
