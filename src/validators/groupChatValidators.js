const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("./commonValidators");
const groupMessageTypeSchema = z.enum(["text", "image", "video", "voice", "round_video", "file"]);

const parseIdList = (value) => {
  if (Array.isArray(value)) return value;
  if (typeof value !== "string") return value;

  const trimmed = value.trim();
  if (!trimmed) return [];

  if (trimmed.startsWith("[")) {
    try {
      return JSON.parse(trimmed);
    } catch (_error) {
      return value;
    }
  }

  return trimmed.split(",").map((item) => item.trim()).filter(Boolean);
};

const createGroupChatSchema = {
  body: z.object({
    name: z.string().trim().min(1).max(120),
    memberIds: z.preprocess(parseIdList, z.array(objectIdSchema).min(1).max(100)),
  }),
};

const groupChatsListSchema = {
  query: paginationQuerySchema,
};

const updateGroupChatSchema = {
  params: z.object({
    groupId: objectIdSchema,
  }),
  body: z.object({
    name: z.string().trim().min(1).max(120).optional(),
  }),
};

const groupIdParamSchema = {
  params: z.object({
    groupId: objectIdSchema,
  }),
};

const groupMessagesSchema = {
  params: z.object({
    groupId: objectIdSchema,
  }),
  query: paginationQuerySchema,
};

const groupConversationSearchSchema = {
  params: z.object({
    groupId: objectIdSchema,
  }),
  query: paginationQuerySchema.extend({
    q: z.string().trim().min(1).max(60),
  }),
};

const sendGroupMessageSchema = {
  params: z.object({
    groupId: objectIdSchema,
  }),
  body: z.object({
    text: z.string().trim().max(2000).optional(),
    messageType: groupMessageTypeSchema.default("text"),
    replyToMessageId: objectIdSchema.optional(),
  }),
};

const groupConversationMessageParamSchema = {
  params: z.object({
    groupId: objectIdSchema,
    messageId: objectIdSchema,
  }),
};

const groupMessageDeleteSchema = {
  params: z.object({
    groupId: objectIdSchema,
    messageId: objectIdSchema,
  }),
  body: z.object({
    scope: z.enum(["self", "everyone"]).default("self"),
  }),
};

const editGroupMessageSchema = {
  params: z.object({
    groupId: objectIdSchema,
    messageId: objectIdSchema,
  }),
  body: z.object({
    text: z.string().trim().min(1).max(2000),
  }),
};

const pinGroupMessageSchema = {
  params: z.object({
    groupId: objectIdSchema,
    messageId: objectIdSchema,
  }),
};

const unpinGroupMessageSchema = {
  params: z.object({
    groupId: objectIdSchema,
  }),
};

const forwardGroupMessageSchema = {
  params: z.object({
    groupId: objectIdSchema,
  }),
  body: z.object({
    sourceMessageId: objectIdSchema,
    comment: z.string().trim().max(2000).optional(),
  }),
};

const groupMessageReactionSchema = {
  params: z.object({
    groupId: objectIdSchema,
    messageId: objectIdSchema,
  }),
  body: z.object({
    emoji: z.string().trim().min(1).max(20),
  }),
};

const groupMessageIdSchema = {
  params: z.object({
    groupId: objectIdSchema,
    messageId: objectIdSchema,
  }),
};

const manageGroupMembersSchema = {
  params: z.object({
    groupId: objectIdSchema,
  }),
  body: z.object({
    memberIds: z.preprocess(parseIdList, z.array(objectIdSchema).min(1).max(100)),
  }),
};

const removeGroupMemberSchema = {
  params: z.object({
    groupId: objectIdSchema,
    userId: objectIdSchema,
  }),
};

const groupMessageSeenSchema = {
  params: z.object({
    groupId: objectIdSchema,
    messageId: objectIdSchema,
  }),
};

const groupMembersListSchema = {
  params: z.object({
    groupId: objectIdSchema,
  }),
  query: paginationQuerySchema,
};

const leaveGroupSchema = {
  params: z.object({
    groupId: objectIdSchema,
  }),
};

module.exports = {
  createGroupChatSchema,
  groupChatsListSchema,
  updateGroupChatSchema,
  groupIdParamSchema,
  groupMessagesSchema,
  groupConversationSearchSchema,
  sendGroupMessageSchema,
  groupConversationMessageParamSchema,
  groupMessageDeleteSchema,
  editGroupMessageSchema,
  pinGroupMessageSchema,
  unpinGroupMessageSchema,
  forwardGroupMessageSchema,
  groupMessageReactionSchema,
  groupMessageIdSchema,
  manageGroupMembersSchema,
  removeGroupMemberSchema,
  groupMessageSeenSchema,
  groupMembersListSchema,
  leaveGroupSchema,
};
