const ApiError = require("../utils/ApiError");
const { getPagination } = require("../utils/pagination");
const { uploadBufferToCloudinary, destroyCloudinaryAsset } = require("../utils/media");
const Conversation = require("../models/Conversation");
const Message = require("../models/Message");
const User = require("../models/User");
const Follow = require("../models/Follow");
const { createAuditLog } = require("./auditLogService");
const { ensureNotBlockedBetweenUsers, loadBlockedUserIds } = require("./chatSecurityService");
const { validateUploadedMedia } = require("../utils/media");
const { recordChatEvent, listChatEvents } = require("./chatEventService");
const logger = require("../utils/logger");
const { incrementCounter } = require("./chatMetricsService");

const participantProjection = "username fullName avatar isPrivateAccount";
const senderProjection = "username fullName avatar";
const escapeRegex = (value = "") => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const SHARE_PREFIX = "[[stugram-share:";
const SHARE_SUFFIX = "]]";
const SUPPORTED_SHARE_KINDS = new Set(["POST", "REEL", "MUSIC", "FILE", "LOCATION"]);

const parseStructuredMetadata = (text = "") => {
  const trimmed = String(text || "").trim();
  if (!trimmed.startsWith(SHARE_PREFIX) || !trimmed.endsWith(SHARE_SUFFIX)) return null;

  try {
    const parsed = JSON.parse(trimmed.slice(SHARE_PREFIX.length, -SHARE_SUFFIX.length));
    if (!parsed?.type || !SUPPORTED_SHARE_KINDS.has(String(parsed.type).toUpperCase())) {
      return null;
    }

    const payload = {
      title: typeof parsed.title === "string" ? parsed.title : "",
      subtitle: typeof parsed.subtitle === "string" ? parsed.subtitle : null,
      tertiary: typeof parsed.tertiary === "string" ? parsed.tertiary : null,
      imageUrl: typeof parsed.imageUrl === "string" ? parsed.imageUrl : null,
      targetId: typeof parsed.targetId === "string" ? parsed.targetId : null,
    };

    const normalizedType = String(parsed.type).toUpperCase();
    const kindByType = {
      POST: "post_share",
      REEL: "reel_share",
      MUSIC: "music",
      FILE: "file",
      LOCATION: "location",
    };

    return {
      kind: kindByType[normalizedType],
      payload,
    };
  } catch (_error) {
    return null;
  }
};

const resolveUploadResourceType = (messageType, file) => {
  if (messageType === "file") return "raw";
  if (messageType === "voice" || messageType === "round_video") return "video";
  return file?.mimetype?.startsWith("video") ? "video" : "image";
};

const buildMediaPayload = ({ uploaded, resolvedType, file }) => ({
  url: uploaded.url,
  publicId: uploaded.publicId,
  type: resolvedType,
  fileName: file?.originalname || null,
  fileSize: Number.isFinite(file?.size) ? file.size : null,
  mimeType: file?.mimetype || null,
  durationSeconds: uploaded.duration || null,
});

const buildConversationKey = (firstId, secondId) => [firstId.toString(), secondId.toString()].sort().join(":");

const isDuplicateKeyError = (error) => error?.code === 11000;

const findExistingClientMessage = (conversationId, senderId, clientId) => {
  if (!clientId) return null;
  return populateMessageRelations(Message.findOne({ conversation: conversationId, sender: senderId, clientId }));
};

const attachSequence = (message, sequence) => ({
  ...message,
  serverSequence: sequence,
});

const buildChatTrace = ({
  targetType = "direct",
  targetId,
  currentUserId,
  clientId = null,
  messageId = null,
  sequence = null,
  eventType = null,
  httpStatus = null,
  errorCode = null,
}) => ({
  targetType,
  targetId: targetId?.toString?.() || targetId || null,
  userId: currentUserId?.toString?.() || currentUserId || null,
  clientId,
  messageId: messageId?.toString?.() || messageId || null,
  sequence,
  eventType,
  httpStatus,
  errorCode,
});

