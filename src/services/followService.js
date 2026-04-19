const ApiError = require("../utils/ApiError");
const Follow = require("../models/Follow");
const FollowRequest = require("../models/FollowRequest");
const User = require("../models/User");
const { createNotification } = require("./notificationService");
const { getPagination } = require("../utils/pagination");
const { recordEvent } = require("./interactionTrackingService");

const createFollowRelation = async (followerId, followingId) => {
  await Follow.create({ follower: followerId, following: followingId });
  await User.findByIdAndUpdate(followerId, { $inc: { followingCount: 1 } });
  await User.findByIdAndUpdate(followingId, { $inc: { followersCount: 1 } });
};

const followUser = async (currentUserId, targetUserId) => {
  if (currentUserId.toString() === targetUserId) throw new ApiError(400, "You cannot follow yourself");
  const targetUser = await User.findById(targetUserId);
  if (!targetUser) throw new ApiError(404, "User not found");

  const existingFollow = await Follow.findOne({ follower: currentUserId, following: targetUserId });
  if (existingFollow) throw new ApiError(409, "Already following user");

  if (targetUser.isPrivateAccount) {
    const request = await FollowRequest.findOneAndUpdate(
      { requester: currentUserId, recipient: targetUserId },
      { status: "pending" },
      { upsert: true, new: true, setDefaultsOnInsert: true }
    );
    await createNotification({
      recipient: targetUserId,
      actor: currentUserId,
      type: "follow_request",
      followRequest: request.id,
      message: "sent you a follow request",
    });
    return { updated: true, status: "requested", request };
  }

  await createFollowRelation(currentUserId, targetUserId);
  await createNotification({
    recipient: targetUserId,
    actor: currentUserId,
    type: "follow",
    message: "started following you",
  });

  await recordEvent(currentUserId, {
    eventType: "follow",
    surface: "profiles",
    targetProfileId: targetUserId,
    creatorId: targetUserId,
  });
  return { updated: true, status: "following" };
};

const unfollowUser = async (currentUserId, targetUserId) => {
  const deletedFollow = await Follow.findOneAndDelete({ follower: currentUserId, following: targetUserId });
  if (deletedFollow) {
    await User.findByIdAndUpdate(currentUserId, { $inc: { followingCount: -1 } });
    await User.findByIdAndUpdate(targetUserId, { $inc: { followersCount: -1 } });
  }
  await FollowRequest.findOneAndUpdate(
    { requester: currentUserId, recipient: targetUserId, status: "pending" },
    { status: "cancelled" }
  );
  return { updated: true, unfollowed: true, status: "not_following" };
};

const removeFollower = async (currentUserId, followerUserId) => {
  if (currentUserId.toString() === followerUserId.toString()) throw new ApiError(400, "Invalid follower");
  const deletedFollow = await Follow.findOneAndDelete({ follower: followerUserId, following: currentUserId });
  if (deletedFollow) {
    await User.findByIdAndUpdate(currentUserId, { $inc: { followersCount: -1 } });
    await User.findByIdAndUpdate(followerUserId, { $inc: { followingCount: -1 } });
  }
  return { updated: true, removed: true, status: "not_following" };
};

const getFollowers = async (username, query) => {
  const user = await User.findOne({ username: username.toLowerCase() });
  if (!user) throw new ApiError(404, "User not found");
  const { page, limit, skip } = getPagination(query);
  const [items, total] = await Promise.all([
    Follow.find({ following: user.id })
      .populate("follower", "username fullName avatar")
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .lean(),
    Follow.countDocuments({ following: user.id }),
  ]);
  return {
    items: items.map((item) => item.follower),
    meta: { page, limit, total, totalPages: Math.ceil(total / limit) },
  };
};

const getFollowing = async (username, query) => {
  const user = await User.findOne({ username: username.toLowerCase() });
  if (!user) throw new ApiError(404, "User not found");
  const { page, limit, skip } = getPagination(query);
  const [items, total] = await Promise.all([
    Follow.find({ follower: user.id })
      .populate("following", "username fullName avatar")
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .lean(),
    Follow.countDocuments({ follower: user.id }),
  ]);
  return {
    items: items.map((item) => item.following),
    meta: { page, limit, total, totalPages: Math.ceil(total / limit) },
  };
};

const getPendingFollowRequests = async (userId, query) => {
  const { page, limit, skip } = getPagination(query);
  const filter = { recipient: userId, status: "pending" };
  const [items, total] = await Promise.all([
    FollowRequest.find(filter)
      .populate("requester", "username fullName avatar")
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .lean(),
    FollowRequest.countDocuments(filter),
  ]);
  return { items, meta: { page, limit, total, totalPages: Math.ceil(total / limit) } };
};

const getPendingFollowRequestsCount = async (userId) =>
  FollowRequest.countDocuments({ recipient: userId, status: "pending" });

const getFollowStatusesForUsers = async (viewerId, targetUserIds = []) => {
  const ids = [...new Set(targetUserIds.map((id) => String(id)).filter(Boolean))];
  const statuses = new Map(ids.map((id) => [id, "not_following"]));

  if (!ids.length) {
    return statuses;
  }

  if (viewerId) {
    const viewerString = String(viewerId);
    if (ids.includes(viewerString)) {
      statuses.set(viewerString, "self");
    }

    const [followRows, requestRows] = await Promise.all([
      Follow.find({ follower: viewerId, following: { $in: ids } }).select("following").lean(),
      FollowRequest.find({ requester: viewerId, recipient: { $in: ids }, status: "pending" }).select("recipient").lean(),
    ]);

    followRows.forEach((row) => {
      statuses.set(String(row.following), "following");
    });

    requestRows.forEach((row) => {
      const recipientId = String(row.recipient);
      if (statuses.get(recipientId) !== "following") {
        statuses.set(recipientId, "requested");
      }
    });
  }

  return statuses;
};

const getFollowStatusForUser = async (viewerId, targetUserId) => {
  const statuses = await getFollowStatusesForUsers(viewerId, [targetUserId]);
  return statuses.get(String(targetUserId)) || "not_following";
};

const decideFollowRequest = async (currentUserId, requestId, action) => {
  const request = await FollowRequest.findById(requestId);
  if (!request || request.recipient.toString() !== currentUserId.toString()) {
    throw new ApiError(404, "Follow request not found");
  }
  if (request.status !== "pending") {
    throw new ApiError(400, "Follow request already processed");
  }

  request.status = action === "accept" ? "accepted" : "rejected";
  await request.save();

  if (action === "accept") {
    await createFollowRelation(request.requester, request.recipient);
    await createNotification({
      recipient: request.requester,
      actor: request.recipient,
      type: "follow_accept",
      followRequest: request.id,
      message: "accepted your follow request",
    });
  }

  return request;
};

module.exports = {
  followUser,
  unfollowUser,
  removeFollower,
  getFollowers,
  getFollowing,
  getPendingFollowRequests,
  getPendingFollowRequestsCount,
  decideFollowRequest,
  getFollowStatusForUser,
  getFollowStatusesForUsers,
};
