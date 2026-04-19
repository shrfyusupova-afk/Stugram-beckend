const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const followService = require("../services/followService");

const followUser = catchAsync(async (req, res) => {
  const result = await followService.followUser(req.user.id, req.params.userId);
  sendResponse(res, { message: "Follow action completed", data: result });
});

const unfollowUser = catchAsync(async (req, res) => {
  const result = await followService.unfollowUser(req.user.id, req.params.userId);
  sendResponse(res, { message: "Unfollow action completed", data: result });
});

const getFollowers = catchAsync(async (req, res) => {
  const result = await followService.getFollowers(req.params.username, req.query);
  sendResponse(res, { message: "Followers fetched successfully", data: result.items, meta: result.meta });
});

const getFollowing = catchAsync(async (req, res) => {
  const result = await followService.getFollowing(req.params.username, req.query);
  sendResponse(res, { message: "Following fetched successfully", data: result.items, meta: result.meta });
});

const getFollowRequests = catchAsync(async (req, res) => {
  const result = await followService.getPendingFollowRequests(req.user.id, req.query);
  sendResponse(res, { message: "Follow requests fetched successfully", data: result.items, meta: result.meta });
});

const acceptFollowRequest = catchAsync(async (req, res) => {
  const result = await followService.decideFollowRequest(req.user.id, req.params.requestId, "accept");
  sendResponse(res, { message: "Follow request accepted", data: result });
});

const rejectFollowRequest = catchAsync(async (req, res) => {
  const result = await followService.decideFollowRequest(req.user.id, req.params.requestId, "reject");
  sendResponse(res, { message: "Follow request rejected", data: result });
});

const removeFollower = catchAsync(async (req, res) => {
  const result = await followService.removeFollower(req.user.id, req.params.userId);
  sendResponse(res, { message: "Follower removed successfully", data: result });
});

module.exports = {
  followUser,
  unfollowUser,
  getFollowers,
  getFollowing,
  getFollowRequests,
  acceptFollowRequest,
  rejectFollowRequest,
  removeFollower,
};
