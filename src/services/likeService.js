const ApiError = require("../utils/ApiError");
const Post = require("../models/Post");
const PostLike = require("../models/PostLike");
const { getPagination } = require("../utils/pagination");
const { createNotification } = require("./notificationService");
const { recordEvent } = require("./interactionTrackingService");

const previewPopulate = {
  path: "author",
  select: "username fullName avatar isPrivateAccount",
};

const mapPostPreview = (post) => ({
  _id: post._id,
  author: post.author,
  media: post.media,
  caption: post.caption,
  hashtags: post.hashtags,
  location: post.location,
  likesCount: post.likesCount,
  commentsCount: post.commentsCount,
  isEdited: post.isEdited,
  createdAt: post.createdAt,
  updatedAt: post.updatedAt,
});

const likePost = async (userId, postId) => {
  const post = await Post.findById(postId);
  if (!post) throw new ApiError(404, "Post not found");

  try {
    await PostLike.create({ user: userId, post: postId });
  } catch (error) {
    if (error?.code === 11000) throw new ApiError(409, "Post already liked");
    throw error;
  }
  post.likesCount += 1;
  await post.save();

  await createNotification({
    recipient: post.author,
    actor: userId,
    type: "like",
    post: post.id,
    message: "liked your post",
  });

  await recordEvent(userId, {
    eventType: "like",
    surface: post.media?.some((item) => item.type === "video") ? "reels" : "feed",
    contentId: postId,
    creatorId: post.author,
  });

  return { likesCount: post.likesCount, liked: true };
};

const unlikePost = async (userId, postId) => {
  const post = await Post.findById(postId);
  if (!post) throw new ApiError(404, "Post not found");

  const deleted = await PostLike.findOneAndDelete({ user: userId, post: postId });
  if (!deleted) throw new ApiError(404, "Like not found");

  post.likesCount = Math.max(post.likesCount - 1, 0);
  await post.save();

  return { likesCount: post.likesCount, liked: false };
};

const getLikedPostsHistory = async (userId, query) => {
  const { page, limit, skip } = getPagination(query);
  const filter = { user: userId };

  const [items, total] = await Promise.all([
    PostLike.find(filter)
      .populate({
        path: "post",
        populate: previewPopulate,
      })
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .lean(),
    PostLike.countDocuments(filter),
  ]);

  const mappedItems = items
    .filter((item) => item.post)
    .map((item) => ({
      likedAt: item.createdAt,
      post: mapPostPreview(item.post),
    }));

  return {
    items: mappedItems,
    meta: {
      page,
      limit,
      total,
      totalPages: Math.max(Math.ceil(total / limit), 1),
    },
  };
};

module.exports = { likePost, unlikePost, getLikedPostsHistory };