const buildMessagePreview = (message) => {
  if (message.isDeletedForEveryone) return "This message was deleted";
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

const buildSeenByDetails = (records = []) =>
  records.map((record) => ({
    user: record.user?._id
      ? {
          _id: record.user._id,
          username: record.user.username,
          fullName: record.user.fullName,
          avatar: record.user.avatar,
        }
      : record.user,
    seenAt: record.seenAt || null,
  }));

const getReadAtFromSeenRecords = (records = [], senderId) => {
  const otherSeen = records.find((record) => {
    const userId = record.user?._id ? record.user._id.toString() : record.user?.toString?.() || "";
    return userId && senderId && userId !== senderId.toString();
  });
  return otherSeen?.seenAt ? new Date(otherSeen.seenAt).toISOString() : null;
};

const buildReplyPreview = (message, currentUserId) => {
  if (!message) return null;
  if (message.deletedFor?.some((userId) => userId.toString() === currentUserId.toString())) return null;

  return {
    _id: message._id,
    text: message.text,
    messageType: message.messageType,
    media: message.media
      ? {
          url: message.media.url,
          type: message.media.type,
          fileName: message.media.fileName || null,
          fileSize: message.media.fileSize || null,
          mimeType: message.media.mimeType || null,
          durationSeconds: message.media.durationSeconds || null,
        }
      : null,
    sender: message.sender
      ? {
          _id: message.sender._id,
          username: message.sender.username,
          fullName: message.sender.fullName,
          avatar: message.sender.avatar,
        }
      : null,
    createdAt: message.createdAt,
  };
};

const buildReactions = (reactions = []) =>
  reactions.map((reaction) => ({
    user: reaction.user?._id
      ? {
          _id: reaction.user._id,
          username: reaction.user.username,
          fullName: reaction.user.fullName,
          avatar: reaction.user.avatar,
        }
      : reaction.user,
    emoji: reaction.emoji,
  }));

const formatMessage = (message, currentUserId) => ({
  _id: message._id,
  conversation: message.conversation,
  sender: message.sender,
  clientId: message.clientId || null,
  text: message.text,
  messageType: message.messageType,
  media: message.media,
  metadata: message.metadata || null,
  replyToMessage: buildReplyPreview(message.replyToMessage, currentUserId),
  reactions: buildReactions(message.reactions),
  seenBy: message.seenBy,
  seenByRecords: buildSeenByDetails(message.seenByRecords || []),
  deletedFor: message.deletedFor,
  forwardedFromMessageId: message.forwardedFromMessageId || null,
  forwardedFromSenderId: message.forwardedFromSenderId || null,
  forwardedFromConversationId: message.forwardedFromConversationId || null,
  forwardedAt: message.forwardedAt || null,
  editedAt: message.editedAt || null,
  isDeletedForEveryone: Boolean(message.isDeletedForEveryone),
  deletedForEveryoneAt: message.deletedForEveryoneAt || null,
  deletedForEveryoneBy: message.deletedForEveryoneBy || null,
  readAt: message.readAt || getReadAtFromSeenRecords(message.seenByRecords || [], message.sender?._id || message.sender),
  createdAt: message.createdAt,
  updatedAt: message.updatedAt,
});

const ensureChatPermission = async (currentUserId, targetUserId) => {
  const targetUser = await User.findById(targetUserId);
  if (!targetUser) throw new ApiError(404, "User not found");

  if (currentUserId.toString() === targetUserId.toString()) {
    throw new ApiError(400, "You cannot create a conversation with yourself");
  }

  if (!targetUser.isPrivateAccount) {
    await ensureNotBlockedBetweenUsers(currentUserId, targetUserId);
    return targetUser;
  }

  const hasAccess = await Follow.exists({
    follower: currentUserId,
    following: targetUserId,
  });

  if (!hasAccess) {
    throw new ApiError(403, "You cannot message this private account");
  }

  await ensureNotBlockedBetweenUsers(currentUserId, targetUserId);
  return targetUser;
};

const ensureConversationParticipant = async (conversationId, currentUserId) => {
  const conversation = await Conversation.findById(conversationId).populate("participants", participantProjection);
  if (!conversation) {
    logger.warn("chat_access_denied", buildChatTrace({
      targetId: conversationId,
      currentUserId,
      httpStatus: 404,
      errorCode: "CONVERSATION_NOT_FOUND",
    }));
    throw new ApiError(404, "Conversation not found");
  }

  const isParticipant = conversation.participants.some(
    (participant) => participant._id.toString() === currentUserId.toString()
  );

  if (!isParticipant) {
    logger.warn("chat_access_denied", buildChatTrace({
      targetId: conversationId,
      currentUserId,
      httpStatus: 403,
      errorCode: "CONVERSATION_ACCESS_DENIED",
    }));
    throw new ApiError(403, "Only participants can access this conversation");
  }

  const counterpart = conversation.participants.find(
    (participant) => participant._id.toString() !== currentUserId.toString()
  );
  if (counterpart) {
    await ensureNotBlockedBetweenUsers(currentUserId, counterpart._id);
  }

  return conversation;
};

const resolveReplyTarget = async (currentUserId, conversationId, replyToMessageId) => {
  if (!replyToMessageId) return null;

  const replyTarget = await Message.findById(replyToMessageId);
  if (!replyTarget || replyTarget.conversation.toString() !== conversationId.toString()) {
    throw new ApiError(400, "Reply target must belong to the same conversation");
  }

  if (
    replyTarget.isDeletedForEveryone ||
    replyTarget.deletedFor.some((userId) => userId.toString() === currentUserId.toString())
  ) {
    throw new ApiError(400, "You cannot reply to a deleted message");
  }

  return replyTarget;
};

const ensureMessageBelongsToConversation = async (currentUserId, conversationId, messageId) => {
  const message = await populateMessageRelations(Message.findById(messageId));
  if (!message || message.conversation.toString() !== conversationId.toString()) {
    throw new ApiError(404, "Message not found");
  }

  await ensureConversationParticipant(conversationId, currentUserId);
  if (message.isDeletedForEveryone || message.deletedFor.some((userId) => userId.toString() === currentUserId.toString())) {
    throw new ApiError(400, "Message is not available");
  }
  return message;
};

const ensureEditableMessage = (message, currentUserId) => {
  const senderId = (message.sender?._id || message.sender).toString();
  if (senderId !== currentUserId.toString()) {
    throw new ApiError(403, "You can edit only your own messages");
  }
  if (message.messageType !== "text") {
    throw new ApiError(400, "Only text messages can be edited");
  }
  if (message.isDeletedForEveryone) {
    throw new ApiError(400, "Deleted messages cannot be edited");
  }
};

const populateMessageRelations = (query) =>
  query
    .populate("sender", senderProjection)
    .populate("reactions.user", senderProjection)
    .populate("seenByRecords.user", senderProjection)
    .populate({
      path: "replyToMessage",
      populate: {
        path: "sender",
        select: senderProjection,
      },
    });

const populateConversationRelations = (query) =>
  query
    .populate("participants", participantProjection)
    .populate({
      path: "pinnedMessage",
      populate: [
        { path: "sender", select: senderProjection },
        { path: "reactions.user", select: senderProjection },
        {
          path: "replyToMessage",
          populate: {
            path: "sender",
            select: senderProjection,
          },
        },
      ],
    });

const getConversationUnreadCounts = async (conversationIds, currentUserId) => {
  if (!conversationIds.length) return new Map();

  const unreadCounts = await Message.aggregate([
    {
      $match: {
        conversation: { $in: conversationIds },
        sender: { $ne: currentUserId },
        seenBy: { $ne: currentUserId },
        deletedFor: { $ne: currentUserId },
        isDeletedForEveryone: { $ne: true },
      },
    },
    {
      $group: {
        _id: "$conversation",
        count: { $sum: 1 },
      },
    },
  ]);

  return new Map(unreadCounts.map((item) => [item._id.toString(), item.count]));
};

const getLatestVisibleMessagesForConversations = async (conversationIds, currentUserId) => {
  if (!conversationIds.length) return new Map();

  const messages = await Message.find({
    conversation: { $in: conversationIds },
    deletedFor: { $ne: currentUserId },
  })
    .populate("sender", senderProjection)
    .sort({ createdAt: -1 })
    .lean();

  const latestByConversation = new Map();
  for (const message of messages) {
    const key = message.conversation.toString();
    if (!latestByConversation.has(key)) {
      latestByConversation.set(key, message);
    }
    if (latestByConversation.size === conversationIds.length) {
      break;
    }
  }

  return latestByConversation;
};

const getTotalUnreadCount = async (currentUserId) => {
  const count = await Message.countDocuments({
    sender: { $ne: currentUserId },
    seenBy: { $ne: currentUserId },
    deletedFor: { $ne: currentUserId },
    isDeletedForEveryone: { $ne: true },
    conversation: {
      $in: await Conversation.find({ participants: currentUserId }).distinct("_id"),
    },
  });

  return { unreadCount: count };
};

const getChatSummary = async (currentUserId) => {
  const conversationIds = await Conversation.find({ participants: currentUserId }).distinct("_id");
  if (!conversationIds.length) {
    return {
      totalUnreadMessages: 0,
      unreadConversations: 0,
    };
  }

  const unreadCountMap = await getConversationUnreadCounts(conversationIds, currentUserId);
  let totalUnreadMessages = 0;
  let unreadConversations = 0;

  unreadCountMap.forEach((count) => {
    totalUnreadMessages += count;
    if (count > 0) {
      unreadConversations += 1;
    }
  });

  return {
    totalUnreadMessages,
    unreadConversations,
  };
};

const formatConversation = ({ conversation, currentUserId, latestVisibleMessage = null, unreadCount = 0 }) => {
  const otherParticipant = conversation.participants.find(
    (participant) => participant._id.toString() !== currentUserId.toString()
  );

  return {
    _id: conversation._id,
    participants: conversation.participants,
    otherParticipant,
    createdBy: conversation.createdBy,
    lastMessage: latestVisibleMessage ? buildMessagePreview(latestVisibleMessage) : conversation.lastMessage,
    lastMessageAt: latestVisibleMessage?.createdAt || conversation.lastMessageAt,
    pinnedMessage: conversation.pinnedMessage ? formatMessage(conversation.pinnedMessage, currentUserId) : null,
    pinnedAt: conversation.pinnedAt || null,
    unreadCount,
    createdAt: conversation.createdAt,
    updatedAt: conversation.updatedAt,
  };
};

const getConversationByIdForUser = async (currentUserId, conversationId) => {
  const conversation = await ensureConversationParticipant(conversationId, currentUserId);
  const populatedConversation = await populateConversationRelations(Conversation.findById(conversation._id));
  const latestVisibleMessages = await getLatestVisibleMessagesForConversations([conversation._id], currentUserId);
  const unreadCountMap = await getConversationUnreadCounts([conversation._id], currentUserId);

  return formatConversation({
    conversation: populatedConversation,
    currentUserId,
    latestVisibleMessage: latestVisibleMessages.get(conversation._id.toString()) || null,
    unreadCount: unreadCountMap.get(conversation._id.toString()) || 0,
  });
};

const createConversation = async (currentUserId, participantId) => {
  await ensureChatPermission(currentUserId, participantId);

  const participantsKey = buildConversationKey(currentUserId, participantId);
  let conversation = await Conversation.findOne({ participantsKey }).populate("participants", participantProjection);

  if (!conversation) {
    conversation = await Conversation.create({
      participants: [currentUserId, participantId],
      createdBy: currentUserId,
      lastMessage: "",
      lastMessageAt: new Date(),
    });
    conversation = await populateConversationRelations(Conversation.findById(conversation.id));
  }

  return formatConversation({ conversation, currentUserId, unreadCount: 0 });
};

const getConversations = async (currentUserId, query) => {
  const { page, limit, skip } = getPagination(query);
  const filter = { participants: currentUserId };

  const [items, blockedIds] = await Promise.all([
    populateConversationRelations(Conversation.find(filter))
      .sort({ lastMessageAt: -1, updatedAt: -1 })
      .skip(skip)
      .limit(limit),
    loadBlockedUserIds(currentUserId),
  ]);

  const visibleItems = items.filter((conversation) => {
    const otherParticipant = conversation.participants.find(
      (participant) => participant._id.toString() !== currentUserId.toString()
    );
    return otherParticipant ? !blockedIds.has(otherParticipant._id.toString()) : true;
  });

  const conversationIds = visibleItems.map((conversation) => conversation._id);
  const [unreadCountMap, latestVisibleMessages] = await Promise.all([
    getConversationUnreadCounts(conversationIds, currentUserId),
    getLatestVisibleMessagesForConversations(conversationIds, currentUserId),
  ]);

  const enrichedItems = visibleItems.map((conversation) =>
    formatConversation({
      conversation,
      currentUserId,
      latestVisibleMessage: latestVisibleMessages.get(conversation._id.toString()) || null,
      unreadCount: unreadCountMap.get(conversation._id.toString()) || 0,
    })
  );

  return {
    items: enrichedItems,
    meta: { page, limit, total: enrichedItems.length, totalPages: Math.max(Math.ceil(enrichedItems.length / limit), 1) },
  };
};

const searchConversations = async (currentUserId, query = {}) => {
  const { page, limit, skip } = getPagination(query);
  const regex = new RegExp(escapeRegex(query.q.trim()), "i");

  const [conversations, blockedIds] = await Promise.all([
    populateConversationRelations(Conversation.find({ participants: currentUserId }))
      .sort({ lastMessageAt: -1, updatedAt: -1 }),
    loadBlockedUserIds(currentUserId),
  ]);

  const matchingItems = conversations.filter((conversation) => {
    const otherParticipant = conversation.participants.find(
      (participant) => participant._id.toString() !== currentUserId.toString()
    );

    return Boolean(
      otherParticipant &&
        !blockedIds.has(otherParticipant._id.toString()) &&
        (regex.test(otherParticipant.username || "") ||
          regex.test(otherParticipant.fullName || "") ||
          regex.test(conversation.lastMessage || ""))
    );
  });

  const pageItems = matchingItems.slice(skip, skip + limit);
  const conversationIds = pageItems.map((conversation) => conversation._id);
  const [unreadCountMap, latestVisibleMessages] = await Promise.all([
    getConversationUnreadCounts(conversationIds, currentUserId),
    getLatestVisibleMessagesForConversations(conversationIds, currentUserId),
  ]);

  return {
    items: pageItems.map((conversation) =>
      formatConversation({
        conversation,
        currentUserId,
        latestVisibleMessage: latestVisibleMessages.get(conversation._id.toString()) || null,
        unreadCount: unreadCountMap.get(conversation._id.toString()) || 0,
      })
    ),
    meta: {
      page,
      limit,
      total: matchingItems.length,
      totalPages: Math.max(Math.ceil(matchingItems.length / limit), 1),
    },
  };
};

const getConversationMessages = async (currentUserId, conversationId, query) => {
  await ensureConversationParticipant(conversationId, currentUserId);
  const { page, limit, skip } = getPagination(query);
  const filter = {
    conversation: conversationId,
    deletedFor: { $ne: currentUserId },
  };

  const [items, total] = await Promise.all([
    populateMessageRelations(Message.find(filter))
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit),
    Message.countDocuments(filter),
  ]);

  return {
    items: items.map((item) => formatMessage(item, currentUserId)),
    meta: { page, limit, total, totalPages: Math.max(Math.ceil(total / limit), 1) },
  };
};

