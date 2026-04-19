const { getFirebaseMessaging, isFirebaseEnabled } = require("../config/firebaseAdmin");
const logger = require("../utils/logger");
const deviceService = require("./deviceService");
const settingsService = require("./settingsService");
const User = require("../models/User");

const INVALID_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
  "messaging/invalid-argument",
]);

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const NOTIFICATION_PREFERENCE_MAP = {
  like: "likes",
  comment: "comments",
  reply: "comments",
  follow_request: "followRequests",
  message: "messages",
  mention: "mentions",
  system: "system",
  follow: "follows",
  follow_accept: "followAccepts",
  call: "messages",
};

const canSendPushForType = async (userId, notificationType) => {
  if (!notificationType) return true;

  const settings = await settingsService.getOrCreateSettings(userId);
  const preferenceKey = NOTIFICATION_PREFERENCE_MAP[notificationType];
  if (!preferenceKey) return true;

  const value = settings.notifications?.[preferenceKey];
  return typeof value === "boolean" ? value : true;
};

const normalizeDataPayload = (data = {}) =>
  Object.fromEntries(
    Object.entries(data)
      .filter(([, value]) => value !== undefined && value !== null)
      .map(([key, value]) => [key, String(value)])
  );

const extractPushContext = (payload = {}, options = {}) => ({
  recipientId: options.recipientId || payload.data?.recipientId || null,
  conversationId: options.conversationId || payload.data?.conversationId || null,
  groupId: options.groupId || payload.data?.groupId || null,
  messageId: options.messageId || payload.data?.messageId || null,
  senderId: options.senderId || payload.data?.senderId || null,
});

const sendPushToTokensOnce = async (tokens, payload, options = {}) => {
  if (!tokens?.length) {
    return {
      attempted: 0,
      successCount: 0,
      failureCount: 0,
      skipped: true,
      reason: "no_active_tokens",
    };
  }

  if (!isFirebaseEnabled()) {
    logger.warn("Push skipped because Firebase is disabled", {
      attempted: tokens.length,
      notificationType: options.notificationType || null,
    });
    return {
      attempted: tokens.length,
      successCount: 0,
      failureCount: 0,
      skipped: true,
      reason: "firebase_disabled",
    };
  }

  logger.info("Push attempt started", {
    attempted: tokens.length,
    notificationType: options.notificationType || null,
    ...extractPushContext(payload, options),
  });

  const messaging = getFirebaseMessaging();
  const response = await messaging.sendEachForMulticast({
    tokens,
    notification: {
      title: payload.title,
      body: payload.body,
      ...(payload.image ? { imageUrl: payload.image } : {}),
    },
    data: normalizeDataPayload({
      notificationType: options.notificationType || payload.notificationType || null,
      ...payload.data,
    }),
    android: {
      priority: "high",
    },
  });

  const invalidTokens = [];
  const failedTokens = [];
  response.responses.forEach((result, index) => {
    if (!result.success) {
      failedTokens.push(tokens[index]);
    }
    if (!result.success && result.error?.code && INVALID_TOKEN_CODES.has(result.error.code)) {
      invalidTokens.push(tokens[index]);
    }
  });

  if (invalidTokens.length) {
    await deviceService.deactivatePushTokens(invalidTokens, "fcm_invalid_token");
    logger.warn("Invalid push tokens deactivated", {
      count: invalidTokens.length,
    });
  }

  if (response.failureCount > 0) {
    logger.warn("Push partially failed", {
      attempted: tokens.length,
      successCount: response.successCount,
      failureCount: response.failureCount,
      notificationType: options.notificationType || null,
      ...extractPushContext(payload, options),
    });
  } else {
    logger.info("Push sent successfully", {
      attempted: tokens.length,
      successCount: response.successCount,
      notificationType: options.notificationType || null,
      ...extractPushContext(payload, options),
    });
  }

  return {
    attempted: tokens.length,
    successCount: response.successCount,
    failureCount: response.failureCount,
    invalidatedTokens: invalidTokens.length,
    failedTokens,
  };
};

