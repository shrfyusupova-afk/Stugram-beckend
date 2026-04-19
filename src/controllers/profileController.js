const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const profileService = require("../services/profileService");
const authService = require("../services/authService");
const profileSuggestionService = require("../services/profileSuggestionService");
const mobileSummaryService = require("../services/mobileSummaryService");

const getProfile = catchAsync(async (req, res) => {
  const result = await profileService.getProfileByUsername(req.user?.id, req.params.username);
  sendResponse(res, { message: "Profile fetched successfully", data: result });
});

const getCurrentProfile = catchAsync(async (req, res) => {
  const result = await profileService.getCurrentUserProfile(req.user.id);
  sendResponse(res, { message: "Current profile fetched successfully", data: result });
});

const checkUsernameAvailability = catchAsync(async (req, res) => {
  const result = await profileService.checkUsernameAvailability(req.query.username);
  sendResponse(res, { message: "Username availability fetched successfully", data: result });
});

const updateProfile = catchAsync(async (req, res) => {
  const result = await profileService.updateProfile(req.user.id, req.body);
  sendResponse(res, { message: "Profile updated successfully", data: result });
});

const uploadAvatar = catchAsync(async (req, res) => {
  const result = await profileService.uploadAvatar(req.user.id, req.file);
  sendResponse(res, { message: "Avatar uploaded successfully", data: result });
});

const uploadBanner = catchAsync(async (req, res) => {
  const result = await profileService.uploadBanner(req.user.id, req.file);
  sendResponse(res, { message: "Banner uploaded successfully", data: result });
});

const getProfileSuggestions = catchAsync(async (req, res) => {
  const result = await profileSuggestionService.getProfileSuggestions(req.user.id, req.query);
  sendResponse(res, { message: "Profile suggestions fetched successfully", data: result.items, meta: result.meta });
});

const getProfileReels = catchAsync(async (req, res) => {
  const result = await profileService.getProfileReels(req.user?.id, req.params.username, req.query);
  sendResponse(res, {
    message: "Profile reels fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const getProfileTaggedPosts = catchAsync(async (req, res) => {
  const result = await profileService.getProfileTaggedPosts(req.user?.id, req.params.username, req.query);
  sendResponse(res, {
    message: "Profile tagged posts fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const getProfileSummary = catchAsync(async (req, res) => {
  const result = await mobileSummaryService.getProfileQuickSummary(req.user?.id, req.params.username);
  sendResponse(res, {
    message: "Profile summary fetched successfully",
    data: result,
  });
});

const getMyProfiles = catchAsync(async (req, res) => {
  const result = await profileService.getProfilesForAccount(req.account.id);
  sendResponse(res, { message: "Profiles fetched successfully", data: result });
});

const createProfile = catchAsync(async (req, res) => {
  const profile = await profileService.createProfileForAccount(req.account.id, req.body);
  const auth = await authService.switchProfile(req.account.id, profile.id, {
    userAgent: req.headers["user-agent"] || null,
    ipAddress: req.ip,
    deviceId: req.headers["x-device-id"] || null,
  });
  sendResponse(res, { statusCode: 201, message: "Profile created successfully", data: auth });
});

module.exports = {
  getProfile,
  getCurrentProfile,
  checkUsernameAvailability,
  updateProfile,
  uploadAvatar,
  uploadBanner,
  getProfileSuggestions,
  getProfileReels,
  getProfileTaggedPosts,
  getProfileSummary,
  getMyProfiles,
  createProfile,
};
