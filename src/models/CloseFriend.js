const mongoose = require("mongoose");

const closeFriendSchema = new mongoose.Schema(
  {
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    closeFriend: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
  },
  { timestamps: true }
);

closeFriendSchema.index({ user: 1, closeFriend: 1 }, { unique: true });
closeFriendSchema.index({ user: 1, createdAt: -1 });

module.exports = mongoose.model("CloseFriend", closeFriendSchema);
