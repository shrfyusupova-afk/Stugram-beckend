const ApiError = require("../utils/ApiError");
const Story = require("../models/Story");
const StoryLike = require("../models/StoryLike");
const StoryComment = require("../models/StoryComment");
const User = require("../models/User");
const Follow = require("../models/Follow");
const { destroyCloudinaryAsset, uploadBufferToCloudinary } = require("../utils/media");
const { getPagination } = require("../utils/pagination");
const chatService = require("./chatService");

const userPreviewProjection = "username fullName avatar bio isPrivateAccount";

const canViewStories = async (viewerId, owner) => {
  if (!owner.isPrivateAccount) return true;
  if (viewerId && owner.id.toString() === viewerId.toString()) return true;
  if (!viewerId) return false;
  return Boolean(await Follow.findOne({ follower: viewerId, following: owner.id }));
};

const getStoryOrThrow = async (storyId) => {
  const story = await Story.findById(storyId).populate("author", userPreviewProjection);
  if (!story || story.expiresAt <= new Date()) {
    throw new ApiError(404, "Story not found");
  }

  return story;
};

const ensureStoryVisible = async (viewerId, story) => {
  const canView = await canViewStories(viewerId, story.author);
  if (!canView) {
    throw new ApiError(403, "This story is not available");
  }
};

const ensureStoryOwner = (userId, story) => {
  if (story.author._id.toString() !== userId.toString()) {
    throw new ApiError(403, "Only story owner can access this resource");
  }
};

const mapUserPreview = (user) => ({
  _id: user._id,
  username: user.username,
  fullName: user.fullName,
  avatar: user.avatar,
  bio: user.bio,
  isPrivateAccount: user.isPrivateAccount,
});

const mapCommentPreview = (comment) => ({
  _id: comment._id,
  author: mapUserPreview(comment.author),
  content: comment.content,
  createdAt: comment.createdAt,
  updatedAt: comment.updatedAt,
});

const buildLatestViewers = async (story, limit = 3) => {
  const latestViewers = [...story.viewers]
    .sort((first, second) => second.viewedAt.getTime() - first.viewedAt.getTime())
    .slice(0, limit);
  const viewerIds = latestViewers.map((item) => item.user);
  const users = await User.find({ _id: { $in: viewerIds } }).select(userPreviewProjection).lean();
  const userMap = new Map(users.map((user) => [user._id.toString(), user]));

  return latestViewers
    .map((item) => userMap.get(item.user.toString()) && { user: mapUserPreview(userMap.get(item.user.toString())), viewedAt: item.viewedAt })
    .filter(Boolean);
};

const buildLatestLikers = async (storyId, limit = 3) => {
  const items = await StoryLike.find({ story: storyId })
    .populate("user", userPreviewProjection)
    .sort({ createdAt: -1 })
    .limit(limit);

  return items
    .filter((item) => item.user)
    .map((item) => ({
      user: mapUserPreview(item.user),
      likedAt: item.createdAt,
    }));
};

const buildLatestCommenters = async (storyId, limit = 3) => {
  const items = await StoryComment.find({ story: storyId })
    .populate("author", userPreviewProjection)
    .sort({ createdAt: -1 })
    .limit(limit);

  return items.filter((item) => item.author).map(mapCommentPreview);
};

const buildOwnerInsights = async (story) => {
  const [latestViewers, latestLikers, latestCommenters] = await Promise.all([
    buildLatestViewers(story, 3),
    buildLatestLikers(story._id, 3),
    buildLatestCommenters(story._id, 3),
  ]);

  return {
    totalViews: story.viewers.length,
    totalLikes: story.likesCount || 0,
    totalReplies: story.repliesCount || 0,
    latestViewers,
    latestLikers,
    latestCommenters,
  };
};