const sendPushToTokens = async (tokens, payload, options = {}) => {
  let firstAttempt;
  try {
    firstAttempt = await sendPushToTokensOnce(tokens, payload, options);
  } catch (error) {
    logger.error("Push attempt failed", {
      message: error.message,
      notificationType: options.notificationType || null,
      ...extractPushContext(payload, options),
    });
    await sleep(options.retryDelayMs || 250);
    try {
      const retry = await sendPushToTokensOnce(tokens, payload, {
        ...options,
        retryDelayMs: 0,
      });
      return {
        attempted: tokens.length,
        successCount: retry.successCount || 0,
        failureCount: retry.failureCount || 0,
        retry,
      };
    } catch (retryError) {
      logger.error("Push retry failed", {
        message: retryError.message,
        notificationType: options.notificationType || null,
        ...extractPushContext(payload, options),
      });
      return {
        attempted: tokens.length,
        successCount: 0,
        failureCount: tokens.length,
        error: retryError.message,
      };
    }
  }

  if (firstAttempt.skipped || firstAttempt.failureCount === 0) {
    return firstAttempt;
  }

  const retryableTokens = (options.retryTokens || firstAttempt.failedTokens || []).slice(0);
  if (!retryableTokens.length) {
    return firstAttempt;
  }

  await sleep(options.retryDelayMs || 250);
  logger.warn("Retrying push delivery once", {
    attempted: retryableTokens.length,
    notificationType: options.notificationType || null,
    ...extractPushContext(payload, options),
  });

  try {
    const retry = await sendPushToTokensOnce(retryableTokens, payload, {
      ...options,
      retryDelayMs: 0,
    });
    return {
      ...firstAttempt,
      retry,
    };
  } catch (error) {
    logger.error("Push retry failed", {
      message: error.message,
      notificationType: options.notificationType || null,
      ...extractPushContext(payload, options),
    });
    return firstAttempt;
  }
};

const sendPushToUser = async (userId, payload, options = {}) => {
  const shouldRespectPreferences = options.respectPreferences !== false;

  if (shouldRespectPreferences) {
    const allowed = await canSendPushForType(userId, options.notificationType || payload.notificationType);
    if (!allowed) {
      logger.info("Push skipped by notification preferences", {
        userId: String(userId),
        notificationType: options.notificationType || payload.notificationType || null,
      });
      return {
        attempted: 0,
        successCount: 0,
        failureCount: 0,
        skipped: true,
        reason: "notification_preference_disabled",
      };
    }
  }

  const tokens = await deviceService.getActivePushTokensForUser(userId);
  return sendPushToTokens(
    tokens.map((item) => item.token),
    payload,
    options
  );
};

const buildChatMessagePushPayload = ({ type, conversationId = null, groupId = null, messageId, senderId, senderName, previewText, mediaType = null }) => {
  const targetType = type === "group_chat" ? "group_chat" : "chat";
  const targetId = targetType === "group_chat" ? groupId : conversationId;

  return {
    title: senderName || "New message",
    body: previewText || "New message",
    data: normalizeDataPayload({
      type: targetType,
      targetId,
      conversationId,
      groupId,
      messageId,
      senderId,
      senderName,
      previewText,
      mediaType,
      notificationType: "message",
    }),
    notificationType: "message",
  };
};

const buildChatMessagePreviewText = (message = {}) => {
  if (message.messageType === "image") return "Photo";
  if (message.messageType === "video") return "Video";
  if (message.messageType === "voice") return "Voice message";
  if (message.messageType === "round_video") return "Round video";
  if (message.messageType === "file") return message.media?.fileName || "File";
  if (message.metadata?.kind === "post_share") return "Shared post";
  if (message.metadata?.kind === "reel_share") return "Shared reel";
  if (message.metadata?.kind === "music") return "Shared music";
  if (message.metadata?.kind === "location") return "Shared location";
  return message.text?.trim() || "Message";
};

const buildNotificationPushPayload = async ({ recipient, actor, type, message, image = null, data = {} }) => {
  const actorUser = actor ? await User.findById(actor).select("fullName username avatar").lean() : null;
  const actorName = actorUser?.fullName || actorUser?.username || "Someone";

  const titleMap = {
    like: actorName,
    comment: actorName,
    reply: actorName,
    follow: actorName,
    follow_request: actorName,
    follow_accept: actorName,
    mention: actorName,
    message: actorName,
    system: "System",
  };

  const bodyMap = {
    like: message || "liked your post",
    comment: message || "commented on your post",
    reply: message || "replied to your comment",
    follow: message || "started following you",
    follow_request: message || "sent you a follow request",
    follow_accept: message || "accepted your follow request",
    mention: message || "mentioned you",
    message: message || "sent you a message",
    system: message || "You have a new notification",
  };

  return sendPushToUser(
    recipient,
    {
      title: titleMap[type] || "Notification",
      body: bodyMap[type] || "You have a new notification",
      image: image || actorUser?.avatar || null,
      data,
      notificationType: type,
    },
    {
      notificationType: type,
    }
  );
};

module.exports = {
  sendPushToUser,
  sendPushToTokens,
  buildNotificationPushPayload,
  buildChatMessagePushPayload,
  buildChatMessagePreviewText,
};
