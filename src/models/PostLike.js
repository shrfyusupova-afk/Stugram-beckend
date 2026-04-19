const mongoose = require("mongoose");

const postLikeSchema = new mongoose.Schema(
  {
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
    },
    post: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Post",
      required: true,
    },
  },
  { timestamps: true }
);

postLikeSchema.index({ user: 1, post: 1 }, { unique: true });
postLikeSchema.index({ user: 1, createdAt: -1 });
postLikeSchema.index({ post: 1, createdAt: -1 });

module.exports = mongoose.model("PostLike", postLikeSchema);