const enrichStories = async (stories, currentUserId) => {
  const storyIds = stories.map((story) => story._id);
  const likedStoryIds = currentUserId
    ? await StoryLike.find({ story: { $in: storyIds }, user: currentUserId }).distinct("story")
    : [];
  const likedSet = new Set(likedStoryIds.map((id) => id.toString()));

  return Promise.all(
    stories.map(async (story) => {
      const isOwner = Boolean(currentUserId && story.author?._id?.toString() === currentUserId.toString());
      const ownerInsights = isOwner ? await buildOwnerInsights(story) : null;

      return {
        _id: story._id,
        author: story.author,
        media: story.media,
        caption: story.caption,
        expiresAt: story.expiresAt,
        createdAt: story.createdAt,
        updatedAt: story.updatedAt,
        viewersCount: story.viewers.length,
        likesCount: story.likesCount || 0,
        repliesCount: story.repliesCount || 0,
        isViewedByMe: Boolean(currentUserId && story.viewers.some((viewer) => viewer.user.toString() === currentUserId.toString())),
        isLikedByMe: likedSet.has(story._id.toString()),
        latestViewers: ownerInsights?.latestViewers || [],
        latestLikers: ownerInsights?.latestLikers || [],
        latestCommenters: ownerInsights?.latestCommenters || [],
        ownerInsights,
      };
    })
  );
};

const createStory = async (userId, payload, file) => {
  if (!file) throw new ApiError(400, "Story media is required");
  const uploaded = await uploadBufferToCloudinary(file.buffer, "stugram/stories", file.mimetype.startsWith("video") ? "video" : "image");

  const story = await Story.create({
    author: userId,
    caption: payload.caption || "",
    media: {
      url: uploaded.url,
      publicId: uploaded.publicId,
      type: uploaded.resourceType === "video" ? "video" : "image",
      width: uploaded.width,
      height: uploaded.height,
      duration: uploaded.duration,
    },
    expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000),
  });

  const hydratedStory = await Story.findById(story._id).populate("author", userPreviewProjection);
  const [formattedStory] = await enrichStories([hydratedStory], userId);
  return formattedStory;
};

const getStoriesFeed = async (userId, query) => {
  const { page, limit, skip } = getPagination(query);
  const followIds = await Follow.find({ follower: userId }).distinct("following");
  const authorIds = [userId, ...followIds];
  const filter = { author: { $in: authorIds }, expiresAt: { $gt: new Date() } };

  const [items, total] = await Promise.all([
    Story.find(filter).sort({ createdAt: -1 }).skip(skip).limit(limit).populate("author", userPreviewProjection),
    Story.countDocuments(filter),
  ]);

  return {
    items: await enrichStories(items, userId),
    meta: { page, limit, total, totalPages: Math.ceil(total / limit) },
  };
};

const hasStoryUpdatesAvailable = async (userId) => {
  const followIds = await Follow.find({ follower: userId }).distinct("following");
  if (!followIds.length) {
    return false;
  }

  const story = await Story.findOne({
    author: { $in: followIds },
    expiresAt: { $gt: new Date() },
  })
    .select("_id")
    .lean();

  return Boolean(story);
};

const getStoriesOfUser = async (viewerId, username, query) => {
  const owner = await User.findOne({ username: username.toLowerCase() });
  if (!owner) throw new ApiError(404, "User not found");
  const canView = await canViewStories(viewerId, owner);
  if (!canView) throw new ApiError(403, "This profile is private");

  const { page, limit, skip } = getPagination(query);
  const filter = { author: owner.id, expiresAt: { $gt: new Date() } };

  const [items, total] = await Promise.all([
    Story.find(filter).sort({ createdAt: -1 }).skip(skip).limit(limit).populate("author", userPreviewProjection),
    Story.countDocuments(filter),
  ]);

  return {
    items: await enrichStories(items, viewerId),
    meta: { page, limit, total, totalPages: Math.ceil(total / limit) },
  };
};

const hasActiveStoriesByUsername = async (viewerId, username) => {
  const owner = await User.findOne({ username: username.toLowerCase() });
  if (!owner) throw new ApiError(404, "User not found");
  const canView = await canViewStories(viewerId, owner);
  if (!canView) throw new ApiError(403, "This profile is private");

  const story = await Story.findOne({
    author: owner._id,
    expiresAt: { $gt: new Date() },
  })
    .select("_id")
    .lean();

  return Boolean(story);
};

