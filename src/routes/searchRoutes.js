const express = require("express");

const searchController = require("../controllers/searchController");
const { requireAuth, optionalAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const {
  searchQuerySchema,
  advancedUserSearchQuerySchema,
  searchHistoryCreateSchema,
  searchHistoryListSchema,
  searchHistoryIdParamSchema,
  searchSuggestionsQuerySchema,
} = require("../validators/searchValidators");

const router = express.Router();

router.post("/history", requireAuth, validate(searchHistoryCreateSchema), searchController.saveSearchHistory);
router.get("/history", requireAuth, validate(searchHistoryListSchema), searchController.getSearchHistory);
router.delete("/history/:historyId", requireAuth, validate(searchHistoryIdParamSchema), searchController.deleteSearchHistoryItem);
router.delete("/history", requireAuth, searchController.clearSearchHistory);
router.get("/suggestions", validate(searchSuggestionsQuerySchema), searchController.getSearchSuggestions);
router.get("/users/advanced", optionalAuth, validate(advancedUserSearchQuerySchema), searchController.searchUsersAdvanced);
router.get("/users", optionalAuth, validate(searchQuerySchema), searchController.searchUsers);
router.get("/posts", validate(searchQuerySchema), searchController.searchPosts);
router.get("/hashtags", validate(searchQuerySchema), searchController.searchHashtags);

module.exports = router;
