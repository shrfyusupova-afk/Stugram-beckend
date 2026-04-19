const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const chatService = require("../services/chatService");
const chatSecurityService = require("../services/chatSecurityService");
const { getIo } = require("../socket/socketServer");
const { isUserConnected, emitMessageDelivered } = require("../socket/chatSocket");
const { sendPushToUser, buildChatMessagePushPayload, buildChatMessagePreviewText } = require("../services/pushNotificationService");
const Message = require("../models/Message");
const logger = require("../utils/logger");
const {
  emitConversationUpdated,
  emitMessageSeen,
  emitNewMessage,
  emitMessageReactionUpdated,
  emitMessageDeleted,
  emitMessageDeletedForEveryone,
  emitMessageEdited,
  emitMessageForwarded,
  emitMessagePinned,
  emitMessageUnpinned,
} = require("../socket/chatSocket");

const getRequestMeta = (req) => ({
  ipAddress: req.ip,
  userAgent: req.headers["user-agent"] || null,
});

const sendDeliverySignals = async ({ io, message, participantIds, conversationId, senderId, senderName }) => {
  const recipientIds = participantIds.filter((participantId) => participantId !== senderId.toString());
  if (!recipientIds.length) return;

  const onlineRecipientIds = recipientIds.filter((recipientId) => isUserConnected(recipientId));
  const offlineRecipientIds = recipientIds.filter((recipientId) => !isUserConnected(recipientId));
  const previewText = buildChatMessagePreviewText(message);

  if (onlineRecipientIds.length) {
    const deliveredAt = new Date();
    await Message.findByIdAndUpdate(message._id, { deliveredAt }).catch(() => null);
    emitMessageDelivered(io, [senderId.toString()], {
      conversationId: conversationId.toString(),
      messageId: message._id.toString(),
      recipientIds: onlineRecipientIds,
      deliveredAt: deliveredAt.toISOString(),
    });
    logger.info("Message delivered via socket", {
      conversationId: conversationId.toString(),
      messageId: message._id.toString(),
      recipientIds: onlineRecipientIds,
    });
  }

  await Promise.allSettled(
    offlineRecipientIds.map((recipientId) =>
      sendPushToUser(
        recipientId,
        buildChatMessagePushPayload({
          type: "chat",
          conversationId: conversationId.toString(),
          messageId: message._id.toString(),
          senderId: senderId.toString(),
          senderName,
          previewText,
          mediaType: message.messageType !== "text" ? message.messageType : null,
        }),
        {
          notificationType: "message",
          recipientId,
          conversationId: conversationId.toString(),
          messageId: message._id.toString(),
          senderId: senderId.toString(),
        }
      )
    )
  );
};

const createConversation = catchAsync(async (req, res) => {
  const conversation = await chatService.createConversation(req.user.id, req.body.participantId);
  sendResponse(res, {
    statusCode: 201,
    message: "Conversation ready",
    data: conversation,
  });
});

