const mongoose = require("mongoose");

const mediaSchema = new mongoose.Schema(
  {
    url: { type: String, required: true },
    publicId: { type: String, required: true },
    type: { type: String, enum: ["image", "video"], required: true },
    width: { type: Number, default: null },
    height: { type: Number, default: null },
    duration: { type: Number, default: null },
  },
  { _id: false }
);

const postSchema = new mongoose.Schema(
  {
    author: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    media: {
      type: [mediaSchema],
      validate: [(value) => value.length > 0, "At least one media item is required"],
    },
    caption: {
      type: String,
      default: "",
      maxlength: 2200,
    },
    hashtags: {
      type: [String],
      default: [],
      index: true,
    },
    location: {
      type: String,
      default: "",
      maxlength: 150,
    },
    likesCount: {
      type: Number,
      default: 0,
    },
    commentsCount: {
      type: Number,
      default: 0,
    },
    isEdited: {
      type: Boolean,
      default: false,
    },
    isHiddenByAdmin: {
      type: Boolean,
      default: false,
      index: true,
    },
    hiddenByAdminAt: {
      type: Date,
      default: null,
    },
    hiddenByAdminReason: {
      type: String,
      default: "",
      maxlength: 300,
    },
  },
  { timestamps: true }
);

postSchema.index({ author: 1, createdAt: -1 });
postSchema.index({ caption: "text", hashtags: "text", location: "text" });

module.exports = mongoose.model("Post", postSchema);
