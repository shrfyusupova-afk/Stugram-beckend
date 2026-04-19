const mongoose = require("mongoose");

const storyLikeSchema = new mongoose.Schema(
  {
    story: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Story",
      required: true,
      index: true,
    },
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
  },
  { timestamps: true }
);

storyLikeSchema.index({ story: 1, user: 1 }, { unique: true });
storyLikeSchema.index({ story: 1, createdAt: -1 });
storyLikeSchema.index({ user: 1, createdAt: -1 });

module.exports = mongoose.model("StoryLike", storyLikeSchema);
