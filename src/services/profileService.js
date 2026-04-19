const ApiError = require("../utils/ApiError");
const User = require("../models/User");
const Follow = require("../models/Follow");
const Post = require("../models/Post");
const { getPagination } = require("../utils/pagination");
const { destroyCloudinaryAsset, uploadBufferToCloudinary } = require("../utils/media");
const { updateSettings } = require("./settingsService");
const bcrypt = require("bcryptjs");
const Account = require("../models/Account");
const { getFollowStatusForUser } = require("./followService");

const publicProfileProjection = "-passwordHash";
const escapeRegex = (value = "") => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

const countReelsForUser = async (userId) =>
  Post.countDocuments({ author: userId, "media.type": "video" });

const buildProfileResponse = async (
  user,
  { viewerId = null, isFollowing = false, followStatus = "not_following", includeReelsCount = true } = {}
) => {
  const base = {
    ...user.toObject(),
    isFollowing,
    followStatus,
  };

  if (!includeReelsCount) {
    return base;
  }

  const reelsCount = await countReelsForUser(user.id);
  return {
    ...base,
    reelsCount,
  };
};

const sanitizeProfileListItem = (user) => ({
  id: user.id,
  username: user.username,
  fullName: user.fullName,
  avatar: user.avatar || null,
  banner: user.banner || null,
  type: user.type || "student",
  region: user.region || "",
  district: user.district || "",
  school: user.school || "",
  createdAt: user.createdAt,
});

const canViewUserContent = async (viewerId, owner) => {
  if (!owner.isPrivateAccount) return true;
  if (viewerId && viewerId.toString() === owner.id.toString()) return true;
  if (!viewerId) return false;
  return Boolean(await Follow.findOne({ follower: viewerId, following: owner.id }));
};

const getProfileByUsername = async (viewerId, username) => {
  const user = await User.findOne({ username: username.toLowerCase() }).select(publicProfileProjection);
  if (!user) {
    throw new ApiError(404, "Profile not found");
  }

  const followStatus = await getFollowStatusForUser(viewerId, user.id);
  return buildProfileResponse(user, {
    viewerId,
    isFollowing: followStatus === "following",
    followStatus,
  });
};

const getCurrentUserProfile = async (currentUserId) => {
  const user = await User.findById(currentUserId).select(publicProfileProjection);
  if (!user) {
    throw new ApiError(404, "User not found");
  }

  return buildProfileResponse(user, {
    viewerId: currentUserId,
    isFollowing: false,
    followStatus: "self",
  });
};

const checkUsernameAvailability = async (username) => {
  const normalizedUsername = username.trim().toLowerCase();
  const exists = await User.exists({ username: normalizedUsername });
  return { available: !Boolean(exists) };
};

const updateProfile = async (currentUserId, payload) => {
  const user = await User.findById(currentUserId);
  if (!user) {
    throw new ApiError(404, "User not found");
  }

  if (payload.username && payload.username.toLowerCase() !== user.username) {
    const exists = await User.findOne({ username: payload.username.toLowerCase() });
    if (exists) {
      throw new ApiError(409, "Username already taken");
    }
    user.username = payload.username.toLowerCase();
  }

  if (payload.fullName !== undefined) user.fullName = payload.fullName;
  if (payload.bio !== undefined) user.bio = payload.bio;
  if (payload.birthday !== undefined) user.birthday = payload.birthday;
  if (payload.location !== undefined) user.location = payload.location;
  if (payload.school !== undefined) user.school = payload.school;
  if (payload.region !== undefined) user.region = payload.region;
  if (payload.district !== undefined) user.district = payload.district;
  if (payload.grade !== undefined) user.grade = payload.grade;
  if (payload.group !== undefined) user.group = payload.group;
  if (typeof payload.isPrivateAccount === "boolean") {
    user.isPrivateAccount = payload.isPrivateAccount;
    await updateSettings(currentUserId, { isPrivateAccount: payload.isPrivateAccount });
  }

  await user.save();
  return buildProfileResponse(user, {
    viewerId: currentUserId,
    isFollowing: false,
    followStatus: "self",
  });
};

const uploadAvatar = async (currentUserId, file) => {
  if (!file) {
    throw new ApiError(400, "Avatar file is required");
  }

  const currentUser = await User.findById(currentUserId).select(publicProfileProjection);
  if (!currentUser) {
    throw new ApiError(404, "User not found");
  }

  const upload = await uploadBufferToCloudinary(file.buffer, "stugram/avatars", "image");
  const previousAvatarPublicId = currentUser.avatarPublicId;
  try {
    currentUser.avatar = upload.url;
    currentUser.avatarPublicId = upload.publicId;
    await currentUser.save();
  } catch (error) {
    await destroyCloudinaryAsset(upload.publicId, "image").catch(() => null);
    throw error;
  }

  if (previousAvatarPublicId && previousAvatarPublicId !== upload.publicId) {
    await destroyCloudinaryAsset(previousAvatarPublicId, "image").catch(() => null);
  }

  return buildProfileResponse(currentUser, {
    viewerId: currentUserId,
    isFollowing: false,
    followStatus: "self",
  });
};

