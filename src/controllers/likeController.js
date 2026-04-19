const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const likeService = require("../services/likeService");

const likePost = catchAsync(async (req, res) => {
  const result = await likeService.likePost(req.user.id, req.params.postId);
  sendResponse(res, { message: "Post liked successfully", data: result });
});

const getLikedPostsHistory = catchAsync(async (req, res) => {
  const result = await likeService.getLikedPostsHistory(req.user.id, req.query);
  sendResponse(res, { message: "Liked posts fetched successfully", data: result.items, meta: result.meta });
});

const unlikePost = catchAsync(async (req, res) => {
  const result = await likeService.unlikePost(req.user.id, req.params.postId);
  sendResponse(res, { message: "Post unliked successfully", data: result });
});

module.exports = { getLikedPostsHistory, likePost, unlikePost };
