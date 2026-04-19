const express = require("express");

const chatController = require("../controllers/chatController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const { uploadChatMedia } = require("../middlewares/upload");
const { messageSendLimiter, reactionLimiter, replyLimiter } = require("../middlewares/chatSecurity");
const {
  createConversationSchema,
  conversationsListSchema,
  chatSearchQuerySchema,
  conversationSearchSchema,
  conversationMessagesSchema,
  conversationIdParamSchema,
  sendMessageSchema,
  sendMediaMessageSchema,
  messageIdSchema,
  messageDeleteSchema,
  editMessageSchema,
  pinMessageSchema,
  unpinMessageSchema,
  forwardMessageSchema,
  updateReactionSchema,
  userIdParamSchema,
  reportUserSchema,
  muteConversationSchema,
} = require("../validators/chatValidators");

const router = express.Router();

router.post("/conversations", requireAuth, validate(createConversationSchema), chatController.createConversation);
router.get("/conversations", requireAuth, validate(conversationsListSchema), chatController.getConversations);
router.get("/conversations/search", requireAuth, validate(chatSearchQuerySchema), chatController.searchConversations);
router.get("/summary", requireAuth, chatController.getSummary);
router.get("/unread-count", requireAuth, chatController.getUnreadCount);
router.get(
  "/conversations/:conversationId/search",
  requireAuth,
  validate(conversationSearchSchema),
  chatController.searchConversationMessages
);
router.get(
  "/conversations/:conversationId/messages",
  requireAuth,
  validate(conversationMessagesSchema),
  chatController.getConversationMessages
);
router.get(
  "/conversations/:conversationId",
  requireAuth,
  validate(conversationIdParamSchema),
  chatController.getConversationById
);
router.post(
  "/conversations/:conversationId/messages",
  requireAuth,
  messageSendLimiter,
  replyLimiter,
  validate(sendMessageSchema),
  chatController.sendMessage
);
router.post(
  "/conversations/:conversationId/messages/media",
  requireAuth,
  messageSendLimiter,
  replyLimiter,
  uploadChatMedia,
  validate(sendMediaMessageSchema),
  chatController.sendMediaMessage
);
router.patch("/messages/:messageId/seen", requireAuth, validate(messageIdSchema), chatController.markMessageSeen);
router.patch("/messages/:messageId/reaction", requireAuth, reactionLimiter, validate(updateReactionSchema), chatController.updateReaction);
router.delete("/messages/:messageId/reaction", requireAuth, reactionLimiter, validate(messageIdSchema), chatController.removeReaction);
router.patch("/messages/:messageId", requireAuth, validate(editMessageSchema), chatController.editMessage);
router.post(
  "/conversations/:conversationId/messages/forward",
  requireAuth,
  messageSendLimiter,
  validate(forwardMessageSchema),
  chatController.forwardMessage
);
router.post(
  "/conversations/:conversationId/pin/:messageId",
  requireAuth,
  validate(pinMessageSchema),
  chatController.pinMessage
);
router.delete(
  "/conversations/:conversationId/pin",
  requireAuth,
  validate(unpinMessageSchema),
  chatController.unpinMessage
);
router.delete("/messages/:messageId", requireAuth, validate(messageDeleteSchema), chatController.deleteMessage);
router.post("/users/:userId/block", requireAuth, validate(userIdParamSchema), chatController.blockUser);
router.delete("/users/:userId/block", requireAuth, validate(userIdParamSchema), chatController.unblockUser);
router.post("/reports", requireAuth, validate(reportUserSchema), chatController.reportUser);
router.post("/conversations/:conversationId/mute", requireAuth, validate(muteConversationSchema), chatController.muteConversation);
router.delete("/conversations/:conversationId/mute", requireAuth, validate(muteConversationSchema), chatController.unmuteConversation);

module.exports = router;
