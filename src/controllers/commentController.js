const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const commentService = require("../services/commentService");

const addComment = catchAsync(async (req, res) => {
  const comment = await commentService.addComment(req.user.id, req.params.postId, req.body);
  sendResponse(res, { statusCode: 201, message: "Comment added successfully", data: comment });
});

const getCommentsByPost = catchAsync(async (req, res) => {
  const result = await commentService.getCommentsByPost(req.params.postId, req.query);
  sendResponse(res, { message: "Comments fetched successfully", data: result.items, meta: result.meta });
});

const deleteComment = catchAsync(async (req, res) => {
  const result = await commentService.deleteComment(req.user.id, req.params.commentId);
  sendResponse(res, { message: "Comment deleted successfully", data: result });
});

module.exports = { addComment, getCommentsByPost, deleteComment };
