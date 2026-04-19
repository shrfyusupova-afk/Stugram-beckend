const notificationService = require("./notificationService");
const chatService = require("./chatService");
const storyService = require("./storyService");
const followService = require("./followService");
const profileSuggestionService = require("./profileSuggestionService");
const profileService = require("./profileService");

const getHomeFeedSummary = async (userId) => {
  const [notificationSummary, chatSummary, storyUpdatesAvailable, pendingFollowRequests, suggestionResult] =
    await Promise.all([
      notificationService.getNotificationSummary(userId),
      chatService.getChatSummary(userId),
      storyService.hasStoryUpdatesAvailable(userId),
      followService.getPendingFollowRequestsCount(userId),
      profileSuggestionService.getProfileSuggestions(userId, { page: 1, limit: 1 }),
    ]);

  return {
    unreadNotifications: notificationSummary.unreadCount,
    unreadMessages: chatSummary.totalUnreadMessages,
    storyUpdatesAvailable,
    pendingFollowRequests,
    suggestedUsersCount: suggestionResult.meta?.total || 0,
  };
};

const getProfileQuickSummary = async (viewerId, username) => {
  const [profileSummary, storiesActive] = await Promise.all([
    profileService.getProfileSummary(viewerId, username),
    storyService.hasActiveStoriesByUsername(viewerId, username),
  ]);

  return {
    ...profileSummary,
    storiesActive,
  };
};

module.exports = {
  getHomeFeedSummary,
  getProfileQuickSummary,
};
