const ApiError = require("../utils/ApiError");
const Highlight = require("../models/Highlight");
const Story = require("../models/Story");
const User = require("../models/User");
const Follow = require("../models/Follow");

const publicProfileProjection = "-passwordHash";

const canViewUserContent = async (viewerId, owner) => {
  if (!owner.isPrivateAccount) return true;
  if (viewerId && viewerId.toString() === owner.id.toString()) return true;
  if (!viewerId) return false;
  return Boolean(await Follow.findOne({ follower: viewerId, following: owner.id }));
};

const formatHighlight = (highlight) => ({
  id: highlight.id,
  ownerId: highlight.ownerId?.toString?.() || highlight.ownerId,
  title: highlight.title,
  coverImageUrl: highlight.coverImageUrl,
  coverUrl: highlight.coverImageUrl,
  storyIds: (highlight.storyIds || []).map((storyId) => storyId?.toString?.() || storyId),
  items: (highlight.items || [])
    .slice()
    .sort((a, b) => (a.order || 0) - (b.order || 0))
    .map((item) => ({
      id: item.id,
      storyId: item.storyId?.toString?.() || item.storyId || null,
      mediaUrl: item.mediaUrl,
      thumbnailUrl: item.thumbnailUrl || item.mediaUrl,
      mediaType: item.mediaType,
      width: item.width ?? null,
      height: item.height ?? null,
      duration: item.duration ?? null,
      order: item.order || 0,
    })),
  createdAt: highlight.createdAt,
  updatedAt: highlight.updatedAt,
  isArchived: Boolean(highlight.isArchived),
});

const getOwnerByUsername = async (username) => {
  const owner = await User.findOne({ username: username.toLowerCase() }).select(publicProfileProjection);
  if (!owner) {
    throw new ApiError(404, "User not found");
  }
  return owner;
};

const buildHighlightItem = (story, order) => ({
  storyId: story._id,
  mediaUrl: story.media.url,
  thumbnailUrl: story.media.url,
  mediaType: story.media.type,
  publicId: story.media.publicId,
  width: story.media.width ?? null,
  height: story.media.height ?? null,
  duration: story.media.duration ?? null,
  order,
});

const loadOwnedActiveStories = async (currentUserId, storyIds) => {
  const uniqueStoryIds = [...new Set((storyIds || []).map((id) => id.toString()))];
  const stories = await Story.find({
    _id: { $in: uniqueStoryIds },
    author: currentUserId,
    expiresAt: { $gt: new Date() },
  }).lean();

  if (stories.length !== uniqueStoryIds.length) {
    throw new ApiError(400, "Only your active stories can be added to highlights");
  }

  const byId = new Map(stories.map((story) => [story._id.toString(), story]));
  return uniqueStoryIds.map((id) => byId.get(id)).filter(Boolean);
};

const getMyHighlights = async (currentUserId) => {
  const items = await Highlight.find({
    ownerId: currentUserId,
    isArchived: false,
  })
    .sort({ createdAt: -1 })
    .lean();
  return items.map(formatHighlight);
};

const getHighlightsByUsername = async (viewerId, username) => {
  const owner = await getOwnerByUsername(username);
  const canView = await canViewUserContent(viewerId, owner);
  if (!canView) {
    throw new ApiError(403, "This profile is private");
  }

  const items = await Highlight.find({
    ownerId: owner._id,
    isArchived: false,
  })
    .sort({ createdAt: -1 })
    .lean();
  return items.map(formatHighlight);
};

const createHighlight = async (currentUserId, payload) => {
  const stories = await loadOwnedActiveStories(currentUserId, payload.storyIds);
  if (stories.length === 0) {
    throw new ApiError(400, "Choose at least one story");
  }

  const storyById = new Map(stories.map((story) => [story._id.toString(), story]));
  const coverStoryId = payload.coverStoryId?.toString?.() || payload.coverStoryId || stories[0]._id.toString();
  const coverStory = storyById.get(coverStoryId) || stories[0];

  const highlight = await Highlight.create({
    ownerId: currentUserId,
    title: payload.title.trim(),
    coverImageUrl: coverStory.media.url,
    storyIds: stories.map((story) => story._id),
    items: stories.map((story, index) => buildHighlightItem(story, index)),
  });

  return formatHighlight(highlight.toObject());
};

