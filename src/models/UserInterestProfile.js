const mongoose = require("mongoose");

const userInterestProfileSchema = new mongoose.Schema(
  {
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      unique: true,
      index: true,
    },
    interestScores: {
      type: Map,
      of: Number,
      default: {},
    },
    onboardingTopics: {
      type: [String],
      default: [],
    },
    negativeTopicScores: {
      type: Map,
      of: Number,
      default: {},
    },
    localePreferences: {
      type: [String],
      default: [],
    },
    explorationRate: {
      type: Number,
      default: 0.15,
    },
    confidenceScore: {
      type: Number,
      default: 0.1,
    },
    lastEventAt: {
      type: Date,
      default: null,
    },
  },
  { timestamps: true }
);

module.exports = mongoose.model("UserInterestProfile", userInterestProfileSchema);
