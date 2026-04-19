const ApiError = require("../utils/ApiError");
const Block = require("../models/Block");
const UserReport = require("../models/UserReport");
const Conversation = require("../models/Conversation");
const { createAuditLog } = require("./auditLogService");

const ensureNotBlockedBetweenUsers = async (firstUserId, secondUserId) => {
  const block = await Block.findOne({
    $or: [
      { blocker: firstUserId, blocked: secondUserId },
      { blocker: secondUserId, blocked: firstUserId },
    ],
  });

  if (block) {
    throw new ApiError(403, "Blocked users cannot interact in chat");
  }
};

const blockUser = async (currentUserId, targetUserId, meta = {}) => {
  if (currentUserId.toString() === targetUserId.toString()) {
    throw new ApiError(400, "You cannot block yourself");
  }

  const block = await Block.findOneAndUpdate(
    { blocker: currentUserId, blocked: targetUserId },
    { $setOnInsert: { blocker: currentUserId, blocked: targetUserId } },
    { upsert: true, new: true }
  );

  await createAuditLog({
    actor: currentUserId,
    action: "chat.block_user",
    category: "abuse",
    status: "warning",
    targetUser: targetUserId,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
  });

  return block;
};

const unblockUser = async (currentUserId, targetUserId, meta = {}) => {
  await Block.findOneAndDelete({ blocker: currentUserId, blocked: targetUserId });
  await createAuditLog({
    actor: currentUserId,
    action: "chat.unblock_user",
    category: "abuse",
    status: "success",
    targetUser: targetUserId,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
  });
  return { unblocked: true };
};

const reportUser = async (currentUserId, payload, meta = {}) => {
  const report = await UserReport.create({
    reporter: currentUserId,
    reportedUser: payload.reportedUserId,
    conversation: payload.conversationId || null,
    message: payload.messageId || null,
    reason: payload.reason,
    details: payload.details || "",
  });

  await createAuditLog({
    actor: currentUserId,
    action: "chat.report_user",
    category: "abuse",
    status: "warning",
    targetUser: payload.reportedUserId,
    conversation: payload.conversationId || null,
    message: payload.messageId || null,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
    details: { reason: payload.reason },
  });

  return report;
};

const muteConversation = async (currentUserId, conversationId, payload, meta = {}) => {
  const conversation = await Conversation.findOne({ _id: conversationId, participants: currentUserId });
  if (!conversation) throw new ApiError(404, "Conversation not found");

  conversation.mutedBy = conversation.mutedBy.filter((item) => item.user.toString() !== currentUserId.toString());
  conversation.mutedBy.push({
    user: currentUserId,
    mutedUntil: payload.mutedUntil || null,
  });
  await conversation.save();

  await createAuditLog({
    actor: currentUserId,
    action: "chat.mute_conversation",
    category: "chat",
    status: "success",
    conversation: conversationId,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
  });

  return { muted: true, mutedUntil: payload.mutedUntil || null };
};

const unmuteConversation = async (currentUserId, conversationId, meta = {}) => {
  const conversation = await Conversation.findOne({ _id: conversationId, participants: currentUserId });
  if (!conversation) throw new ApiError(404, "Conversation not found");

  conversation.mutedBy = conversation.mutedBy.filter((item) => item.user.toString() !== currentUserId.toString());
  await conversation.save();

  await createAuditLog({
    actor: currentUserId,
    action: "chat.unmute_conversation",
    category: "chat",
    status: "success",
    conversation: conversationId,
    ipAddress: meta.ipAddress || null,
    userAgent: meta.userAgent || null,
  });

  return { muted: false };
};

module.exports = {
  ensureNotBlockedBetweenUsers,
  blockUser,
  unblockUser,
  reportUser,
  muteConversation,
  unmuteConversation,
};