const markStoryAsViewed = async (userId, storyId) => {
  const story = await getStoryOrThrow(storyId);
  await ensureStoryVisible(userId, story);
  if (!story.viewers.some((viewer) => viewer.user.toString() === userId.toString())) {
    story.viewers.push({ user: userId });
    await story.save();
  }

  const [formattedStory] = await enrichStories([story], userId);
  return formattedStory;
};

const getStoryViewers = async (userId, storyId, query) => {
  const story = await getStoryOrThrow(storyId);
  ensureStoryOwner(userId, story);

  const { page, limit, skip } = getPagination(query);
  const viewers = story.viewers.sort((first, second) => second.viewedAt.getTime() - first.viewedAt.getTime());
  const total = viewers.length;
  const paginatedViewers = viewers.slice(skip, skip + limit);
  const viewerIds = paginatedViewers.map((item) => item.user);
  const users = await User.find({ _id: { $in: viewerIds } }).select(userPreviewProjection).lean();
  const userMap = new Map(users.map((user) => [user._id.toString(), user]));

  return {
    items: paginatedViewers
      .map((item) => userMap.get(item.user.toString()) && { user: mapUserPreview(userMap.get(item.user.toString())), viewedAt: item.viewedAt })
      .filter(Boolean),
    meta: { page, limit, total, totalPages: Math.ceil(total / limit) || 1 },
  };
};

const getStoryInsights = async (userId, storyId) => {
  const story = await getStoryOrThrow(storyId);
  ensureStoryOwner(userId, story);

  return buildOwnerInsights(story);
};

const likeStory = async (userId, storyId) => {
  const story = await getStoryOrThrow(storyId);
  await ensureStoryVisible(userId, story);

  const existingLike = await StoryLike.findOne({ story: storyId, user: userId });
  if (!existingLike) {
    await StoryLike.create({ story: storyId, user: userId });
    story.likesCount += 1;
    await story.save();
  }

  const hydratedStory = await getStoryOrThrow(storyId);
  const [formattedStory] = await enrichStories([hydratedStory], userId);
  return formattedStory;
};

const unlikeStory = async (userId, storyId) => {
  const story = await getStoryOrThrow(storyId);
  await ensureStoryVisible(userId, story);

  const deletedLike = await StoryLike.findOneAndDelete({ story: storyId, user: userId });
  if (deletedLike && story.likesCount > 0) {
    story.likesCount -= 1;
    await story.save();
  }

  const hydratedStory = await getStoryOrThrow(storyId);
  const [formattedStory] = await enrichStories([hydratedStory], userId);
  return formattedStory;
};

const getStoryLikes = async (userId, storyId, query) => {
  const story = await getStoryOrThrow(storyId);
  ensureStoryOwner(userId, story);

  const { page, limit, skip } = getPagination(query);
  const [items, total] = await Promise.all([
    StoryLike.find({ story: storyId })
      .populate("user", userPreviewProjection)
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit),
    StoryLike.countDocuments({ story: storyId }),
  ]);

  return {
    items: items
      .filter((item) => item.user)
      .map((item) => ({
        user: mapUserPreview(item.user),
        likedAt: item.createdAt,
      })),
    meta: { page, limit, total, totalPages: Math.ceil(total / limit) || 1 },
  };
};

const addStoryComment = async (userId, storyId, payload) => {
  const story = await getStoryOrThrow(storyId);
  await ensureStoryVisible(userId, story);

  const comment = await StoryComment.create({
    story: storyId,
    author: userId,
    content: payload.content,
  });

  story.commentsCount += 1;
  await story.save();

  const populatedComment = await StoryComment.findById(comment._id).populate("author", userPreviewProjection);

  return {
    _id: populatedComment._id,
    author: mapUserPreview(populatedComment.author),
    content: populatedComment.content,
    createdAt: populatedComment.createdAt,
    updatedAt: populatedComment.updatedAt,
  };
};

