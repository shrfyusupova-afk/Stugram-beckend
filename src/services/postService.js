const ApiError = require("../utils/ApiError");
const Post = require("../models/Post");
const SavedPost = require("../models/SavedPost");
const User = require("../models/User");
const Follow = require("../models/Follow");
const Block = require("../models/Block");
const Comment = require("../models/Comment");
const PostLike = require("../models/PostLike");
const UserContentPreference = require("../models/UserContentPreference");
const { destroyCloudinaryAsset, uploadBufferToCloudinary } = require("../utils/media");
const { getPagination, buildPaginationMeta } = require("../utils/pagination");

const normalizeHashtags = (hashtags = []) =>
  [...new Set(hashtags.map((tag) => tag.replace(/^#/, "").toLowerCase()))];

const mapMediaFiles = async (files, folder) =>
  Promise.all(
    files.map(async (file) => {
      const uploaded = await uploadBufferToCloudinary(file.buffer, folder, file.mimetype.startsWith("video") ? "video" : "image");
      return {
        url: uploaded.url,
        publicId: uploaded.publicId,
        type: uploaded.resourceType === "video" ? "video" : "image",
        width: uploaded.width,
        height: uploaded.height,
        duration: uploaded.duration,
      };
    })
  );

const canViewUserContent = async (viewerId, owner) => {
  if (!owner.isPrivateAccount) return true;
  if (viewerId && viewerId.toString() === owner.id.toString()) return true;
  if (!viewerId) return false;
  return Boolean(await Follow.findOne({ follower: viewerId, following: owner.id }));
};

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

const createPost = async (userId, payload, files) => {
  if (!files || files.length === 0) {
    throw new ApiError(400, "At least one media file is required");
  }

  const media = await mapMediaFiles(files, "stugram/posts");
  let post = null;
  try {
    post = await Post.create({
      author: userId,
      media,
      caption: payload.caption || "",
      hashtags: normalizeHashtags(payload.hashtags || []),
      location: payload.location || "",
    });

    await User.findByIdAndUpdate(userId, { $inc: { postsCount: 1 } });
    const createdPost = await Post.findById(post._id).populate(previewPopulate).lean();
    return {
      ...createdPost,
      savesCount: 0,
      viewerHasLiked: false,
      viewerHasSaved: false,
    };
  } catch (error) {
    if (post?._id) {
      await Post.findByIdAndDelete(post._id).catch(() => null);
    }
    await Promise.allSettled(
      media.map((item) => destroyCloudinaryAsset(item.publicId, item.type === "video" ? "video" : "image"))
    );
    throw error;
  }
};

const savePost = async (userId, postId) => {
  const post = await Post.findById(postId).populate(previewPopulate);
  if (!post) {
    throw new ApiError(404, "Post not found");
  }

  try {
    await SavedPost.create({ user: userId, post: postId });
  } catch (error) {
    if (error?.code === 11000) {
      throw new ApiError(409, "Post already saved");
    }
    throw error;
  }

  return {
    saved: true,
    post: mapPostPreview(post),
  };
};

const updatePost = async (userId, postId, payload) => {
  const post = await Post.findById(postId);
  if (!post) throw new ApiError(404, "Post not found");
  if (post.author.toString() !== userId.toString()) throw new ApiError(403, "You can edit only your own posts");

  if (payload.caption !== undefined) post.caption = payload.caption;
  if (payload.location !== undefined) post.location = payload.location;
  if (payload.hashtags !== undefined) post.hashtags = normalizeHashtags(payload.hashtags);
  post.isEdited = true;
  await post.save();
  const updatedPost = await Post.findById(post._id).populate(previewPopulate).lean();
  return {
    ...updatedPost,
    savesCount: updatedPost?.savesCount || 0,
  };
};

const unsavePost = async (userId, postId) => {
  const deleted = await SavedPost.findOneAndDelete({ user: userId, post: postId });
  if (!deleted) {
    throw new ApiError(404, "Saved post not found");
  }

  return { saved: false, postId };
};

const deletePost = async (userId, postId) => {
  const post = await Post.findById(postId);
  if (!post) throw new ApiError(404, "Post not found");
  if (post.author.toString() !== userId.toString()) throw new ApiError(403, "You can delete only your own posts");

  const mediaAssets = [...post.media];
  await Promise.all([
    SavedPost.deleteMany({ post: postId }),
    PostLike.deleteMany({ post: postId }),
    Comment.deleteMany({ post: postId }),
    post.deleteOne(),
  ]);
  await User.findByIdAndUpdate(userId, { $inc: { postsCount: -1 } });

  await Promise.allSettled(
    mediaAssets.map((item) => destroyCloudinaryAsset(item.publicId, item.type === "video" ? "video" : "image"))
  );

  return { deleted: true };
};

const getSinglePost = async (viewerId, postId) => {
  const post = await Post.findById(postId).populate("author", "username fullName avatar isPrivateAccount").lean();
  if (!post) throw new ApiError(404, "Post not found");
  const canView = await canViewUserContent(viewerId, post.author);
  if (!canView) throw new ApiError(403, "This profile is private");

  const [viewerHasLiked, viewerHasSaved, savesCount] = await Promise.all([
    viewerId ? PostLike.exists({ user: viewerId, post: postId }) : Promise.resolve(false),
    viewerId ? SavedPost.exists({ user: viewerId, post: postId }) : Promise.resolve(false),
    SavedPost.countDocuments({ post: postId }),
  ]);

  return {
    ...post,
    viewerHasLiked: Boolean(viewerHasLiked),
    viewerHasSaved: Boolean(viewerHasSaved),
    savesCount,
  };
};

const getUserPosts = async (viewerId, username, query) => {
  const owner = await User.findOne({ username: username.toLowerCase() });
  if (!owner) throw new ApiError(404, "User not found");
  const canView = await canViewUserContent(viewerId, owner);
  if (!canView) throw new ApiError(403, "This profile is private");

  const { page, limit, skip } = getPagination(query);
  const filter = { author: owner.id, isHiddenByAdmin: { $ne: true } };
  const [items, total] = await Promise.all([
    Post.find(filter).sort({ createdAt: -1 }).skip(skip).limit(limit).populate("author", "username fullName avatar"),
    Post.countDocuments(filter),
  ]);

  return { items, meta: buildPaginationMeta({ page, limit, total }) };
};

const getFeed = async (userId, query) => {
  const { page, limit, skip } = getPagination(query);
  const [followIds, blocks, hiddenPostIds, publicAuthorIds] = await Promise.all([
    Follow.find({ follower: userId }).distinct("following"),
    Block.find({ $or: [{ blocker: userId }, { blocked: userId }] }).select("blocker blocked").lean(),
    UserContentPreference.find({
      user: userId,
      preferenceType: { $in: ["hide", "not_interested"] },
    }).distinct("post"),
    User.distinct("_id", { isPrivateAccount: { $ne: true }, isSuspended: { $ne: true } }),
  ]);

  const blockedIds = new Set();
  for (const block of blocks) {
    blockedIds.add(String(block.blocker));
    blockedIds.add(String(block.blocked));
  }
  blockedIds.delete(String(userId));

  const authorIds = [...new Set([String(userId), ...followIds.map(String), ...publicAuthorIds.map(String)])].filter(
    (id) => !blockedIds.has(id)
  );

  const [items, total] = await Promise.all([
    Post.find({ author: { $in: authorIds }, _id: { $nin: hiddenPostIds }, isHiddenByAdmin: { $ne: true } })
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .populate("author", "username fullName avatar")
      .lean(),
    Post.countDocuments({ author: { $in: authorIds }, _id: { $nin: hiddenPostIds }, isHiddenByAdmin: { $ne: true } }),
  ]);

  return { items, meta: buildPaginationMeta({ page, limit, total }) };
};

const getSavedPosts = async (userId, query) => {
  const { page, limit, skip } = getPagination(query);
  const filter = { user: userId };

  const [items, total] = await Promise.all([
    SavedPost.find(filter)
      .populate({
        path: "post",
        populate: previewPopulate,
      })
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .lean(),
    SavedPost.countDocuments(filter),
  ]);

  const mappedItems = items
    .filter((item) => item.post)
    .map((item) => ({
      savedAt: item.createdAt,
      post: mapPostPreview(item.post),
    }));

  return {
    items: mappedItems,
    meta: { page, limit, total, totalPages: Math.max(Math.ceil(total / limit), 1) },
  };
};

module.exports = {
  createPost,
  savePost,
  updatePost,
  unsavePost,
  deletePost,
  getSinglePost,
  getUserPosts,
  getFeed,
  getSavedPosts,
};
