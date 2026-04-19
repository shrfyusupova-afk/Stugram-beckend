const mongoose = require("mongoose");

const storyCommentSchema = new mongoose.Schema(
  {
    story: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Story",
      required: true,
      index: true,
    },
    author: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    content: {
      type: String,
      required: true,
      trim: true,
      maxlength: 1000,
    },
  },
  { timestamps: true }
);

storyCommentSchema.index({ story: 1, createdAt: -1 });
storyCommentSchema.index({ author: 1, createdAt: -1 });

module.exports = mongoose.model("StoryComment", storyCommentSchema);