const searchConversationMessages = async (currentUserId, conversationId, query = {}) => {
  await ensureConversationParticipant(conversationId, currentUserId);
  const { page, limit, skip } = getPagination(query);
  const regex = new RegExp(escapeRegex(query.q.trim()), "i");
  const filter = {
    conversation: conversationId,
    deletedFor: { $ne: currentUserId },
    text: regex,
  };

  const [items, total] = await Promise.all([
    populateMessageRelations(Message.find(filter))
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit),
    Message.countDocuments(filter),
  ]);

  return {
    items: items.map((item) => formatMessage(item, currentUserId)),
    meta: {
      page,
      limit,
      total,
      totalPages: Math.max(Math.ceil(total / limit), 1),
    },
  };
};

const getConversationEvents = async (currentUserId, conversationId, query = {}) => {
  if (!conversationId) throw new ApiError(400, "conversationId is required");
  const conversation = await ensureConversationParticipant(conversationId, currentUserId);
  logger.info("replay_events_requested", {
    ...buildChatTrace({
      targetId: conversation._id,
      currentUserId,
      eventType: "replay.fetch",
    }),
    after: query.after || 0,
    limit: query.limit || null,
  });
  const result = await listChatEvents({
    targetType: "direct",
    targetId: conversation._id,
    after: query.after,
    limit: query.limit,
  });
  logger.info("replay_events_returned", {
    ...buildChatTrace({
      targetId: conversation._id,
      currentUserId,
      eventType: "replay.fetch",
      sequence: result.toSequence,
    }),
    fromSequence: result.fromSequence,
    toSequence: result.toSequence,
    eventCount: result.events.length,
    hasMore: result.hasMore,
  });
  return result;
};

