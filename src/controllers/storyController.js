const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const storyService = require("../services/storyService");
const { getIo } = require("../socket/socketServer");
const { emitConversationUpdated, emitNewMessage } = require("../socket/chatSocket");

const createStory = catchAsync(async (req, res) => {
  const story = await storyService.createStory(req.user.id, req.body, req.file);
  sendResponse(res, { statusCode: 201, message: "Story created successfully", data: story });
});

const getStoriesFeed = catchAsync(async (req, res) => {
  const result = await storyService.getStoriesFeed(req.user.id, req.query);
  sendResponse(res, { message: "Stories feed fetched successfully", data: result.items, meta: result.meta });
});

const getStoriesOfUser = catchAsync(async (req, res) => {
  const result = await storyService.getStoriesOfUser(req.user?.id, req.params.username, req.query);
  sendResponse(res, { message: "User stories fetched successfully", data: result.items, meta: result.meta });
});

const markStoryAsViewed = catchAsync(async (req, res) => {
  const story = await storyService.markStoryAsViewed(req.user.id, req.params.storyId);
  sendResponse(res, { message: "Story marked as viewed", data: story });
});

const getStoryViewers = catchAsync(async (req, res) => {
  const result = await storyService.getStoryViewers(req.user.id, req.params.storyId, req.query);
  sendResponse(res, {
    message: "Story viewers fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const getStoryInsights = catchAsync(async (req, res) => {
  const insights = await storyService.getStoryInsights(req.user.id, req.params.storyId);
  sendResponse(res, {
    message: "Story insights fetched successfully",
    data: insights,
  });
});

const likeStory = catchAsync(async (req, res) => {
  const story = await storyService.likeStory(req.user.id, req.params.storyId);
  sendResponse(res, { message: "Story liked successfully", data: story });
});

const unlikeStory = catchAsync(async (req, res) => {
  const story = await storyService.unlikeStory(req.user.id, req.params.storyId);
  sendResponse(res, { message: "Story unliked successfully", data: story });
});

const getStoryLikes = catchAsync(async (req, res) => {
  const result = await storyService.getStoryLikes(req.user.id, req.params.storyId, req.query);
  sendResponse(res, {
    message: "Story likes fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const addStoryComment = catchAsync(async (req, res) => {
  const comment = await storyService.addStoryComment(req.user.id, req.params.storyId, req.body);
  sendResponse(res, {
    statusCode: 201,
    message: "Story comment added successfully",
    data: comment,
  });
});

const getStoryComments = catchAsync(async (req, res) => {
  const result = await storyService.getStoryComments(req.user.id, req.params.storyId, req.query);
  sendResponse(res, {
    message: "Story comments fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const getStoryReplies = catchAsync(async (req, res) => {
  const result = await storyService.getStoryReplies(req.user.id, req.params.storyId, req.query);
  sendResponse(res, {
    message: "Story replies fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const replyToStory = catchAsync(async (req, res) => {
  const result = await storyService.replyToStory(req.user.id, req.params.storyId, req.body);
  const io = getIo();

  if (result.participantIds && result.message) {
    emitNewMessage(io, result.participantIds, result.message);
    await emitConversationUpdated(io, result.participantIds, result.conversationId);
  }

  sendResponse(res, {
    statusCode: 201,
    message: "Story reply sent successfully",
    data: result,
  });
});

const deleteStoryComment = catchAsync(async (req, res) => {
  const result = await storyService.deleteStoryComment(req.user.id, req.params.storyId, req.params.commentId);
  sendResponse(res, {
    message: "Story comment deleted successfully",
    data: result,
  });
});

const deleteStory = catchAsync(async (req, res) => {
  const result = await storyService.deleteStory(req.user.id, req.params.storyId);
  sendResponse(res, { message: "Story deleted successfully", data: result });
});

module.exports = {
  createStory,
  getStoriesFeed,
  getStoriesOfUser,
  markStoryAsViewed,
  getStoryViewers,
  getStoryInsights,
  likeStory,
  unlikeStory,
  getStoryLikes,
  addStoryComment,
  getStoryComments,
  getStoryReplies,
  replyToStory,
  deleteStoryComment,
  deleteStory,
};
