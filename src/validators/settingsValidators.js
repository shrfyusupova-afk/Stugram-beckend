const { z } = require("zod");

const HIDDEN_WORDS_MAX_COUNT = 100;
const HIDDEN_WORD_MAX_LENGTH = 64;

const settingsUpdateSchema = {
  body: z.object({
    isPrivateAccount: z.boolean().optional(),
    isDarkMode: z.boolean().optional(),
    readReceipts: z.boolean().optional(),
    dataSaver: z.boolean().optional(),
    videoAutoPlay: z.boolean().optional(),
    sensitiveFilter: z.boolean().optional(),
    language: z.string().trim().min(2).max(12).optional(),
    notifications: z
      .object({
        likes: z.boolean().optional(),
        comments: z.boolean().optional(),
        replies: z.boolean().optional(),
        follows: z.boolean().optional(),
        followRequests: z.boolean().optional(),
        followAccepts: z.boolean().optional(),
      })
      .optional(),
  }),
};

const notificationSettingsSchema = {
  body: z.object({
    likes: z.boolean().optional(),
    comments: z.boolean().optional(),
    followRequests: z.boolean().optional(),
    messages: z.boolean().optional(),
    mentions: z.boolean().optional(),
    system: z.boolean().optional(),
  }),
};

const hiddenWordsSettingsSchema = {
  body: z.object({
    words: z.array(z.string().trim().min(1).max(HIDDEN_WORD_MAX_LENGTH)).max(HIDDEN_WORDS_MAX_COUNT).optional(),
    hideComments: z.boolean().optional(),
    hideMessages: z.boolean().optional(),
    hideStoryReplies: z.boolean().optional(),
  }),
};

module.exports = {
  settingsUpdateSchema,
  notificationSettingsSchema,
  hiddenWordsSettingsSchema,
  HIDDEN_WORDS_MAX_COUNT,
  HIDDEN_WORD_MAX_LENGTH,
};
