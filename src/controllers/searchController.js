const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const searchService = require("../services/searchService");

const searchUsers = catchAsync(async (req, res) => {
  const result = await searchService.searchUsers(req.query, req.user?.id || null);
  sendResponse(res, { message: "Users search completed", data: result.items, meta: result.meta });
});

const searchUsersAdvanced = catchAsync(async (req, res) => {
  const result = await searchService.searchUsersAdvanced(req.query, req.user?.id || null);
  sendResponse(res, {
    message: "Advanced users search completed",
    data: result.items,
    meta: result.meta,
  });
});

const searchPosts = catchAsync(async (req, res) => {
  const result = await searchService.searchPosts(req.query, req.user?.id || null);
  sendResponse(res, { message: "Posts search completed", data: result.items, meta: result.meta });
});

const searchHashtags = catchAsync(async (req, res) => {
  const result = await searchService.searchHashtags(req.query);
  sendResponse(res, { message: "Hashtags search completed", data: result.items, meta: result.meta });
});

const getSearchSuggestions = catchAsync(async (req, res) => {
  const result = await searchService.getSearchSuggestions(req.query, req.user?.id || null);
  sendResponse(res, {
    message: "Search suggestions fetched successfully",
    data: result,
  });
});

const saveSearchHistory = catchAsync(async (req, res) => {
  const item = await searchService.saveSearchHistory(req.user.id, req.body);
  sendResponse(res, {
    statusCode: 201,
    message: "Search history saved successfully",
    data: item,
  });
});

const getSearchHistory = catchAsync(async (req, res) => {
  const result = await searchService.getSearchHistory(req.user.id, req.query);
  sendResponse(res, {
    message: "Search history fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const deleteSearchHistoryItem = catchAsync(async (req, res) => {
  const result = await searchService.deleteSearchHistoryItem(req.user.id, req.params.historyId);
  sendResponse(res, {
    message: "Search history item deleted successfully",
    data: result,
  });
});

const clearSearchHistory = catchAsync(async (req, res) => {
  const result = await searchService.clearSearchHistory(req.user.id);
  sendResponse(res, {
    message: "Search history cleared successfully",
    data: result,
  });
});

module.exports = {
  searchUsers,
  searchUsersAdvanced,
  searchPosts,
  searchHashtags,
  getSearchSuggestions,
  saveSearchHistory,
  getSearchHistory,
  deleteSearchHistoryItem,
  clearSearchHistory,
};