const uploadBanner = async (currentUserId, file) => {
  if (!file) {
    throw new ApiError(400, "Banner file is required");
  }

  const currentUser = await User.findById(currentUserId).select(publicProfileProjection);
  if (!currentUser) {
    throw new ApiError(404, "User not found");
  }

  const upload = await uploadBufferToCloudinary(file.buffer, "stugram/banners", "image");
  const previousBannerPublicId = currentUser.bannerPublicId;

  try {
    currentUser.banner = upload.url;
    currentUser.bannerPublicId = upload.publicId;
    await currentUser.save();
  } catch (error) {
    await destroyCloudinaryAsset(upload.publicId, "image").catch(() => null);
    throw error;
  }

  if (previousBannerPublicId && previousBannerPublicId !== upload.publicId) {
    await destroyCloudinaryAsset(previousBannerPublicId, "image").catch(() => null);
  }

  return buildProfileResponse(currentUser, {
    viewerId: currentUserId,
    isFollowing: false,
    followStatus: "self",
  });
};

const getProfileReels = async (viewerId, username, query = {}) => {
  const owner = await User.findOne({ username: username.toLowerCase() }).select(publicProfileProjection);
  if (!owner) {
    throw new ApiError(404, "User not found");
  }

  const canView = await canViewUserContent(viewerId, owner);
  if (!canView) {
    throw new ApiError(403, "This profile is private");
  }

  const { page, limit, skip } = getPagination(query);
  const filter = { author: owner._id, "media.type": "video", isHiddenByAdmin: { $ne: true } };
  const [items, total] = await Promise.all([
    Post.find(filter)
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .populate("author", "username fullName avatar isPrivateAccount")
      .lean(),
    Post.countDocuments(filter),
  ]);

  return {
    items,
    meta: {
      page,
      limit,
      total,
      totalPages: Math.max(Math.ceil(total / limit), 1),
    },
  };
};

const getProfileTaggedPosts = async (viewerId, username, query = {}) => {
  const owner = await User.findOne({ username: username.toLowerCase() }).select(publicProfileProjection);
  if (!owner) {
    throw new ApiError(404, "User not found");
  }

  const canView = await canViewUserContent(viewerId, owner);
  if (!canView) {
    throw new ApiError(403, "This profile is private");
  }

  const { page, limit, skip } = getPagination(query);
  const mentionRegex = new RegExp(`(^|\\s)@${escapeRegex(owner.username)}\\b`, "i");
  const candidateItems = await Post.find({ caption: mentionRegex })
    .sort({ createdAt: -1 })
    .limit(skip + Math.min(limit * 3, 150))
    .populate("author", "username fullName avatar isPrivateAccount")
    .lean();

  const visibleItems = [];
  for (const item of candidateItems) {
    if (await canViewUserContent(viewerId, item.author)) {
      visibleItems.push(item);
    }
  }

  return {
    items: visibleItems.slice(skip, skip + limit),
    meta: {
      page,
      limit,
      total: visibleItems.length,
      totalPages: Math.max(Math.ceil(visibleItems.length / limit), 1),
    },
  };
};

const getProfileSummary = async (viewerId, username) => {
  const owner = await User.findOne({ username: username.toLowerCase() }).select(publicProfileProjection);
  if (!owner) {
    throw new ApiError(404, "User not found");
  }

  const canView = await canViewUserContent(viewerId, owner);
  if (!canView) {
    throw new ApiError(403, "This profile is private");
  }

  const reelsCount = await Post.countDocuments({ author: owner._id, "media.type": "video" });

  return {
    followersCount: owner.followersCount || 0,
    followingCount: owner.followingCount || 0,
    postsCount: owner.postsCount || 0,
    reelsCount,
  };
};

module.exports = {
  getProfileByUsername,
  getCurrentUserProfile,
  checkUsernameAvailability,
  updateProfile,
  uploadAvatar,
  uploadBanner,
  getProfileReels,
  getProfileTaggedPosts,
  getProfileSummary,
  getProfilesForAccount: async (accountId) => {
    const items = await User.find({ accountId })
      .sort({ createdAt: -1 })
      .select("username fullName avatar banner type region district school createdAt")
      .lean();
    return items.map((item) => ({
      id: item._id,
      username: item.username,
      fullName: item.fullName,
      avatar: item.avatar || null,
      banner: item.banner || null,
      type: item.type || "student",
      region: item.region || "",
      district: item.district || "",
      school: item.school || "",
      createdAt: item.createdAt,
    }));
  },
  createProfileForAccount: async (accountId, payload) => {
    const account = await Account.findById(accountId);
    if (!account) throw new ApiError(401, "Account not found");

    const username = payload.username.trim().toLowerCase();
    const exists = await User.findOne({ username });
    if (exists) throw new ApiError(409, "Username already taken");

    const type = payload.type;
    if (type === "student") {
      if (!payload.region || !payload.district || !payload.school) {
        throw new ApiError(400, "Region, district and school are required for student profiles");
      }
    }

    const fullName = `${payload.firstName.trim()} ${payload.lastName.trim()}`.trim();
    const passwordHash = await bcrypt.hash(payload.password, 12);

    const profile = await User.create({
      accountId,
      identity: null,
      username,
      fullName,
      passwordHash,
      type,
      region: type === "student" ? payload.region : "",
      district: type === "student" ? payload.district : "",
      school: type === "student" ? payload.school : "",
      lastLoginAt: new Date(),
    });

    return profile;
  },
};
