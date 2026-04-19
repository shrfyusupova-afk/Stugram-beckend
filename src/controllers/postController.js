const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const postService = require("../services/postService");

const createPost = catchAsync(async (req, res) => {
  const post = await postService.createPost(req.user.id, req.body, req.files);
  sendResponse(res, { statusCode: 201, message: "Post created successfully", data: post });
});

const savePost = catchAsync(async (req, res) => {
  const result = await postService.savePost(req.user.id, req.params.postId);
  sendResponse(res, { statusCode: 201, message: "Post saved successfully", data: result });
});

const updatePost = catchAsync(async (req, res) => {
  const post = await postService.updatePost(req.user.id, req.params.postId, req.body);
  sendResponse(res, { message: "Post updated successfully", data: post });
});

const unsavePost = catchAsync(async (req, res) => {
  const result = await postService.unsavePost(req.user.id, req.params.postId);
  sendResponse(res, { message: "Post unsaved successfully", data: result });
});

const deletePost = catchAsync(async (req, res) => {
  const result = await postService.deletePost(req.user.id, req.params.postId);
  sendResponse(res, { message: "Post deleted successfully", data: result });
});

const getPost = catchAsync(async (req, res) => {
  const post = await postService.getSinglePost(req.user?.id, req.params.postId);
  sendResponse(res, { message: "Post fetched successfully", data: post });
});

const getUserPosts = catchAsync(async (req, res) => {
  const result = await postService.getUserPosts(req.user?.id, req.params.username, req.query);
  sendResponse(res, { message: "User posts fetched successfully", data: result.items, meta: result.meta });
});

const getFeed = catchAsync(async (req, res) => {
  const result = await postService.getFeed(req.user.id, req.query);
  sendResponse(res, { message: "Feed fetched successfully", data: result.items, meta: result.meta });
});

const getSavedPosts = catchAsync(async (req, res) => {
  const result = await postService.getSavedPosts(req.user.id, req.query);
  sendResponse(res, { message: "Saved posts fetched successfully", data: result.items, meta: result.meta });
});

module.exports = {
  createPost,
  savePost,
  updatePost,
  unsavePost,
  deletePost,
  getPost,
  getUserPosts,
  getFeed,
  getSavedPosts,
};
