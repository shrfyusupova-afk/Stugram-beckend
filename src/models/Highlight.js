const mongoose = require("mongoose");

const highlightItemSchema = new mongoose.Schema(
  {
    storyId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Story",
      default: null,
    },
    mediaUrl: {
      type: String,
      required: true,
    },
    thumbnailUrl: {
      type: String,
      default: null,
    },
    mediaType: {
      type: String,
      enum: ["image", "video"],
      required: true,
    },
    publicId: {
      type: String,
      default: null,
    },
    width: {
      type: Number,
      default: null,
    },
    height: {
      type: Number,
      default: null,
    },
    duration: {
      type: Number,
      default: null,
    },
    order: {
      type: Number,
      default: 0,
    },
  },
  { _id: true }
);

const highlightSchema = new mongoose.Schema(
  {
    ownerId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    title: {
      type: String,
      required: true,
      trim: true,
      maxlength: 30,
    },
    coverImageUrl: {
      type: String,
      required: true,
    },
    storyIds: {
      type: [
        {
          type: mongoose.Schema.Types.ObjectId,
          ref: "Story",
        },
      ],
      default: [],
    },
    items: {
      type: [highlightItemSchema],
      default: [],
      validate: {
        validator(value) {
          return Array.isArray(value) && value.length > 0;
        },
        message: "Highlight must contain at least one item",
      },
    },
    isArchived: {
      type: Boolean,
      default: false,
      index: true,
    },
  },
  { timestamps: true }
);

highlightSchema.index({ ownerId: 1, isArchived: 1, createdAt: -1 });

module.exports = mongoose.model("Highlight", highlightSchema);