const sendMessage = async (currentUserId, conversationId, payload) => {
  const conversation = await ensureConversationParticipant(conversationId, currentUserId);
  logger.info("message_create_attempt", buildChatTrace({
    targetId: conversation._id,
    currentUserId,
    clientId: payload.clientId || null,
    eventType: "message.created",
  }));
  incrementCounter("chat_send_attempt_total", { targetType: "direct", hasMedia: "false" });
  if (payload.clientId) {
    const existing = await findExistingClientMessage(conversationId, currentUserId, payload.clientId);
    if (existing) {
      logger.info("message_duplicate_clientid_resolved", buildChatTrace({
        targetId: conversation._id,
        currentUserId,
        clientId: payload.clientId,
        messageId: existing._id,
        eventType: "message.created",
      }));
      incrementCounter("chat_duplicate_resolved_total", { targetType: "direct", stage: "prefind" });
      return {
        message: formatMessage(existing, currentUserId),
        conversationId: conversation._id.toString(),
        participantIds: conversation.participants.map((participant) => participant._id.toString()),
      };
    }
  }
  const replyTarget = await resolveReplyTarget(currentUserId, conversationId, payload.replyToMessageId);

  let message;
  try {
    message = await Message.create({
      conversation: conversationId,
      sender: currentUserId,
      clientId: payload.clientId || null,
      text: payload.text?.trim() || "",
      messageType: payload.messageType,
      media: payload.media || null,
      metadata: parseStructuredMetadata(payload.text),
      replyToMessage: replyTarget?._id || null,
      seenBy: [currentUserId],
      seenByRecords: [{ user: currentUserId, seenAt: new Date() }],
    });
  } catch (error) {
    // The Mongo unique idempotency index is the final race-safety layer. If a
    // parallel retry created this clientId first, return that existing row with
    // the normal success shape instead of surfacing E11000 to the client.
    if (isDuplicateKeyError(error) && payload.clientId) {
      const existing = await findExistingClientMessage(conversationId, currentUserId, payload.clientId);
      if (existing) {
        logger.info("message_duplicate_clientid_resolved", buildChatTrace({
          targetId: conversation._id,
          currentUserId,
          clientId: payload.clientId,
          messageId: existing._id,
          eventType: "message.created",
          httpStatus: 201,
        }));
        incrementCounter("chat_duplicate_resolved_total", { targetType: "direct", stage: "create" });
        return {
          message: formatMessage(existing, currentUserId),
          conversationId: conversation._id.toString(),
          participantIds: conversation.participants.map((participant) => participant._id.toString()),
        };
      }
    }
    logger.error("message_create_failed", {
      ...buildChatTrace({
        targetId: conversation._id,
        currentUserId,
        clientId: payload.clientId || null,
        eventType: "message.created",
        errorCode: error.code || error.name || "MESSAGE_CREATE_FAILED",
      }),
      detail: error.message,
    });
    incrementCounter("chat_send_failed_terminal_total", { targetType: "direct", reason: "create_exception" });
    throw error;
  }

  const preview = buildMessagePreview(message);
  await Conversation.findByIdAndUpdate(conversationId, {
    lastMessage: preview,
    lastMessageAt: message.createdAt,
  });

  const populatedMessage = await populateMessageRelations(Message.findById(message._id));
  const formattedMessage = formatMessage(populatedMessage, currentUserId);
  const event = await recordChatEvent({
    targetType: "direct",
    targetId: conversation._id,
    type: "message.created",
    messageId: message._id,
    clientId: payload.clientId || null,
    actorId: currentUserId,
    payload: (sequence) => ({
      message: attachSequence(formattedMessage, sequence),
    }),
  });
  const sequencedMessage = attachSequence(formattedMessage, event.sequence);
  const participantIds = conversation.participants.map((participant) => participant._id.toString());
  await createAuditLog({
    actor: currentUserId,
    action: "chat.send_message",
    category: "chat",
    status: "success",
    conversation: conversationId,
    message: message._id,
  });
  logger.info("message_create_success", buildChatTrace({
    targetId: conversation._id,
    currentUserId,
    clientId: payload.clientId || null,
    messageId: message._id,
    sequence: event.sequence,
    eventType: "message.created",
    httpStatus: 201,
  }));
  incrementCounter("chat_send_success_total", { targetType: "direct", hasMedia: "false" });

  return {
    message: sequencedMessage,
    conversationId: conversation._id.toString(),
    participantIds,
    event,
  };
};