const getStoryComments = async (userId, storyId, query) => {
  const story = await getStoryOrThrow(storyId);
  ensureStoryOwner(userId, story);

  const { page, limit, skip } = getPagination(query);
  const [items, total] = await Promise.all([
    StoryComment.find({ story: storyId })
      .populate("author", userPreviewProjection)
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit),
    StoryComment.countDocuments({ story: storyId }),
  ]);

  return {
    items: items
      .filter((item) => item.author)
      .map(mapCommentPreview),
    meta: { page, limit, total, totalPages: Math.ceil(total / limit) || 1 },
  };
};

const getStoryReplies = async (userId, storyId, query) => {
  const story = await getStoryOrThrow(storyId);
  ensureStoryOwner(userId, story);

  const { page, limit, skip } = getPagination(query);

  // For replies, we'll fetch messages that have a specific metadata kind for story_reply
  // or use the StoryComment model if it was repurposed for "replies" internally.
  // Actually, the current repo uses StoryComment for comments and a separate replyToStory that sends a DM.
  // Let's use StoryComment for the "Replies" list in the insights for now if that's what we want to show.
  // BUT the prompt asks for "exact users who replied to the story".
  // If replyToStory sends a DM, we might need to search Messages.
  // However, story.repliesCount exists in the model now. Let's stick to StoryComment as the source of truth for the list.

  const [items, total] = await Promise.all([
    StoryComment.find({ story: storyId })
      .populate("author", userPreviewProjection)
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit),
    StoryComment.countDocuments({ story: storyId }),
  ]);

  return {
    items: items
      .filter((item) => item.author)
      .map(mapCommentPreview),
    meta: { page, limit, total, totalPages: Math.ceil(total / limit) || 1 },
  };
};

const replyToStory = async (userId, storyId, payload) => {
  const story = await getStoryOrThrow(storyId);
  await ensureStoryVisible(userId, story);

  if (story.author._id.toString() === userId.toString()) {
    throw new ApiError(400, "You cannot reply to your own story");
  }

  // 1. Increment repliesCount and create a StoryComment record for the insights list
  await StoryComment.create({
    story: storyId,
    author: userId,
    content: payload.text,
  });

  story.repliesCount += 1;
  await story.save();

  // 2. Send the DM
  const conversation = await chatService.createConversation(userId, story.author._id);
  const result = await chatService.sendMessage(userId, conversation._id, {
    text: payload.text,
    messageType: "text",
    metadata: {
      kind: "story_reply",
      payload: {
        storyId: story._id,
        mediaUrl: story.media.url,
      }
    }
  });

  return {
    storyId: story._id,
    conversationId: conversation._id,
    participantIds: result.participantIds,
    message: result.message,
  };
};

const deleteStoryComment = async (userId, storyId, commentId) => {
  const story = await getStoryOrThrow(storyId);
  const comment = await StoryComment.findOne({ _id: commentId, story: storyId });
  if (!comment) {
    throw new ApiError(404, "Story comment not found");
  }

  const canDelete = comment.author.toString() === userId.toString() || story.author._id.toString() === userId.toString();
  if (!canDelete) {
    throw new ApiError(403, "You cannot delete this story comment");
  }

  await comment.deleteOne();
  if (story.repliesCount > 0) {
    story.repliesCount -= 1;
    await story.save();
  }

  return { deleted: true, commentId };
};

const deleteStory = async (userId, storyId) => {
  const story = await Story.findById(storyId);
  if (!story) throw new ApiError(404, "Story not found");
  if (story.author.toString() !== userId.toString()) throw new ApiError(403, "Only owner can delete story");
  await Promise.all([StoryLike.deleteMany({ story: storyId }), StoryComment.deleteMany({ story: storyId })]);
  await story.deleteOne();
  await destroyCloudinaryAsset(story.media.publicId, story.media.type === "video" ? "video" : "image").catch(() => null);
  return { deleted: true };
};

module.exports = {
  createStory,
  getStoriesFeed,
  hasStoryUpdatesAvailable,
  getStoriesOfUser,
  hasActiveStoriesByUsername,
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