const getConversations = catchAsync(async (req, res) => {
  const result = await chatService.getConversations(req.user.id, req.query);
  sendResponse(res, {
    message: "Conversations fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const searchConversations = catchAsync(async (req, res) => {
  const result = await chatService.searchConversations(req.user.id, req.query);
  sendResponse(res, {
    message: "Conversation search fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const getUnreadCount = catchAsync(async (req, res) => {
  const result = await chatService.getTotalUnreadCount(req.user.id);
  sendResponse(res, {
    message: "Unread count fetched successfully",
    data: result,
  });
});

const getSummary = catchAsync(async (req, res) => {
  const result = await chatService.getChatSummary(req.user.id);
  sendResponse(res, {
    message: "Chat summary fetched successfully",
    data: result,
  });
});

const getConversationMessages = catchAsync(async (req, res) => {
  const result = await chatService.getConversationMessages(req.user.id, req.params.conversationId, req.query);
  sendResponse(res, {
    message: "Messages fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const getConversationById = catchAsync(async (req, res) => {
  const result = await chatService.getConversationByIdForUser(req.user.id, req.params.conversationId);
  sendResponse(res, {
    message: "Conversation fetched successfully",
    data: result,
  });
});

const searchConversationMessages = catchAsync(async (req, res) => {
  const result = await chatService.searchConversationMessages(req.user.id, req.params.conversationId, req.query);
  sendResponse(res, {
    message: "Conversation message search fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const sendMessage = catchAsync(async (req, res) => {
  const result = await chatService.sendMessage(req.user.id, req.params.conversationId, req.body);
  const io = getIo();

  emitNewMessage(io, result.participantIds, result.message);
  await emitConversationUpdated(io, result.participantIds, result.conversationId);
  await sendDeliverySignals({
    io,
    message: result.message,
    participantIds: result.participantIds,
    conversationId: result.conversationId,
    senderId: req.user.id,
    senderName: result.message.sender?.fullName || result.message.sender?.username || "New message",
  });

  sendResponse(res, {
    statusCode: 201,
    message: "Message sent successfully",
    data: result.message,
  });
});

const sendMediaMessage = catchAsync(async (req, res) => {
  const result = await chatService.sendMediaMessage(req.user.id, req.params.conversationId, req.body, req.file);
  const io = getIo();

  emitNewMessage(io, result.participantIds, result.message);
  await emitConversationUpdated(io, result.participantIds, result.conversationId);
  await sendDeliverySignals({
    io,
    message: result.message,
    participantIds: result.participantIds,
    conversationId: result.conversationId,
    senderId: req.user.id,
    senderName: result.message.sender?.fullName || result.message.sender?.username || "New message",
  });

  sendResponse(res, {
    statusCode: 201,
    message: "Media message sent successfully",
    data: result.message,
  });
});

const markMessageSeen = catchAsync(async (req, res) => {
  const result = await chatService.markMessageSeen(req.user.id, req.params.messageId);
  const io = getIo();

  emitMessageSeen(io, result.participantIds, {
    conversationId: result.conversationId,
    message: result.message,
    seenByUserId: req.user.id,
    seenAt: result.seenAt,
    readAt: result.message.readAt,
  });
  await emitConversationUpdated(io, result.participantIds, result.conversationId);

  sendResponse(res, {
    message: "Message marked as seen",
    data: result.message,
  });
});

const deleteMessage = catchAsync(async (req, res) => {
  const scope = req.body?.scope || "self";
  const result = await chatService.deleteMessage(req.user.id, req.params.messageId, scope);
  const io = getIo();

  if (result.deletedForEveryone) {
    emitMessageDeletedForEveryone(io, result.participantIds, {
      conversationId: result.conversationId,
      messageId: req.params.messageId,
      deletedByUserId: req.user.id,
      deletedAt: result.deletedAt,
      message: result.message,
    });
  } else {
    emitMessageDeleted(io, result.participantIds, {
      conversationId: result.conversationId,
      messageId: req.params.messageId,
      deletedByUserId: req.user.id,
      deletedForUserId: req.user.id,
    });
  }
  await emitConversationUpdated(io, result.participantIds, result.conversationId);

  sendResponse(res, {
    message: "Message deleted successfully",
    data: result,
  });
});

const editMessage = catchAsync(async (req, res) => {
  const result = await chatService.editMessage(req.user.id, req.params.messageId, req.body);
  const io = getIo();

  emitMessageEdited(io, result.participantIds, {
    conversationId: result.conversationId,
    message: result.message,
  });
  await emitConversationUpdated(io, result.participantIds, result.conversationId);

  sendResponse(res, {
    message: "Message edited successfully",
    data: result.message,
  });
});

const forwardMessage = catchAsync(async (req, res) => {
  const result = await chatService.forwardMessage(req.user.id, req.params.conversationId, req.body);
  const io = getIo();

  emitNewMessage(io, result.participantIds, result.message);
  emitMessageForwarded(io, result.participantIds, {
    conversationId: result.conversationId,
    message: result.message,
  });
  await emitConversationUpdated(io, result.participantIds, result.conversationId);
  await sendDeliverySignals({
    io,
    message: result.message,
    participantIds: result.participantIds,
    conversationId: result.conversationId,
    senderId: req.user.id,
    senderName: result.message.sender?.fullName || result.message.sender?.username || "New message",
  });

  sendResponse(res, {
    statusCode: 201,
    message: "Message forwarded successfully",
    data: result.message,
  });
});

const pinMessage = catchAsync(async (req, res) => {
  const result = await chatService.pinMessage(req.user.id, req.params.conversationId, req.params.messageId);
  const io = getIo();

  emitMessagePinned(io, result.participantIds, {
    conversationId: result.conversationId,
    conversation: result.conversation,
  });
  await emitConversationUpdated(io, result.participantIds, result.conversationId);

  sendResponse(res, {
    message: "Message pinned successfully",
    data: result.conversation,
  });
});

const unpinMessage = catchAsync(async (req, res) => {
  const result = await chatService.unpinMessage(req.user.id, req.params.conversationId);
  const io = getIo();

  emitMessageUnpinned(io, result.participantIds, {
    conversationId: result.conversationId,
    conversation: result.conversation,
  });
  await emitConversationUpdated(io, result.participantIds, result.conversationId);

  sendResponse(res, {
    message: "Message unpinned successfully",
    data: result.conversation,
  });
});

const updateReaction = catchAsync(async (req, res) => {
  const result = await chatService.updateMessageReaction(req.user.id, req.params.messageId, req.body.emoji);
  const io = getIo();

  emitMessageReactionUpdated(io, result.participantIds, {
    conversationId: result.conversationId,
    message: result.message,
  });
  await emitConversationUpdated(io, result.participantIds, result.conversationId);

  sendResponse(res, {
    message: "Message reaction updated successfully",
    data: result.message,
  });
});

const removeReaction = catchAsync(async (req, res) => {
  const result = await chatService.removeMessageReaction(req.user.id, req.params.messageId);
  const io = getIo();

  emitMessageReactionUpdated(io, result.participantIds, {
    conversationId: result.conversationId,
    message: result.message,
  });
  await emitConversationUpdated(io, result.participantIds, result.conversationId);

  sendResponse(res, {
    message: "Message reaction removed successfully",
    data: result.message,
  });
});

const blockUser = catchAsync(async (req, res) => {
  const result = await chatSecurityService.blockUser(req.user.id, req.params.userId, getRequestMeta(req));
  sendResponse(res, {
    statusCode: 201,
    message: "User blocked successfully",
    data: result,
  });
});

const unblockUser = catchAsync(async (req, res) => {
  const result = await chatSecurityService.unblockUser(req.user.id, req.params.userId, getRequestMeta(req));
  sendResponse(res, {
    message: "User unblocked successfully",
    data: result,
  });
});

const reportUser = catchAsync(async (req, res) => {
  const result = await chatSecurityService.reportUser(req.user.id, req.body, getRequestMeta(req));
  sendResponse(res, {
    statusCode: 201,
    message: "User reported successfully",
    data: result,
  });
});

const muteConversation = catchAsync(async (req, res) => {
  const result = await chatSecurityService.muteConversation(
    req.user.id,
    req.params.conversationId,
    { mutedUntil: req.body.mutedUntil ? new Date(req.body.mutedUntil) : null },
    getRequestMeta(req)
  );
  sendResponse(res, {
    message: "Conversation muted successfully",
    data: result,
  });
});

const unmuteConversation = catchAsync(async (req, res) => {
  const result = await chatSecurityService.unmuteConversation(req.user.id, req.params.conversationId, getRequestMeta(req));
  sendResponse(res, {
    message: "Conversation unmuted successfully",
    data: result,
  });
});

module.exports = {
  createConversation,
  getConversations,
  searchConversations,
  getSummary,
  getUnreadCount,
  getConversationMessages,
  getConversationById,
  searchConversationMessages,
  sendMessage,
  sendMediaMessage,
  markMessageSeen,
  deleteMessage,
  editMessage,
  forwardMessage,
  pinMessage,
  unpinMessage,
  updateReaction,
  removeReaction,
  blockUser,
  unblockUser,
  reportUser,
  muteConversation,
  unmuteConversation,
};
