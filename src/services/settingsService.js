const Settings = require("../models/Settings");
const User = require("../models/User");

const defaultNotificationSettings = Object.freeze({
  likes: true,
  comments: true,
  followRequests: true,
  messages: true,
  mentions: true,
  system: true,
});

const defaultHiddenWordsSettings = Object.freeze({
  words: [],
  hideComments: true,
  hideMessages: true,
  hideStoryReplies: true,
});

const mergeNotificationSettings = (settings = {}) => ({
  ...defaultNotificationSettings,
  likes: typeof settings.likes === "boolean" ? settings.likes : defaultNotificationSettings.likes,
  comments: typeof settings.comments === "boolean" ? settings.comments : defaultNotificationSettings.comments,
  followRequests:
    typeof settings.followRequests === "boolean" ? settings.followRequests : defaultNotificationSettings.followRequests,
  messages: typeof settings.messages === "boolean" ? settings.messages : defaultNotificationSettings.messages,
  mentions: typeof settings.mentions === "boolean" ? settings.mentions : defaultNotificationSettings.mentions,
  system: typeof settings.system === "boolean" ? settings.system : defaultNotificationSettings.system,
});

const normalizeHiddenWords = (words = []) =>
  [...new Set(words.map((item) => item.trim().toLowerCase()).filter(Boolean))];

const mergeHiddenWordsSettings = (settings = {}) => ({
  words: Array.isArray(settings.words) ? normalizeHiddenWords(settings.words) : defaultHiddenWordsSettings.words,
  hideComments:
    typeof settings.hideComments === "boolean" ? settings.hideComments : defaultHiddenWordsSettings.hideComments,
  hideMessages:
    typeof settings.hideMessages === "boolean" ? settings.hideMessages : defaultHiddenWordsSettings.hideMessages,
  hideStoryReplies:
    typeof settings.hideStoryReplies === "boolean"
      ? settings.hideStoryReplies
      : defaultHiddenWordsSettings.hideStoryReplies,
});

const formatSettings = (settings) => ({
  _id: settings._id,
  userId: settings.user,
  user: settings.user,
  isPrivateAccount: Boolean(settings.isPrivateAccount),
  isDarkMode: Boolean(settings.isDarkMode),
  readReceipts: settings.readReceipts !== false,
  dataSaver: Boolean(settings.dataSaver),
  videoAutoPlay: settings.videoAutoPlay !== false,
  sensitiveFilter: Boolean(settings.sensitiveFilter),
  language: settings.language || "en",
  notifications: mergeNotificationSettings(settings.notifications),
  hiddenWords: mergeHiddenWordsSettings(settings.hiddenWords),
  createdAt: settings.createdAt,
  updatedAt: settings.updatedAt,
});

const getOrCreateSettings = async (userId) => {
  let settings = await Settings.findOne({ user: userId });
  const user = await User.findById(userId).select("isPrivateAccount").lean();
  let hasChanges = false;

  if (!settings) {
    settings = await Settings.create({
      user: userId,
      isPrivateAccount: user?.isPrivateAccount || false,
    });
  } else if (user && settings.isPrivateAccount !== user.isPrivateAccount) {
    settings.isPrivateAccount = user.isPrivateAccount;
    hasChanges = true;
  }

  const mergedNotifications = {
    ...settings.notifications,
    ...mergeNotificationSettings(settings.notifications),
  };
  const mergedHiddenWords = mergeHiddenWordsSettings(settings.hiddenWords);

  if (JSON.stringify(settings.notifications) !== JSON.stringify(mergedNotifications)) {
    settings.notifications = mergedNotifications;
    hasChanges = true;
  }

  if (JSON.stringify(settings.hiddenWords) !== JSON.stringify(mergedHiddenWords)) {
    settings.hiddenWords = mergedHiddenWords;
    hasChanges = true;
  }

  if (hasChanges) {
    await settings.save();
  }

  return settings;
};

const getSettings = async (userId) => {
  const settings = await getOrCreateSettings(userId);
  return formatSettings(settings);
};

const updateSettings = async (userId, payload) => {
  const settings = await getOrCreateSettings(userId);
  Object.assign(settings, payload);
  if (typeof payload.isPrivateAccount === "boolean") {
    await User.findByIdAndUpdate(userId, { isPrivateAccount: payload.isPrivateAccount });
  }
  await settings.save();
  return formatSettings(settings);
};

const getNotificationSettings = async (userId) => {
  const settings = await getOrCreateSettings(userId);
  return mergeNotificationSettings(settings.notifications);
};

const updateNotificationSettings = async (userId, payload) => {
  const settings = await getOrCreateSettings(userId);
  settings.notifications = {
    ...settings.notifications,
    ...payload,
  };
  await settings.save();
  return mergeNotificationSettings(settings.notifications);
};

const getHiddenWordsSettings = async (userId) => {
  const settings = await getOrCreateSettings(userId);
  return mergeHiddenWordsSettings(settings.hiddenWords);
};

const updateHiddenWordsSettings = async (userId, payload) => {
  const settings = await getOrCreateSettings(userId);
  settings.hiddenWords = mergeHiddenWordsSettings({
    ...settings.hiddenWords,
    ...payload,
  });
  await settings.save();
  return mergeHiddenWordsSettings(settings.hiddenWords);
};

module.exports = {
  getSettings,
  getOrCreateSettings,
  updateSettings,
  getNotificationSettings,
  updateNotificationSettings,
  defaultNotificationSettings,
  getHiddenWordsSettings,
  updateHiddenWordsSettings,
  defaultHiddenWordsSettings,
};
