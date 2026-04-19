const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const groupChatService = require("../services/groupChatService");
const { getIo } = require("../socket/socketServer");
const { isUserConnected, emitMessageDelivered } = require("../socket/chatSocket");
const { sendPushToUser, buildChatMessagePushPayload, buildChatMessagePreviewText } = require("../services/pushNotificationService");
const GroupMessage = require("../models/GroupMessage");
const logger = require("../utils/logger");
const {
  emitGroupConversationUpdated,
  emitGroupMemberAdded,
  emitGroupMemberRemoved,
  emitGroupMessageSeen,
  emitGroupMessageReactionUpdated,
  emitGroupMessageDeleted,
  emitGroupMessageDeletedForEveryone,
  emitGroupMessageEdited,
  emitGroupMessageForwarded,
  emitGroupMessagePinned,
  emitGroupMessageUnpinned,
  emitNewGroupMessage,
} = require("../socket/chatSocket");

const sendGroupDeliverySignals = async ({ io, message, participantIds, groupId, senderId, senderName }) => {
  const recipientIds = participantIds.filter((participantId) => participantId !== senderId.toString());
  if (!recipientIds.length) return;

  const onlineRecipientIds = recipientIds.filter((recipientId) => isUserConnected(recipientId));
  const offlineRecipientIds = recipientIds.filter((recipientId) => !isUserConnected(recipientId));
  const previewText = buildChatMessagePreviewText(message);

  if (onlineRecipientIds.length) {
    const deliveredAt = new Date();
    await GroupMessage.findByIdAndUpdate(message._id, { deliveredAt }).catch(() => null);
    emitMessageDelivered(io, [senderId.toString()], {
      groupId: groupId.toString(),
      messageId: message._id.toString(),
      recipientIds: onlineRecipientIds,
      deliveredAt: deliveredAt.toISOString(),
    });
    logger.info("Group message delivered via socket", {
      groupId: groupId.toString(),
      messageId: message._id.toString(),
      recipientIds: onlineRecipientIds,
    });
  }

  await Promise.allSettled(
    offlineRecipientIds.map((recipientId) =>
      sendPushToUser(
        recipientId,
        buildChatMessagePushPayload({
          type: "group_chat",
          groupId: groupId.toString(),
          messageId: message._id.toString(),
          senderId: senderId.toString(),
          senderName,
          previewText,
          mediaType: message.messageType !== "text" ? message.messageType : null,
        }),
        {
          notificationType: "message",
          recipientId,
          groupId: groupId.toString(),
          messageId: message._id.toString(),
          senderId: senderId.toString(),
        }
      )
    )
  );
};

const createGroupChat = catchAsync(async (req, res) => {
  const result = await groupChatService.createGroupChat(req.user.id, req.body, req.file || null);
  const io = getIo();

  await emitGroupConversationUpdated(io, result.participantIds, result.group._id);

  sendResponse(res, {
    statusCode: 201,
    message: "Group chat created successfully",
    data: result.group,
  });
});