const sendMediaMessage = async (currentUserId, conversationId, payload, file) => {
  if (!file) {
    throw new ApiError(400, "Chat media file is required");
  }

  const conversation = await ensureConversationParticipant(conversationId, currentUserId);
  logger.info("message_create_attempt", buildChatTrace({
    targetId: conversation._id,
    currentUserId,
    clientId: payload.clientId || null,
    eventType: "message.created",
  }));
  incrementCounter("chat_send_attempt_total", { targetType: "direct", hasMedia: "true" });
  if (payload.clientId) {
    const existing = await findExistingClientMessage(conversationId, currentUserId, payload.clientId);
    if (existing) {
      logger.info("message_duplicate_clientid_resolved", buildChatTrace({
        targetId: conversation._id,
        currentUserId,
        clientId: payload.clientId,
        messageId: existing._id,
        eventType: "message.created",
      }));
      incrementCounter("chat_duplicate_resolved_total", { targetType: "direct", stage: "prefind" });
      return {
        message: formatMessage(existing, currentUserId),
        conversationId: conversation._id.toString(),
        participantIds: conversation.participants.map((participant) => participant._id.toString()),
      };
    }
  }
  const replyTarget = await resolveReplyTarget(currentUserId, conversationId, payload.replyToMessageId);
  const uploaded = await uploadBufferToCloudinary(
    file.buffer,
    "stugram/chats",
    resolveUploadResourceType(payload.messageType, file)
  );

  const resolvedType = await validateUploadedMedia({ uploaded, expectedType: payload.messageType });
  let message = null;

  try {
    message = await Message.create({
      conversation: conversationId,
      sender: currentUserId,
      clientId: payload.clientId || null,
      text: payload.text?.trim() || "",
      messageType: resolvedType,
      metadata: parseStructuredMetadata(payload.text),
      replyToMessage: replyTarget?._id || null,
      media: buildMediaPayload({ uploaded, resolvedType, file }),
      seenBy: [currentUserId],
      seenByRecords: [{ user: currentUserId, seenAt: new Date() }],
    });

    const preview = buildMessagePreview(message);
    await Conversation.findByIdAndUpdate(conversationId, {
      lastMessage: preview,
      lastMessageAt: message.createdAt,
    });

    const populatedMessage = await populateMessageRelations(Message.findById(message._id));
    const formattedMessage = formatMessage(populatedMessage, currentUserId);
    const event = await recordChatEvent({
      targetType: "direct",
      targetId: conversation._id,
      type: "message.created",
      messageId: message._id,
      clientId: payload.clientId || null,
      actorId: currentUserId,
      payload: (sequence) => ({
        message: attachSequence(formattedMessage, sequence),
      }),
    });
    const sequencedMessage = attachSequence(formattedMessage, event.sequence);
    const participantIds = conversation.participants.map((participant) => participant._id.toString());
    await createAuditLog({
      actor: currentUserId,
      action: "chat.send_media_message",
      category: "chat",
      status: "success",
      conversation: conversationId,
      message: message._id,
      details: { messageType: resolvedType },
    });
    logger.info("message_create_success", buildChatTrace({
      targetId: conversation._id,
      currentUserId,
      clientId: payload.clientId || null,
      messageId: message._id,
      sequence: event.sequence,
      eventType: "message.created",
      httpStatus: 201,
    }));
    incrementCounter("chat_send_success_total", { targetType: "direct", hasMedia: "true" });

    return {
      message: sequencedMessage,
      conversationId: conversation._id.toString(),
      participantIds,
      event,
    };
  } catch (error) {
    if (isDuplicateKeyError(error) && payload.clientId) {
      const existing = await findExistingClientMessage(conversationId, currentUserId, payload.clientId);
      if (existing) {
        // A concurrent send won the unique-index race after this request had
        // uploaded media. Remove this request's unused upload and return the
        // already persisted message with the standard success shape.
        await destroyCloudinaryAsset(
          uploaded.publicId,
          resolvedType === "file" ? "raw" : resolvedType === "image" ? "image" : "video"
        ).catch(() => null);
        logger.info("message_duplicate_clientid_resolved", buildChatTrace({
          targetId: conversation._id,
          currentUserId,
          clientId: payload.clientId,
          messageId: existing._id,
          eventType: "message.created",
          httpStatus: 201,
        }));
        incrementCounter("chat_duplicate_resolved_total", { targetType: "direct", stage: "create" });
        return {
          message: formatMessage(existing, currentUserId),
          conversationId: conversation._id.toString(),
          participantIds: conversation.participants.map((participant) => participant._id.toString()),
        };
      }
    }
    if (message?._id) {
      await Message.findByIdAndDelete(message._id).catch(() => null);
    }
    await destroyCloudinaryAsset(uploaded.publicId, resolvedType === "file" ? "raw" : resolvedType === "image" ? "image" : "video").catch(() => null);
    logger.error("message_create_failed", {
      ...buildChatTrace({
        targetId: conversation._id,
        currentUserId,
        clientId: payload.clientId || null,
        eventType: "message.created",
        errorCode: error.code || error.name || "MEDIA_MESSAGE_CREATE_FAILED",
      }),
      detail: error.message,
    });
    incrementCounter("chat_media_upload_failure_total", { targetType: "direct" });
    incrementCounter("chat_send_failed_terminal_total", { targetType: "direct", reason: "media_exception" });
    throw error;
  }
};

