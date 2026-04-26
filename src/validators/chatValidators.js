const { z } = require("zod");

const { objectIdSchema, paginationQuerySchema } = require("./commonValidators");
const chatMessageTypeSchema = z.enum(["text", "image", "video", "voice", "round_video", "file"]);

const createConversationSchema = {
  body: z.object({
    participantId: objectIdSchema,
  }),
};

const conversationMessagesSchema = {
  params: z.object({
    conversationId: objectIdSchema,
  }),
  query: paginationQuerySchema,
};

const conversationsListSchema = {
  query: paginationQuerySchema,
};

const chatSearchQuerySchema = {
  query: paginationQuerySchema.extend({
    q: z.string().trim().min(1).max(60),
  }),
};

const conversationSearchSchema = {
  params: z.object({
    conversationId: objectIdSchema,
  }),
  query: paginationQuerySchema.extend({
    q: z.string().trim().min(1).max(60),
  }),
};

const sendMessageSchema = {
  params: z.object({
    conversationId: objectIdSchema,
  }),
  body: z
    .object({
      text: z.string().trim().max(2000).optional(),
      messageType: chatMessageTypeSchema.default("text"),
      replyToMessageId: objectIdSchema.optional(),
      clientId: z.string().trim().max(128).regex(/^[A-Za-z0-9:_-]+$/).optional(),
      media: z
        .object({
          url: z.string().trim().url(),
          publicId: z.string().trim().max(255).nullable().optional(),
          type: z.enum(["image", "video", "voice", "round_video", "file"]),
          fileName: z.string().trim().max(255).optional().nullable(),
          fileSize: z.coerce.number().int().min(0).optional().nullable(),
          mimeType: z.string().trim().max(255).optional().nullable(),
          durationSeconds: z.coerce.number().min(0).optional().nullable(),
        })
        .nullable()
        .optional(),
    })
    .superRefine((value, ctx) => {
      if (value.messageType === "text" && !value.text?.trim()) {
        ctx.addIssue({
          code: "custom",
          path: ["text"],
          message: "Text message is required",
        });
      }

      if (value.messageType !== "text") {
        if (!value.media) {
          ctx.addIssue({
            code: "custom",
            path: ["media"],
            message: "Media payload is required for media messages",
          });
        } else if (value.media.type !== value.messageType) {
          ctx.addIssue({
            code: "custom",
            path: ["media", "type"],
            message: "Media type must match message type",
          });
        }
      }
    }),
};

const sendMediaMessageSchema = {
  params: z.object({
    conversationId: objectIdSchema,
  }),
  body: z.object({
    text: z.string().trim().max(2000).optional(),
    messageType: z.enum(["image", "video", "voice", "round_video", "file"]),
    replyToMessageId: objectIdSchema.optional(),
    clientId: z.string().trim().max(128).regex(/^[A-Za-z0-9:_-]+$/).optional(),
  }),
};

const conversationIdParamSchema = {
  params: z.object({
    conversationId: objectIdSchema,
  }),
};

const messageIdSchema = {
  params: z.object({
    messageId: objectIdSchema,
  }),
};

const conversationMessageParamSchema = {
  params: z.object({
    conversationId: objectIdSchema,
    messageId: objectIdSchema,
  }),
};

const messageDeleteSchema = {
  params: z.object({
    messageId: objectIdSchema,
  }),
  body: z.object({
    scope: z.enum(["self", "everyone"]).default("self"),
  }),
};

const editMessageSchema = {
  params: z.object({
    messageId: objectIdSchema,
  }),
  body: z.object({
    text: z.string().trim().min(1).max(2000),
  }),
};

const pinMessageSchema = {
  params: z.object({
    conversationId: objectIdSchema,
    messageId: objectIdSchema,
  }),
};

const unpinMessageSchema = {
  params: z.object({
    conversationId: objectIdSchema,
  }),
};

const forwardMessageSchema = {
  params: z.object({
    conversationId: objectIdSchema,
  }),
  body: z.object({
    sourceMessageId: objectIdSchema,
    comment: z.string().trim().max(2000).optional(),
  }),
};

const updateReactionSchema = {
  params: z.object({
    messageId: objectIdSchema,
  }),
  body: z.object({
    emoji: z.string().trim().min(1).max(20),
  }),
};

const userIdParamSchema = {
  params: z.object({
    userId: objectIdSchema,
  }),
};

const reportUserSchema = {
  body: z.object({
    reportedUserId: objectIdSchema,
    conversationId: objectIdSchema.optional(),
    messageId: objectIdSchema.optional(),
    reason: z.enum(["spam", "harassment", "nudity", "violence", "scam", "other"]),
    details: z.string().trim().max(1000).optional(),
  }),
};

const muteConversationSchema = {
  params: z.object({
    conversationId: objectIdSchema,
  }),
  body: z.object({
    mutedUntil: z.string().datetime().optional(),
  }),
};

module.exports = {
  createConversationSchema,
  conversationsListSchema,
  chatSearchQuerySchema,
  conversationSearchSchema,
  conversationMessagesSchema,
  conversationIdParamSchema,
  sendMessageSchema,
  sendMediaMessageSchema,
  messageIdSchema,
  conversationMessageParamSchema,
  messageDeleteSchema,
  editMessageSchema,
  pinMessageSchema,
  unpinMessageSchema,
  forwardMessageSchema,
  updateReactionSchema,
  userIdParamSchema,
  reportUserSchema,
  muteConversationSchema,
};
