const mongoose = require("mongoose");

const searchHistorySchema = new mongoose.Schema(
  {
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    queryText: {
      type: String,
      required: true,
      trim: true,
      maxlength: 120,
    },
    searchType: {
      type: String,
      enum: ["user", "keyword", "hashtag"],
      required: true,
      index: true,
    },
    targetId: {
      type: mongoose.Schema.Types.ObjectId,
      default: null,
      index: true,
    },
  },
  { timestamps: true }
);

searchHistorySchema.index({ user: 1, createdAt: -1 });
searchHistorySchema.index({ user: 1, updatedAt: -1, createdAt: -1 });
searchHistorySchema.index({ user: 1, queryText: 1 });
searchHistorySchema.index({ user: 1, targetId: 1 });
searchHistorySchema.index({ user: 1, searchType: 1, queryText: 1 }, { unique: true, partialFilterExpression: { targetId: null } });
searchHistorySchema.index({ user: 1, searchType: 1, targetId: 1 }, { unique: true, sparse: true });

module.exports = mongoose.model("SearchHistory", searchHistorySchema);