const markMessageSeen = async (currentUserId, messageId) => {
  const message = await populateMessageRelations(Message.findById(messageId));
  if (!message) throw new ApiError(404, "Message not found");

  const conversation = await ensureConversationParticipant(message.conversation, currentUserId);

  const freshMessage = await Message.findById(messageId);
  const alreadySeen = freshMessage.seenBy.some((userId) => userId.toString() === currentUserId.toString());
  const seenAt = new Date();
  if (!alreadySeen) {
    freshMessage.seenBy.push(currentUserId);
    freshMessage.seenByRecords.push({ user: currentUserId, seenAt });
    await freshMessage.save();
  }
  const populatedUpdatedMessage = await populateMessageRelations(Message.findById(freshMessage._id));
  const formattedMessage = formatMessage(populatedUpdatedMessage, currentUserId);
  const event = await recordChatEvent({
    targetType: "direct",
    targetId: conversation._id,
    type: "message.seen",
    messageId: freshMessage._id,
    clientId: freshMessage.clientId || null,
    actorId: currentUserId,
    payload: (sequence) => ({
      messageId: freshMessage._id.toString(),
      seenBy: formattedMessage.seenBy,
      seenByRecords: formattedMessage.seenByRecords,
      seenVersion: sequence,
      serverSequence: sequence,
      message: attachSequence(formattedMessage, sequence),
    }),
  });

  const participantIds = conversation.participants.map((participant) => participant._id.toString());
  logger.info("message_seen_updated", buildChatTrace({
    targetId: conversation._id,
    currentUserId,
    clientId: freshMessage.clientId || null,
    messageId: freshMessage._id,
    sequence: event.sequence,
    eventType: "message.seen",
  }));
  return {
    message: attachSequence(formattedMessage, event.sequence),
    participantIds,
    conversationId: conversation._id.toString(),
    seenAt: seenAt.toISOString(),
    event,
  };
};

