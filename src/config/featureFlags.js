const { env } = require("./env");

const isChatRealtimeEnabled = () => env.chatRealtimeEnabled !== false;
const isGroupSendEnabled = () => env.chatGroupSendEnabled !== false;
const isMediaSendEnabled = () => env.chatMediaSendEnabled !== false;
const isChatReplayEnabled = () => env.chatReplaySyncEnabled !== false;
const isSocketJoinConversationEnabled = () => env.socketJoinConversationEnabled !== false;
const getRecommendationMode = () => env.recommendationMode || "weighted-cache";
const isRecommendationEnabled = () => getRecommendationMode() !== "disabled";

module.exports = {
  isChatRealtimeEnabled,
  isGroupSendEnabled,
  isMediaSendEnabled,
  isChatReplayEnabled,
  isSocketJoinConversationEnabled,
  getRecommendationMode,
  isRecommendationEnabled,
};
