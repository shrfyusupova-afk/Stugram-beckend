const mongoose = require("mongoose");

const profileSuggestionFeatureSchema = new mongoose.Schema(
  {
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    candidateProfile: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    mutualFollowScore: {
      type: Number,
      default: 0,
    },
    interestSimilarityScore: {
      type: Number,
      default: 0,
    },
    audienceOverlapScore: {
      type: Number,
      default: 0,
    },
    visitIntentScore: {
      type: Number,
      default: 0,
    },
    creatorSimilarityScore: {
      type: Number,
      default: 0,
    },
    localityScore: {
      type: Number,
      default: 0,
    },
    popularityScore: {
      type: Number,
      default: 0,
    },
    qualityScore: {
      type: Number,
      default: 0.7,
    },
    finalScore: {
      type: Number,
      default: 0,
      index: true,
    },
    snapshotAt: {
      type: Date,
      default: Date.now,
    },
  },
  { timestamps: true }
);

profileSuggestionFeatureSchema.index({ user: 1, candidateProfile: 1 }, { unique: true });
profileSuggestionFeatureSchema.index({ user: 1, finalScore: -1 });

module.exports = mongoose.model("ProfileSuggestionFeature", profileSuggestionFeatureSchema);
