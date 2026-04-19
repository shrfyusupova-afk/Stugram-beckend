const express = require("express");

const groupChatController = require("../controllers/groupChatController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const { uploadGroupAvatar, uploadChatMedia } = require("../middlewares/upload");
const { messageSendLimiter, replyLimiter } = require("../middlewares/chatSecurity");
const {
  createGroupChatSchema,
  groupChatsListSchema,
  updateGroupChatSchema,
  groupIdParamSchema,
  groupMessagesSchema,
  groupConversationSearchSchema,
  sendGroupMessageSchema,
  forwardGroupMessageSchema,
  groupMessageReactionSchema,
  groupMessageIdSchema,
  groupMessageDeleteSchema,
  editGroupMessageSchema,
  pinGroupMessageSchema,
  unpinGroupMessageSchema,
  manageGroupMembersSchema,
  removeGroupMemberSchema,
  groupMessageSeenSchema,
  groupMembersListSchema,
  leaveGroupSchema,
} = require("../validators/groupChatValidators");

const router = express.Router();

router.post("/", requireAuth, uploadGroupAvatar, validate(createGroupChatSchema), groupChatController.createGroupChat);
router.get("/", requireAuth, validate(groupChatsListSchema), groupChatController.getGroupChats);
router.patch("/:groupId", requireAuth, uploadGroupAvatar, validate(updateGroupChatSchema), groupChatController.updateGroupChat);
router.get("/:groupId", requireAuth, validate(groupIdParamSchema), groupChatController.getGroupChatDetail);
router.get("/:groupId/members", requireAuth, validate(groupMembersListSchema), groupChatController.getGroupMembers);
router.get(
  "/:groupId/search",
  requireAuth,
  validate(groupConversationSearchSchema),
  groupChatController.searchGroupMessages
);
router.post("/:groupId/leave", requireAuth, validate(leaveGroupSchema), groupChatController.leaveGroupChat);
router.get("/:groupId/messages", requireAuth, validate(groupMessagesSchema), groupChatController.getGroupMessages);
router.post(
  "/:groupId/messages",
  requireAuth,
  messageSendLimiter,
  replyLimiter,
  uploadChatMedia,
  validate(sendGroupMessageSchema),
  groupChatController.sendGroupMessage
);
router.post(
  "/:groupId/messages/forward",
  requireAuth,
  messageSendLimiter,
  validate(forwardGroupMessageSchema),
  groupChatController.forwardGroupMessage
);
router.post("/:groupId/members", requireAuth, validate(manageGroupMembersSchema), groupChatController.addGroupMembers);
router.patch(
  "/:groupId/messages/:messageId",
  requireAuth,
  validate(editGroupMessageSchema),
  groupChatController.editGroupMessage
);
router.patch(
  "/:groupId/messages/:messageId/reaction",
  requireAuth,
  validate(groupMessageReactionSchema),
  groupChatController.updateGroupMessageReaction
);
router.delete(
  "/:groupId/messages/:messageId/reaction",
  requireAuth,
  validate(groupMessageIdSchema),
  groupChatController.removeGroupMessageReaction
);
router.patch(
  "/:groupId/messages/:messageId/seen",
  requireAuth,
  validate(groupMessageSeenSchema),
  groupChatController.markGroupMessageSeen
);
router.post(
  "/:groupId/pin/:messageId",
  requireAuth,
  validate(pinGroupMessageSchema),
  groupChatController.pinGroupMessage
);
router.delete(
  "/:groupId/pin",
  requireAuth,
  validate(unpinGroupMessageSchema),
  groupChatController.unpinGroupMessage
);
router.delete(
  "/:groupId/messages/:messageId",
  requireAuth,
  validate(groupMessageDeleteSchema),
  groupChatController.deleteGroupMessage
);
router.delete(
  "/:groupId/members/:userId",
  requireAuth,
  validate(removeGroupMemberSchema),
  groupChatController.removeGroupMember
);

module.exports = router;
