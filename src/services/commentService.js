const ApiError = require("../utils/ApiError");
const Comment = require("../models/Comment");
const Post = require("../models/Post");
const { createNotification } = require("./notificationService");
const { getPagination } = require("../utils/pagination");

const addComment = async (userId, postId, payload) => {
  const post = await Post.findById(postId);
  if (!post) throw new ApiError(404, "Post not found");

  let parentComment = null;
  if (payload.parentCommentId) {
    parentComment = await Comment.findById(payload.parentCommentId);
    if (!parentComment) throw new ApiError(404, "Parent comment not found");
  }

  const comment = await Comment.create({
    post: postId,
    author: userId,
    parentComment: payload.parentCommentId || null,
    content: payload.content,
  });

  post.commentsCount += 1;
  await post.save();

  if (parentComment) {
    parentComment.repliesCount += 1;
    await parentComment.save();
    await createNotification({
      recipient: parentComment.author,
      actor: userId,
      type: "reply",
      post: postId,
      comment: comment.id,
      message: "replied to your comment",
    });
  } else {
    await createNotification({
      recipient: post.author,
      actor: userId,
      type: "comment",
      post: postId,
      comment: comment.id,
      message: "commented on your post",
    });
  }

  return comment;
};

const getCommentsByPost = async (postId, query) => {
  const { page, limit, skip } = getPagination(query);
  const filter = { post: postId };
  const [items, total] = await Promise.all([
    Comment.find(filter)
      .populate("author", "username fullName avatar")
      .populate("parentComment", "_id content author")
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .lean(),
    Comment.countDocuments(filter),
  ]);

  return { items, meta: { page, limit, total, totalPages: Math.ceil(total / limit) } };
};

const deleteComment = async (userId, commentId) => {
  const comment = await Comment.findById(commentId);
  if (!comment) throw new ApiError(404, "Comment not found");
  if (comment.author.toString() !== userId.toString()) throw new ApiError(403, "Only owner can delete comment");

  await comment.deleteOne();
  await Post.findByIdAndUpdate(comment.post, { $inc: { commentsCount: -1 } });
  if (comment.parentComment) {
    await Comment.findByIdAndUpdate(comment.parentComment, { $inc: { repliesCount: -1 } });
  }
  return { deleted: true };
};

module.exports = { addComment, getCommentsByPost, deleteComment };
