const ApiError = require("../utils/ApiError");
const CloseFriend = require("../models/CloseFriend");
const User = require("../models/User");
const { getPagination } = require("../utils/pagination");

const closeFriendUserProjection = "username fullName avatar bio isPrivateAccount";

const mapCloseFriendPreview = (user) => ({
  _id: user._id,
  username: user.username,
  fullName: user.fullName,
  avatar: user.avatar,
  bio: user.bio,
  isPrivateAccount: user.isPrivateAccount,
});

const addCloseFriend = async (currentUserId, targetUserId) => {
  if (String(currentUserId) === String(targetUserId)) {
    throw new ApiError(400, "You cannot add yourself to close friends");
  }

  const targetUser = await User.findById(targetUserId).select(closeFriendUserProjection).lean();
  if (!targetUser) {
    throw new ApiError(404, "User not found");
  }

  const relation = await CloseFriend.findOneAndUpdate(
    { user: currentUserId, closeFriend: targetUserId },
    { $setOnInsert: { user: currentUserId, closeFriend: targetUserId } },
    { upsert: true, new: true, setDefaultsOnInsert: true }
  );

  return {
    added: true,
    alreadyExists: relation.createdAt.getTime() !== relation.updatedAt.getTime(),
    user: mapCloseFriendPreview(targetUser),
  };
};

const removeCloseFriend = async (currentUserId, targetUserId) => {
  await CloseFriend.findOneAndDelete({ user: currentUserId, closeFriend: targetUserId });

  return {
    removed: true,
    userId: targetUserId,
  };
};

const listCloseFriends = async (currentUserId, query = {}) => {
  const { page, limit, skip } = getPagination(query);
  const [items, total] = await Promise.all([
    CloseFriend.find({ user: currentUserId })
      .populate("closeFriend", closeFriendUserProjection)
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .lean(),
    CloseFriend.countDocuments({ user: currentUserId }),
  ]);

  const mappedItems = items
    .filter((item) => item.closeFriend)
    .map((item) => ({
      addedAt: item.createdAt,
      user: mapCloseFriendPreview(item.closeFriend),
    }));

  return {
    items: mappedItems,
    meta: {
      page,
      limit,
      total,
      totalPages: Math.ceil(total / limit) || 1,
    },
  };
};

module.exports = {
  addCloseFriend,
  removeCloseFriend,
  listCloseFriends,
};
