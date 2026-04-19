const mongoose = require("mongoose");

const contentFeatureSchema = new mongoose.Schema(
  {
    post: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Post",
      required: true,
      unique: true,
      index: true,
    },
    creator: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    contentType: {
      type: String,
      enum: ["post", "reel"],
      default: "post",
      index: true,
    },
    topics: {
      type: [String],
      default: [],
      index: true,
    },
    topicWeights: {
      type: Map,
      of: Number,
      default: {},
    },
    language: {
      type: String,
      default: null,
    },
    region: {
      type: String,
      default: null,
      index: true,
    },
    qualityScore: {
      type: Number,
      default: 0.7,
      index: true,
    },
    spamScore: {
      type: Number,
      default: 0,
      index: true,
    },
    popularityScore: {
      type: Number,
      default: 0,
    },
    embeddingVersion: {
      type: Number,
      default: 1,
    },
  },
  { timestamps: true }
);

contentFeatureSchema.index({ contentType: 1, createdAt: -1 });
contentFeatureSchema.index({ creator: 1, contentType: 1, createdAt: -1 });
contentFeatureSchema.index({ topics: 1, contentType: 1 });

module.exports = mongoose.model("ContentFeature", contentFeatureSchema);