const updateMessageReaction = async (currentUserId, messageId, emoji) => {
  const message = await Message.findById(messageId);
  if (!message) throw new ApiError(404, "Message not found");

  const conversation = await ensureConversationParticipant(message.conversation, currentUserId);
  if (message.isDeletedForEveryone) {
    throw new ApiError(400, "You cannot react to a deleted message");
  }
  if (message.deletedFor.some((userId) => userId.toString() === currentUserId.toString())) {
    throw new ApiError(400, "You cannot react to a deleted message");
  }

  const existingReactionIndex = message.reactions.findIndex(
    (reaction) => reaction.user.toString() === currentUserId.toString()
  );

  if (existingReactionIndex >= 0) {
    message.reactions[existingReactionIndex].emoji = emoji;
  } else {
    message.reactions.push({ user: currentUserId, emoji });
  }

  await message.save();
  await createAuditLog({
    actor: currentUserId,
    action: "chat.update_reaction",
    category: "chat",
    status: "success",
    conversation: message.conversation,
    message: messageId,
    details: { emoji },
  });
  const populatedMessage = await populateMessageRelations(Message.findById(messageId));
  const formattedMessage = formatMessage(populatedMessage, currentUserId);
  const event = await recordChatEvent({
    targetType: "direct",
    targetId: conversation._id,
    type: "message.reactions",
    messageId,
    clientId: message.clientId || null,
    actorId: currentUserId,
    payload: (sequence) => ({
      messageId: messageId.toString(),
      reactions: formattedMessage.reactions,
      reactionVersion: sequence,
      serverSequence: sequence,
      message: attachSequence(formattedMessage, sequence),
    }),
  });
  const participantIds = conversation.participants.map((participant) => participant._id.toString());
  logger.info("message_reactions_updated", buildChatTrace({
    targetId: conversation._id,
    currentUserId,
    clientId: message.clientId || null,
    messageId,
    sequence: event.sequence,
    eventType: "message.reactions",
  }));

  return {
    message: attachSequence(formattedMessage, event.sequence),
    conversationId: conversation._id.toString(),
    participantIds,
    event,
  };
};

const removeMessageReaction = async (currentUserId, messageId) => {
  const message = await Message.findById(messageId);
  if (!message) throw new ApiError(404, "Message not found");

  const conversation = await ensureConversationParticipant(message.conversation, currentUserId);
  message.reactions = message.reactions.filter((reaction) => reaction.user.toString() !== currentUserId.toString());
  await message.save();
  await createAuditLog({
    actor: currentUserId,
    action: "chat.remove_reaction",
    category: "chat",
    status: "success",
    conversation: message.conversation,
    message: messageId,
  });

  const populatedMessage = await populateMessageRelations(Message.findById(messageId));
  const formattedMessage = formatMessage(populatedMessage, currentUserId);
  const event = await recordChatEvent({
    targetType: "direct",
    targetId: conversation._id,
    type: "message.reactions",
    messageId,
    clientId: message.clientId || null,
    actorId: currentUserId,
    payload: (sequence) => ({
      messageId: messageId.toString(),
      reactions: formattedMessage.reactions,
      reactionVersion: sequence,
      serverSequence: sequence,
      message: attachSequence(formattedMessage, sequence),
    }),
  });
  const participantIds = conversation.participants.map((participant) => participant._id.toString());
  logger.info("message_reactions_updated", buildChatTrace({
    targetId: conversation._id,
    currentUserId,
    clientId: message.clientId || null,
    messageId,
    sequence: event.sequence,
    eventType: "message.reactions",
  }));

  return {
    message: attachSequence(formattedMessage, event.sequence),
    conversationId: conversation._id.toString(),
    participantIds,
    event,
  };
};

const deleteMessage = async (currentUserId, messageId, scope = "self") => {
  const message = await Message.findById(messageId);
  if (!message) throw new ApiError(404, "Message not found");
  const conversation = await ensureConversationParticipant(message.conversation, currentUserId);

  const senderId = (message.sender?._id || message.sender).toString();
  if (senderId !== currentUserId.toString()) {
    throw new ApiError(403, "You can delete only your own messages");
  }

  const deleteForEveryone = String(scope).toLowerCase() === "everyone";
  if (deleteForEveryone) {
    await Message.findByIdAndUpdate(messageId, {
      isDeletedForEveryone: true,
      deletedForEveryoneAt: new Date(),
      deletedForEveryoneBy: currentUserId,
    });
  } else {
    await Message.findByIdAndUpdate(
      messageId,
      { $addToSet: { deletedFor: currentUserId } },
      { new: true }
    );
  }

  const updatedMessage = await populateMessageRelations(Message.findById(messageId));
  const formattedMessage = formatMessage(updatedMessage, currentUserId);
  const event = await recordChatEvent({
    targetType: "direct",
    targetId: conversation._id,
    type: "message.deleted",
    messageId,
    clientId: message.clientId || null,
    actorId: currentUserId,
    payload: (sequence) => ({
      messageId: messageId.toString(),
      deletedAt: formattedMessage.deletedForEveryoneAt || new Date().toISOString(),
      deleteForEveryone,
      deleteVersion: sequence,
      serverSequence: sequence,
      message: attachSequence(formattedMessage, sequence),
    }),
  });

  await createAuditLog({
    actor: currentUserId,
    action: "chat.delete_message",
    category: "chat",
    status: "success",
    conversation: message.conversation,
    message: messageId,
  });
  logger.info("message_delete_success", buildChatTrace({
    targetId: conversation._id,
    currentUserId,
    clientId: message.clientId || null,
    messageId,
    sequence: event.sequence,
    eventType: "message.deleted",
  }));

  return {
    deleted: true,
    deletedForEveryone: deleteForEveryone,
    deletedAt: new Date().toISOString(),
    message: attachSequence(formattedMessage, event.sequence),
    conversationId: conversation._id.toString(),
    participantIds: conversation.participants.map((participant) => participant._id.toString()),
    event,
  };
};

