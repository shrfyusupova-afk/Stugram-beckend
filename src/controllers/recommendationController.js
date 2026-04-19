const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const recommendationService = require("../services/recommendationService");
const profileSuggestionService = require("../services/profileSuggestionService");
const interactionTrackingService = require("../services/interactionTrackingService");
const mobileSummaryService = require("../services/mobileSummaryService");

const getMyFeed = catchAsync(async (req, res) => {
  const result = await recommendationService.getPersonalizedFeed(req.user.id, req.query, { surface: "feed" });
  sendResponse(res, { message: "Feed fetched successfully", data: result.items, meta: result.meta });
});

const getFeedSummary = catchAsync(async (req, res) => {
  const result = await mobileSummaryService.getHomeFeedSummary(req.user.id);
  sendResponse(res, { message: "Feed summary fetched successfully", data: result });
});

const getMyReels = catchAsync(async (req, res) => {
  const result = await recommendationService.getPersonalizedFeed(req.user.id, req.query, { surface: "reels" });
  sendResponse(res, { message: "Reels fetched successfully", data: result.items, meta: result.meta });
});

const getProfileSuggestions = catchAsync(async (req, res) => {
  const result = await profileSuggestionService.getProfileSuggestions(req.user.id, req.query);
  sendResponse(res, { message: "Profile suggestions fetched successfully", data: result.items, meta: result.meta });
});

const trackImpression = catchAsync(async (req, res) => {
  const result = await interactionTrackingService.trackImpression(req.user.id, req.body);
  sendResponse(res, { statusCode: 201, message: "Impression tracked successfully", data: result });
});

const trackWatchProgress = catchAsync(async (req, res) => {
  const result = await interactionTrackingService.trackWatchProgress(req.user.id, req.body);
  sendResponse(res, { statusCode: 201, message: "Watch progress tracked successfully", data: result });
});

const markNotInterested = catchAsync(async (req, res) => {
  const result = await interactionTrackingService.markNotInterested(req.user.id, req.body);
  sendResponse(res, { statusCode: 201, message: "Content preference saved successfully", data: result });
});

const seedOnboardingTopics = catchAsync(async (req, res) => {
  const result = await interactionTrackingService.seedOnboardingTopics(req.user.id, req.body.topics);
  sendResponse(res, { statusCode: 201, message: "Recommendation onboarding updated successfully", data: { topics: result } });
});

module.exports = {
  getFeedSummary,
  getMyFeed,
  getMyReels,
  getProfileSuggestions,
  trackImpression,
  trackWatchProgress,
  markNotInterested,
  seedOnboardingTopics,
};