const updateHighlight = async (currentUserId, highlightId, payload) => {
  const highlight = await Highlight.findOne({ _id: highlightId, ownerId: currentUserId, isArchived: false });
  if (!highlight) {
    throw new ApiError(404, "Highlight not found");
  }

  if (payload.title !== undefined) {
    highlight.title = payload.title.trim();
  }

  if (payload.coverStoryId) {
    const coverStoryId = payload.coverStoryId.toString();
    const coverItem = highlight.items.find((item) => (item.storyId?.toString?.() || item.storyId) === coverStoryId);
    if (!coverItem) {
      throw new ApiError(400, "Cover story must belong to this highlight");
    }
    highlight.coverImageUrl = coverItem.mediaUrl;
  }

  await highlight.save();
  return formatHighlight(highlight.toObject());
};

const deleteHighlight = async (currentUserId, highlightId) => {
  const highlight = await Highlight.findOneAndDelete({ _id: highlightId, ownerId: currentUserId });
  if (!highlight) {
    throw new ApiError(404, "Highlight not found");
  }
  return { deleted: true, id: highlight.id };
};

const addStoryToHighlight = async (currentUserId, highlightId, payload) => {
  const highlight = await Highlight.findOne({ _id: highlightId, ownerId: currentUserId, isArchived: false });
  if (!highlight) {
    throw new ApiError(404, "Highlight not found");
  }

  const [story] = await loadOwnedActiveStories(currentUserId, [payload.storyId]);
  const storyId = story._id.toString();
  if (highlight.storyIds.some((id) => id.toString() === storyId)) {
    throw new ApiError(409, "Story already exists in this highlight");
  }

  const nextOrder = typeof payload.insertAt === "number"
    ? Math.max(0, Math.min(payload.insertAt, highlight.items.length))
    : highlight.items.length;

  const items = highlight.items
    .map((item) => item.toObject?.() || item)
    .sort((a, b) => (a.order || 0) - (b.order || 0));
  items.splice(nextOrder, 0, buildHighlightItem(story, nextOrder));
  highlight.items = items.map((item, index) => ({ ...item, order: index }));
  highlight.storyIds = [...highlight.storyIds, story._id];
  if (payload.makeCover || !highlight.coverImageUrl) {
    highlight.coverImageUrl = story.media.url;
  }
  await highlight.save();
  return formatHighlight(highlight.toObject());
};

const removeStoryFromHighlight = async (currentUserId, highlightId, storyId) => {
  const highlight = await Highlight.findOne({ _id: highlightId, ownerId: currentUserId, isArchived: false });
  if (!highlight) {
    throw new ApiError(404, "Highlight not found");
  }

  const normalizedStoryId = storyId.toString();
  const nextItems = highlight.items
    .map((item) => item.toObject?.() || item)
    .filter((item) => (item.storyId?.toString?.() || item.storyId) !== normalizedStoryId);

  if (nextItems.length === highlight.items.length) {
    throw new ApiError(404, "Story not found in highlight");
  }

  if (nextItems.length === 0) {
    await Highlight.findByIdAndDelete(highlightId);
    return { deleted: true, id: highlightId, removedStoryId: normalizedStoryId, becameEmpty: true };
  }

  highlight.items = nextItems.map((item, index) => ({ ...item, order: index }));
  highlight.storyIds = highlight.storyIds.filter((id) => id.toString() !== normalizedStoryId);

  const currentCoverStillExists = highlight.items.some((item) => item.mediaUrl === highlight.coverImageUrl);
  if (!currentCoverStillExists) {
    highlight.coverImageUrl = highlight.items[0].mediaUrl;
  }

  await highlight.save();
  return {
    deleted: false,
    id: highlight.id,
    removedStoryId: normalizedStoryId,
    becameEmpty: false,
    highlight: formatHighlight(highlight.toObject()),
  };
};

module.exports = {
  getMyHighlights,
  getHighlightsByUsername,
  createHighlight,
  updateHighlight,
  deleteHighlight,
  addStoryToHighlight,
  removeStoryFromHighlight,
};