const editMessage = async (currentUserId, messageId, payload) => {
  const message = await populateMessageRelations(Message.findById(messageId));
  if (!message) throw new ApiError(404, "Message not found");

  const conversation = await ensureConversationParticipant(message.conversation, currentUserId);
  ensureEditableMessage(message, currentUserId);

  message.text = payload.text.trim();
  message.editedAt = new Date();
  message.editedBy = currentUserId;
  message.metadata = parseStructuredMetadata(message.text) || message.metadata;
  await message.save();

  const populatedMessage = await populateMessageRelations(Message.findById(message._id));
  const formattedMessage = formatMessage(populatedMessage, currentUserId);
  const event = await recordChatEvent({
    targetType: "direct",
    targetId: conversation._id,
    type: "message.edited",
    messageId: message._id,
    clientId: message.clientId || null,
    actorId: currentUserId,
    payload: (sequence) => ({
      messageId: message._id.toString(),
      text: formattedMessage.text,
      editedAt: formattedMessage.editedAt,
      editVersion: sequence,
      serverSequence: sequence,
      message: attachSequence(formattedMessage, sequence),
    }),
  });
  const participantIds = conversation.participants.map((participant) => participant._id.toString());

  await createAuditLog({
    actor: currentUserId,
    action: "chat.edit_message",
    category: "chat",
    status: "success",
    conversation: message.conversation,
    message: messageId,
  });
  logger.info("message_edit_success", buildChatTrace({
    targetId: conversation._id,
    currentUserId,
    clientId: message.clientId || null,
    messageId,
    sequence: event.sequence,
    eventType: "message.edited",
  }));

  return {
    message: attachSequence(formattedMessage, event.sequence),
    conversationId: conversation._id.toString(),
    participantIds,
    event,
  };
};

const forwardMessage = async (currentUserId, conversationId, payload) => {
  const conversation = await ensureConversationParticipant(conversationId, currentUserId);
  const sourceMessage = await Message.findById(payload.sourceMessageId)
    .populate("sender", senderProjection)
    .populate("reactions.user", senderProjection)
    .populate({
      path: "replyToMessage",
      populate: { path: "sender", select: senderProjection },
    });

  if (!sourceMessage) throw new ApiError(404, "Source message not found");
  if (sourceMessage.isDeletedForEveryone || sourceMessage.deletedFor.some((userId) => userId.toString() === currentUserId.toString())) {
    throw new ApiError(400, "You cannot forward a deleted message");
  }

  const message = await Message.create({
    conversation: conversationId,
    sender: currentUserId,
    text: payload.comment?.trim() || "",
    messageType: sourceMessage.messageType,
    media: sourceMessage.media || null,
    metadata: sourceMessage.metadata || null,
    replyToMessage: null,
    forwardedFromMessageId: sourceMessage._id,
    forwardedFromSenderId: sourceMessage.sender?._id || sourceMessage.sender || null,
    forwardedFromConversationId: sourceMessage.conversation,
    forwardedAt: new Date(),
    seenBy: [currentUserId],
    seenByRecords: [{ user: currentUserId, seenAt: new Date() }],
  });

  await Conversation.findByIdAndUpdate(conversationId, {
    lastMessage: buildMessagePreview(message),
    lastMessageAt: message.createdAt,
  });

  const populatedMessage = await populateMessageRelations(Message.findById(message._id));
  const formattedMessage = formatMessage(populatedMessage, currentUserId);
  const event = await recordChatEvent({
    targetType: "direct",
    targetId: conversation._id,
    type: "message.created",
    messageId: message._id,
    clientId: null,
    actorId: currentUserId,
    payload: (sequence) => ({
      message: attachSequence(formattedMessage, sequence),
    }),
  });
  const participantIds = conversation.participants.map((participant) => participant._id.toString());

  await createAuditLog({
    actor: currentUserId,
    action: "chat.forward_message",
    category: "chat",
    status: "success",
    conversation: conversationId,
    message: message._id,
    details: { sourceMessageId: payload.sourceMessageId },
  });

  return {
    message: attachSequence(formattedMessage, event.sequence),
    conversationId: conversation._id.toString(),
    participantIds,
    event,
  };
};

const pinMessage = async (currentUserId, conversationId, messageId) => {
  const conversation = await ensureConversationParticipant(conversationId, currentUserId);
  const message = await Message.findById(messageId);
  if (!message || message.conversation.toString() !== conversationId.toString()) {
    throw new ApiError(404, "Message not found");
  }
  if (message.isDeletedForEveryone) {
    throw new ApiError(400, "Deleted messages cannot be pinned");
  }

  await Conversation.findByIdAndUpdate(conversationId, {
    pinnedMessage: message._id,
    pinnedBy: currentUserId,
    pinnedAt: new Date(),
  });

  const populatedConversation = await populateConversationRelations(Conversation.findById(conversationId));
  const participantIds = conversation.participants.map((participant) => participant._id.toString());
  return {
    conversation: formatConversation({
      conversation: populatedConversation,
      currentUserId,
      latestVisibleMessage: null,
      unreadCount: 0,
    }),
    conversationId: conversation._id.toString(),
    participantIds,
  };
};

const unpinMessage = async (currentUserId, conversationId) => {
  const conversation = await ensureConversationParticipant(conversationId, currentUserId);
  await Conversation.findByIdAndUpdate(conversationId, {
    pinnedMessage: null,
    pinnedBy: null,
    pinnedAt: null,
  });

  const populatedConversation = await populateConversationRelations(Conversation.findById(conversationId));
  const participantIds = conversation.participants.map((participant) => participant._id.toString());
  return {
    conversation: formatConversation({
      conversation: populatedConversation,
      currentUserId,
      latestVisibleMessage: null,
      unreadCount: 0,
    }),
    conversationId: conversation._id.toString(),
    participantIds,
  };
};

module.exports = {
  createConversation,
  getConversations,
  searchConversations,
  getTotalUnreadCount,
  getChatSummary,
  getConversationMessages,
  getConversationEvents,
  searchConversationMessages,
  sendMessage,
  sendMediaMessage,
  markMessageSeen,
  deleteMessage,
  editMessage,
  forwardMessage,
  pinMessage,
  unpinMessage,
  updateMessageReaction,
  removeMessageReaction,
  ensureConversationParticipant,
  getConversationByIdForUser,
  buildConversationKey,
  getConversationUnreadCounts,
  formatConversation,
};
