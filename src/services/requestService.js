const ApiError = require("../utils/ApiError");
const FollowRequest = require("../models/FollowRequest");
const { getPagination, buildPaginationMeta } = require("../utils/pagination");
const chatService = require("./chatService");
const blockService = require("./blockService");

const buildNotFoundError = () => new ApiError(404, "Request not found", { code: "REQUEST_NOT_FOUND" });

const loadPendingRequestForRecipient = async (recipientId, requestId) => {
  const request = await FollowRequest.findById(requestId).populate("requester", "username fullName avatar");
  if (!request) throw buildNotFoundError();
  if (request.recipient.toString() !== recipientId.toString()) {
    throw new ApiError(403, "You cannot modify this request", { code: "REQUEST_FORBIDDEN" });
  }
  if (request.status !== "pending") {
    throw new ApiError(409, "Request already resolved", { code: "REQUEST_ALREADY_RESOLVED" });
  }
  return request;
};

const formatRequest = (request) => ({
  id: request.id,
  type: "follow",
  fromUser: {
    id: request.requester?._id?.toString?.() || request.requester?.toString?.() || null,
    name: request.requester?.fullName || "",
    username: request.requester?.username || "",
    avatarUrl: request.requester?.avatar || null,
  },
  createdAt: request.createdAt,
});

const listRequests = async (currentUserId, query = {}) => {
  const { page, limit, skip } = getPagination(query);
  const filter = { recipient: currentUserId, status: "pending" };
  const [items, total] = await Promise.all([
    FollowRequest.find(filter)
      .populate("requester", "username fullName avatar")
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .lean(),
    FollowRequest.countDocuments(filter),
  ]);

  return {
    requests: items.map(formatRequest),
    meta: buildPaginationMeta({ page, limit, total }),
  };
};

const addToChat = async (currentUserId, requestId) => {
  const request = await loadPendingRequestForRecipient(currentUserId, requestId);
  const requesterId = request.requester?._id || request.requester;

  let conversation;
  try {
    conversation = await chatService.createConversation(currentUserId, requesterId.toString());
  } catch (error) {
    if (error.statusCode === 403) {
      throw new ApiError(403, error.message || "User is blocked", { code: "USER_BLOCKED" });
    }
    throw new ApiError(500, "Conversation create failed", { code: "CONVERSATION_CREATE_FAILED" });
  }

  request.status = "cancelled";
  await request.save();

  return {
    requestId: request.id,
    conversation,
    resolved: true,
  };
};

const blockFromRequest = async (currentUserId, requestId, meta = {}) => {
  const request = await loadPendingRequestForRecipient(currentUserId, requestId);
  const requesterId = request.requester?._id || request.requester;

  try {
    await blockService.blockUser(currentUserId, requesterId.toString(), meta);
  } catch (_error) {
    throw new ApiError(500, "Failed to block user", { code: "BLOCK_FAILED" });
  }

  request.status = "cancelled";
  await request.save();

  return {
    requestId: request.id,
    blockedUserId: requesterId.toString(),
    resolved: true,
  };
};

const removeRequest = async (currentUserId, requestId) => {
  const request = await loadPendingRequestForRecipient(currentUserId, requestId);
  request.status = "rejected";
  await request.save();
  return { requestId: request.id, removed: true };
};

module.exports = {
  listRequests,
  addToChat,
  blockFromRequest,
  removeRequest,
};