const getGroupChats = catchAsync(async (req, res) => {
  const result = await groupChatService.getGroupChats(req.user.id, req.query);
  sendResponse(res, {
    message: "Group chats fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const getGroupChatDetail = catchAsync(async (req, res) => {
  const group = await groupChatService.getGroupChatById(req.user.id, req.params.groupId);
  sendResponse(res, {
    message: "Group chat fetched successfully",
    data: group,
  });
});

const getGroupMembers = catchAsync(async (req, res) => {
  const result = await groupChatService.getGroupMembers(req.user.id, req.params.groupId, req.query);
  sendResponse(res, {
    message: "Group members fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const getGroupMessages = catchAsync(async (req, res) => {
  const result = await groupChatService.getGroupMessages(req.user.id, req.params.groupId, req.query);
  sendResponse(res, {
    message: "Group messages fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const searchGroupMessages = catchAsync(async (req, res) => {
  const result = await groupChatService.searchGroupMessages(req.user.id, req.params.groupId, req.query);
  sendResponse(res, {
    message: "Group message search fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const sendGroupMessage = catchAsync(async (req, res) => {
  const result = await groupChatService.sendGroupMessage(req.user.id, req.params.groupId, req.body, req.file || null);
  const io = getIo();

  emitNewGroupMessage(io, result.participantIds, {
    groupId: result.groupId,
    message: result.message,
  });
  await emitGroupConversationUpdated(io, result.participantIds, result.groupId);
  await sendGroupDeliverySignals({
    io,
    message: result.message,
    participantIds: result.participantIds,
    groupId: result.groupId,
    senderId: req.user.id,
    senderName: result.message.sender?.fullName || result.message.sender?.username || "New message",
  });

  sendResponse(res, {
    statusCode: 201,
    message: "Group message sent successfully",
    data: result.message,
  });
});

const forwardGroupMessage = catchAsync(async (req, res) => {
  const result = await groupChatService.forwardGroupMessage(req.user.id, req.params.groupId, req.body);
  const io = getIo();

  emitNewGroupMessage(io, result.participantIds, {
    groupId: result.groupId,
    message: result.message,
  });
  emitGroupMessageForwarded(io, result.participantIds, {
    groupId: result.groupId,
    message: result.message,
  });
  await emitGroupConversationUpdated(io, result.participantIds, result.groupId);
  await sendGroupDeliverySignals({
    io,
    message: result.message,
    participantIds: result.participantIds,
    groupId: result.groupId,
    senderId: req.user.id,
    senderName: result.message.sender?.fullName || result.message.sender?.username || "New message",
  });

  sendResponse(res, {
    statusCode: 201,
    message: "Group message forwarded successfully",
    data: result.message,
  });
});

const updateGroupMessageReaction = catchAsync(async (req, res) => {
  const result = await groupChatService.updateGroupMessageReaction(
    req.user.id,
    req.params.groupId,
    req.params.messageId,
    req.body.emoji
  );
  const io = getIo();

  emitGroupMessageReactionUpdated(io, result.participantIds, {
    groupId: result.groupId,
    message: result.message,
  });
  await emitGroupConversationUpdated(io, result.participantIds, result.groupId);

  sendResponse(res, {
    message: "Group message reaction updated successfully",
    data: {
      updated: true,
      message: result.message,
    },
  });
});

const editGroupMessage = catchAsync(async (req, res) => {
  const result = await groupChatService.editGroupMessage(
    req.user.id,
    req.params.groupId,
    req.params.messageId,
    req.body
  );
  const io = getIo();

  emitGroupMessageEdited(io, result.participantIds, {
    groupId: result.groupId,
    message: result.message,
  });
  await emitGroupConversationUpdated(io, result.participantIds, result.groupId);

  sendResponse(res, {
    message: "Group message edited successfully",
    data: {
      updated: true,
      message: result.message,
    },
  });
});

const removeGroupMessageReaction = catchAsync(async (req, res) => {
  const result = await groupChatService.removeGroupMessageReaction(req.user.id, req.params.groupId, req.params.messageId);
  const io = getIo();

  emitGroupMessageReactionUpdated(io, result.participantIds, {
    groupId: result.groupId,
    message: result.message,
  });
  await emitGroupConversationUpdated(io, result.participantIds, result.groupId);

  sendResponse(res, {
    message: "Group message reaction removed successfully",
    data: {
      updated: true,
      message: result.message,
    },
  });
});

const deleteGroupMessage = catchAsync(async (req, res) => {
  const result = await groupChatService.deleteGroupMessage(
    req.user.id,
    req.params.groupId,
    req.params.messageId,
    req.body?.scope || "self"
  );
  const io = getIo();

  if (result.deletedForEveryone) {
    emitGroupMessageDeletedForEveryone(io, result.participantIds, {
      groupId: result.groupId,
      messageId: req.params.messageId,
      deletedByUserId: req.user.id,
      deletedAt: result.deletedAt,
      message: result.message,
    });
  } else {
    emitGroupMessageDeleted(io, result.participantIds, {
      groupId: result.groupId,
      messageId: req.params.messageId,
      deletedByUserId: req.user.id,
      deletedForUserId: req.user.id,
    });
  }
  await emitGroupConversationUpdated(io, result.participantIds, result.groupId);

  sendResponse(res, {
    message: "Group message deleted successfully",
    data: result,
  });
});

const pinGroupMessage = catchAsync(async (req, res) => {
  const result = await groupChatService.pinGroupMessage(req.user.id, req.params.groupId, req.params.messageId);
  const io = getIo();

  emitGroupMessagePinned(io, result.participantIds, {
    groupId: result.groupId,
    group: result.group,
  });
  await emitGroupConversationUpdated(io, result.participantIds, result.groupId);

  sendResponse(res, {
    message: "Group message pinned successfully",
    data: {
      updated: true,
      group: result.group,
    },
  });
});

const unpinGroupMessage = catchAsync(async (req, res) => {
  const result = await groupChatService.unpinGroupMessage(req.user.id, req.params.groupId);
  const io = getIo();

  emitGroupMessageUnpinned(io, result.participantIds, {
    groupId: result.groupId,
    group: result.group,
  });
  await emitGroupConversationUpdated(io, result.participantIds, result.groupId);

  sendResponse(res, {
    message: "Group message unpinned successfully",
    data: {
      updated: true,
      group: result.group,
    },
  });
});

const addGroupMembers = catchAsync(async (req, res) => {
  const result = await groupChatService.addGroupMembers(req.user.id, req.params.groupId, req.body);
  const io = getIo();

  emitGroupMemberAdded(io, result.addedMemberIds, {
    groupId: result.group._id,
    group: result.group,
  });
  await emitGroupConversationUpdated(io, result.participantIds, result.group._id);

  sendResponse(res, {
    message: "Group members updated successfully",
    data: {
      updated: true,
      group: result.group,
      participantIds: result.participantIds,
      addedMemberIds: result.addedMemberIds,
      membersCount: result.membersCount || result.group?.membersCount || result.participantIds?.length || 0,
    },
  });
});

const updateGroupChat = catchAsync(async (req, res) => {
  const result = await groupChatService.updateGroupChat(req.user.id, req.params.groupId, req.body, req.file || null);
  const io = getIo();

  await emitGroupConversationUpdated(io, result.participantIds, result.group._id);

  sendResponse(res, {
    message: "Group chat updated successfully",
    data: {
      updated: true,
      group: result.group,
    },
  });
});

const removeGroupMember = catchAsync(async (req, res) => {
  const result = await groupChatService.removeGroupMember(req.user.id, req.params.groupId, req.params.userId);
  const io = getIo();

  emitGroupMemberRemoved(io, [req.params.userId], {
    groupId: result.groupId,
    userId: result.userId,
  });
  await emitGroupConversationUpdated(io, result.participantIds, result.groupId);

  sendResponse(res, {
    message: "Group member removed successfully",
    data: {
      updated: true,
      ...result,
    },
  });
});

const leaveGroupChat = catchAsync(async (req, res) => {
  const result = await groupChatService.leaveGroupChat(req.user.id, req.params.groupId);
  const io = getIo();

  emitGroupMemberRemoved(io, [req.user.id], {
    groupId: result.groupId,
    userId: result.userId,
  });
  await emitGroupConversationUpdated(io, result.participantIds, result.groupId);

  sendResponse(res, {
    message: "Left group chat successfully",
    data: {
      updated: true,
      ...result,
    },
  });
});

const markGroupMessageSeen = catchAsync(async (req, res) => {
  const result = await groupChatService.markGroupMessageSeen(req.user.id, req.params.groupId, req.params.messageId);
  const io = getIo();

  emitGroupMessageSeen(io, result.participantIds, {
    groupId: result.groupId,
    message: result.message,
    seenByUserId: req.user.id,
    seenAt: result.seenAt,
    readAt: result.message.readAt,
  });
  await emitGroupConversationUpdated(io, result.participantIds, result.groupId);

  sendResponse(res, {
    message: "Group message marked as seen",
    data: result.message,
  });
});

module.exports = {
  createGroupChat,
  getGroupChats,
  getGroupChatDetail,
  getGroupMembers,
  getGroupMessages,
  searchGroupMessages,
  sendGroupMessage,
  forwardGroupMessage,
  updateGroupMessageReaction,
  editGroupMessage,
  removeGroupMessageReaction,
  deleteGroupMessage,
  pinGroupMessage,
  unpinGroupMessage,
  addGroupMembers,
  updateGroupChat,
  removeGroupMember,
  leaveGroupChat,
  markGroupMessageSeen,
};
