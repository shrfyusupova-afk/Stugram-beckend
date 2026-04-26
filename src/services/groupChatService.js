const ApiError = require("../utils/ApiError");
const GroupConversation = require("../models/GroupConversation");
const GroupMessage = require("../models/GroupMessage");
const User = require("../models/User");
const { getPagination } = require("../utils/pagination");
const { destroyCloudinaryAsset, uploadBufferToCloudinary, validateUploadedMedia } = require("../utils/media");
const { createAuditLog } = require("./auditLogService");
const { recordChatEvent, listChatEvents } = require("./chatEventService");
const logger = require("../utils/logger");
const { incrementCounter } = require("./chatMetricsService");

const participantProjection = "username fullName avatar isPrivateAccount";
const senderProjection = "username fullName avatar";
const SHARE_PREFIX = "[[stugram-share:";
const SHARE_SUFFIX = "]]";
const SUPPORTED_SHARE_KINDS = new Set(["POST", "REEL", "MUSIC", "FILE", "LOCATION"]);
const escapeRegex = (value = "") => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

const isDuplicateKeyError = (error) => error?.code === 11000;

const attachSequence = (message, sequence) => ({
  ...message,
  serverSequence: sequence,
});

const buildGroupTrace = ({
  targetId,
  currentUserId,
  clientId = null,
  messageId = null,
  sequence = null,
  eventType = null,
  httpStatus = null,
  errorCode = null,
}) => ({
  targetType: "group",
  targetId: targetId?.toString?.() || targetId || null,
  userId: currentUserId?.toString?.() || currentUserId || null,
  clientId,
  messageId: messageId?.toString?.() || messageId || null,
  sequence,
  eventType,
  httpStatus,
  errorCode,
});

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
  if (
    message.isDeletedForEveryone ||
    message.deletedFor?.some((userId) => userId.toString() === currentUserId.toString())
  ) return null;

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

const formatGroupMessage = (message, currentUserId) => ({
  _id: message._id,
  conversation: message.groupConversation,
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

const mapMemberPreview = (member) => ({
  _id: member._id,
  username: member.username,
  fullName: member.fullName,
  avatar: member.avatar,
  isPrivateAccount: member.isPrivateAccount,
});

const formatGroupConversation = ({ group, currentUserId, latestVisibleMessage = null, unreadCount = 0 }) => {
  const members = group.members.map((member) => member.user || member);
  const ownerId = (group.owner?._id || group.owner).toString();
  const owner = members.find((member) => member._id.toString() === ownerId) || null;

  return {
    _id: group._id,
    type: "group",
    name: group.name,
    avatar: group.avatar,
    participants: members,
    members,
    membersCount: members.length,
    owner,
    createdBy: group.owner,
    lastMessage: latestVisibleMessage ? buildMessagePreview(latestVisibleMessage) : group.lastMessage,
    lastMessageAt: latestVisibleMessage?.createdAt || group.lastMessageAt,
    pinnedMessage: group.pinnedMessage ? formatGroupMessage(group.pinnedMessage, currentUserId) : null,
    pinnedAt: group.pinnedAt || null,
    unreadCount,
    createdAt: group.createdAt,
    updatedAt: group.updatedAt,
    isOwner: ownerId === currentUserId.toString(),
  };
};

const ensureMembersExist = async (memberIds) => {
  const users = await User.find({ _id: { $in: memberIds } }).select(participantProjection);
  if (users.length !== memberIds.length) {
    throw new ApiError(400, "Some group members do not exist");
  }

  return users;
};

const populateGroup = (query) =>
  query.populate("members.user", participantProjection).populate("owner", participantProjection);

const ensureGroupMember = async (groupId, currentUserId) => {
  const group = await populateGroup(GroupConversation.findById(groupId));
  if (!group) {
    logger.warn("group_membership_denied", buildGroupTrace({
      targetId: groupId,
      currentUserId,
      httpStatus: 404,
      errorCode: "GROUP_NOT_FOUND",
    }));
    throw new ApiError(404, "Group chat not found");
  }

  const isMember = group.members.some((member) => member.user._id.toString() === currentUserId.toString());
  if (!isMember) {
    logger.warn("group_membership_denied", buildGroupTrace({
      targetId: groupId,
      currentUserId,
      httpStatus: 403,
      errorCode: "GROUP_ACCESS_DENIED",
    }));
    throw new ApiError(403, "Only group members can access this group chat");
  }

  return group;
};

const ensureGroupOwner = (group, currentUserId) => {
  if ((group.owner._id || group.owner).toString() !== currentUserId.toString()) {
    throw new ApiError(403, "Only group owner can manage members");
  }
};

const resolveReplyTarget = async (currentUserId, groupId, replyToMessageId) => {
  if (!replyToMessageId) return null;

  const replyTarget = await GroupMessage.findById(replyToMessageId);
  if (!replyTarget || replyTarget.groupConversation.toString() !== groupId.toString()) {
    throw new ApiError(400, "Reply target must belong to the same group chat");
  }

  if (
    replyTarget.isDeletedForEveryone ||
    replyTarget.deletedFor.some((userId) => userId.toString() === currentUserId.toString())
  ) {
    throw new ApiError(400, "You cannot reply to a deleted message");
  }

  return replyTarget;
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

const populateGroupConversationRelations = (query) =>
  query
    .populate("members.user", participantProjection)
    .populate("owner", participantProjection)
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

const getGroupUnreadCounts = async (groupIds, currentUserId) => {
  if (!groupIds.length) return new Map();

  const unreadCounts = await GroupMessage.aggregate([
    {
      $match: {
        groupConversation: { $in: groupIds },
        sender: { $ne: currentUserId },
        seenBy: { $ne: currentUserId },
        deletedFor: { $ne: currentUserId },
        isDeletedForEveryone: { $ne: true },
      },
    },
    {
      $group: {
        _id: "$groupConversation",
        count: { $sum: 1 },
      },
    },
  ]);

  return new Map(unreadCounts.map((item) => [item._id.toString(), item.count]));
};

const getLatestVisibleMessagesForGroups = async (groupIds, currentUserId) => {
  if (!groupIds.length) return new Map();

  const messages = await GroupMessage.find({
    groupConversation: { $in: groupIds },
    deletedFor: { $ne: currentUserId },
  })
    .populate("sender", senderProjection)
    .sort({ createdAt: -1 })
    .lean();

  const latestByGroup = new Map();
  for (const message of messages) {
    const key = message.groupConversation.toString();
    if (!latestByGroup.has(key)) {
      latestByGroup.set(key, message);
    }
    if (latestByGroup.size === groupIds.length) {
      break;
    }
  }

  return latestByGroup;
};

const getGroupAudienceUserIds = async (groupId) => {
  const group = await GroupConversation.findById(groupId).select("members.user").lean();
  if (!group) return [];
  return group.members.map((member) => member.user.toString());
};

const createGroupChat = async (currentUserId, payload, file = null) => {
  const uniqueMemberIds = [...new Set(payload.memberIds.map((id) => id.toString()))].filter(
    (id) => id !== currentUserId.toString()
  );

  if (!uniqueMemberIds.length) {
    throw new ApiError(400, "Group chat must include at least one other member");
  }

  const resolvedMembers = await ensureMembersExist(uniqueMemberIds);
  let uploadedAvatar = null;

  if (file) {
    uploadedAvatar = await uploadBufferToCloudinary(file.buffer, "stugram/group-chats", "image");
  }

  let group = null;
  try {
    group = await GroupConversation.create({
      name: payload.name.trim(),
      avatar: uploadedAvatar?.url || null,
      avatarPublicId: uploadedAvatar?.publicId || null,
      owner: currentUserId,
      members: [
        { user: currentUserId },
        ...resolvedMembers.map((member) => ({
          user: member._id,
        })),
      ],
      lastMessage: "",
      lastMessageAt: new Date(),
    });
  } catch (error) {
    if (uploadedAvatar?.publicId) {
      await destroyCloudinaryAsset(uploadedAvatar.publicId, "image").catch(() => null);
    }
    throw error;
  }

  const hydratedGroup = await populateGroupConversationRelations(GroupConversation.findById(group._id));
  await createAuditLog({
    actor: currentUserId,
    action: "group_chat.create",
    category: "chat",
    status: "success",
    details: { memberCount: hydratedGroup.members.length },
  });

  return {
    group: formatGroupConversation({
      group: hydratedGroup,
      currentUserId,
      unreadCount: 0,
    }),
    participantIds: hydratedGroup.members.map((member) => member.user._id.toString()),
  };
};

const getGroupChats = async (currentUserId, query = {}) => {
  const { page, limit, skip } = getPagination(query);
  const filter = { "members.user": currentUserId };

  const [items, total] = await Promise.all([
    populateGroupConversationRelations(GroupConversation.find(filter))
      .sort({ lastMessageAt: -1, updatedAt: -1 })
      .skip(skip)
      .limit(limit),
    GroupConversation.countDocuments(filter),
  ]);

  const groupIds = items.map((group) => group._id);
  const [unreadCountMap, latestVisibleMessages] = await Promise.all([
    getGroupUnreadCounts(groupIds, currentUserId),
    getLatestVisibleMessagesForGroups(groupIds, currentUserId),
  ]);

  const enrichedItems = items.map((group) =>
    formatGroupConversation({
      group,
      currentUserId,
      latestVisibleMessage: latestVisibleMessages.get(group._id.toString()) || null,
      unreadCount: unreadCountMap.get(group._id.toString()) || 0,
    })
  );

  return {
    items: enrichedItems,
    meta: {
      page,
      limit,
      total,
      totalPages: Math.ceil(total / limit) || 1,
    },
  };
};

const getGroupChatById = async (currentUserId, groupId) => {
  const group = await ensureGroupMember(groupId, currentUserId);
  const populatedGroup = await populateGroupConversationRelations(GroupConversation.findById(group._id));
  const latestVisibleMessages = await getLatestVisibleMessagesForGroups([group._id], currentUserId);
  const unreadCountMap = await getGroupUnreadCounts([group._id], currentUserId);

  return formatGroupConversation({
    group: populatedGroup,
    currentUserId,
    latestVisibleMessage: latestVisibleMessages.get(group._id.toString()) || null,
    unreadCount: unreadCountMap.get(group._id.toString()) || 0,
  });
};

const getGroupMembers = async (currentUserId, groupId, query = {}) => {
  const group = await ensureGroupMember(groupId, currentUserId);
  const { page, limit, skip } = getPagination(query);
  const total = group.members.length;
  const paginatedMembers = group.members.slice(skip, skip + limit);
  const ownerId = (group.owner?._id || group.owner).toString();

  return {
    items: paginatedMembers.map((member) => ({
      user: mapMemberPreview(member.user),
      joinedAt: member.joinedAt,
      role: member.user._id.toString() === ownerId ? "owner" : "member",
    })),
    meta: {
      page,
      limit,
      total,
      totalPages: Math.max(Math.ceil(total / limit), 1),
    },
  };
};

const getGroupMessages = async (currentUserId, groupId, query = {}) => {
  await ensureGroupMember(groupId, currentUserId);
  const { page, limit, skip } = getPagination(query);
  const filter = {
    groupConversation: groupId,
    deletedFor: { $ne: currentUserId },
  };

  const [items, total] = await Promise.all([
    populateMessageRelations(GroupMessage.find(filter))
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit),
    GroupMessage.countDocuments(filter),
  ]);

  return {
    items: items.map((item) => formatGroupMessage(item, currentUserId)),
    meta: {
      page,
      limit,
      total,
      totalPages: Math.ceil(total / limit) || 1,
    },
  };
};

const searchGroupMessages = async (currentUserId, groupId, query = {}) => {
  await ensureGroupMember(groupId, currentUserId);
  const { page, limit, skip } = getPagination(query);
  const regex = new RegExp(escapeRegex(String(query.q || "").trim()), "i");
  const filter = {
    groupConversation: groupId,
    deletedFor: { $ne: currentUserId },
    text: regex,
  };

  const [items, total] = await Promise.all([
    populateMessageRelations(GroupMessage.find(filter))
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit),
    GroupMessage.countDocuments(filter),
  ]);

  return {
    items: items.map((item) => formatGroupMessage(item, currentUserId)),
    meta: {
      page,
      limit,
      total,
      totalPages: Math.max(Math.ceil(total / limit), 1),
    },
  };
};

const getGroupEvents = async (currentUserId, groupId, query = {}) => {
  if (!groupId) throw new ApiError(400, "groupId is required");
  const group = await ensureGroupMember(groupId, currentUserId);
  logger.info("replay_events_requested", {
    ...buildGroupTrace({
      targetId: group._id,
      currentUserId,
      eventType: "replay.fetch",
    }),
    after: query.after || 0,
    limit: query.limit || null,
  });
  const result = await listChatEvents({
    targetType: "group",
    targetId: group._id,
    after: query.after,
    limit: query.limit,
  });
  logger.info("replay_events_returned", {
    ...buildGroupTrace({
      targetId: group._id,
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

const createMessageRecord = async ({ currentUserId, group, payload, file = null }) => {
  const memberIds = group.members.map((member) => member.user._id.toString());
  logger.info("message_create_attempt", buildGroupTrace({
    targetId: group._id,
    currentUserId,
    clientId: payload.clientId || null,
    eventType: "message.created",
  }));
  incrementCounter("chat_send_attempt_total", { targetType: "group", hasMedia: file ? "true" : "false" });
  const findExistingClientMessage = () => {
    if (!payload.clientId) return null;
    return populateMessageRelations(
      GroupMessage.findOne({ groupConversation: group._id, sender: currentUserId, clientId: payload.clientId })
    );
  };
  if (payload.clientId) {
    const existing = await findExistingClientMessage();
    if (existing) {
      logger.info("message_duplicate_clientid_resolved", buildGroupTrace({
        targetId: group._id,
        currentUserId,
        clientId: payload.clientId,
        messageId: existing._id,
        eventType: "message.created",
      }));
      incrementCounter("chat_duplicate_resolved_total", { targetType: "group", stage: "prefind" });
      return {
        message: formatGroupMessage(existing, currentUserId),
        groupId: group._id.toString(),
        participantIds: memberIds,
      };
    }
  }
  const replyTarget = await resolveReplyTarget(currentUserId, group._id, payload.replyToMessageId);

  if (!file && !payload.text?.trim()) {
    throw new ApiError(400, "Text message is required");
  }

  if (!file && payload.messageType && payload.messageType !== "text") {
    throw new ApiError(400, "Media file is required for media messages");
  }

  let media = null;
  let messageType = payload.messageType || "text";

  if (file) {
    const uploaded = await uploadBufferToCloudinary(
      file.buffer,
      "stugram/group-chats",
      resolveUploadResourceType(payload.messageType, file)
    );
    const resolvedType = await validateUploadedMedia({ uploaded, expectedType: payload.messageType });
    messageType = resolvedType;
    media = buildMediaPayload({ uploaded, resolvedType, file });
  }

  let message = null;
  try {
    message = await GroupMessage.create({
      groupConversation: group._id,
      sender: currentUserId,
      clientId: payload.clientId || null,
      text: payload.text?.trim() || "",
      messageType,
      media,
      metadata: parseStructuredMetadata(payload.text),
      replyToMessage: replyTarget?._id || null,
      seenBy: [currentUserId],
      seenByRecords: [{ user: currentUserId, seenAt: new Date() }],
      deletedFor: [],
    });

    await GroupConversation.findByIdAndUpdate(group._id, {
      lastMessage: buildMessagePreview(message),
      lastMessageAt: message.createdAt,
    });
  } catch (error) {
    if (isDuplicateKeyError(error) && payload.clientId) {
      const existing = await findExistingClientMessage();
      if (existing) {
        // The unique idempotency index is the final guard against concurrent
        // retries. Return the already-created row with the normal success shape
        // rather than surfacing E11000 as a failed send.
        if (media?.publicId) {
          await destroyCloudinaryAsset(media.publicId, messageType === "file" ? "raw" : messageType === "image" ? "image" : "video").catch(() => null);
        }
        logger.info("message_duplicate_clientid_resolved", buildGroupTrace({
          targetId: group._id,
          currentUserId,
          clientId: payload.clientId,
          messageId: existing._id,
          eventType: "message.created",
          httpStatus: 201,
        }));
        incrementCounter("chat_duplicate_resolved_total", { targetType: "group", stage: "create" });
        return {
          message: formatGroupMessage(existing, currentUserId),
          groupId: group._id.toString(),
          participantIds: memberIds,
        };
      }
    }
    if (message?._id) {
      await GroupMessage.findByIdAndDelete(message._id).catch(() => null);
    }
    if (media?.publicId) {
      await destroyCloudinaryAsset(media.publicId, messageType === "file" ? "raw" : messageType === "image" ? "image" : "video").catch(() => null);
    }
    logger.error("message_create_failed", {
      ...buildGroupTrace({
        targetId: group._id,
        currentUserId,
        clientId: payload.clientId || null,
        eventType: "message.created",
        errorCode: error.code || error.name || "GROUP_MESSAGE_CREATE_FAILED",
      }),
      detail: error.message,
    });
    incrementCounter("chat_send_failed_terminal_total", { targetType: "group", reason: "create_exception" });
    if (file) {
      incrementCounter("chat_media_upload_failure_total", { targetType: "group" });
    }
    throw error;
  }

  const populatedMessage = await populateMessageRelations(GroupMessage.findById(message._id));
  const formattedMessage = formatGroupMessage(populatedMessage, currentUserId);
  const event = await recordChatEvent({
    targetType: "group",
    targetId: group._id,
    type: "message.created",
    messageId: message._id,
    clientId: payload.clientId || null,
    actorId: currentUserId,
    payload: (sequence) => ({
      message: attachSequence(formattedMessage, sequence),
    }),
  });
  const sequencedMessage = attachSequence(formattedMessage, event.sequence);

  await createAuditLog({
    actor: currentUserId,
    action: file ? "group_chat.send_media_message" : "group_chat.send_message",
    category: "chat",
    status: "success",
    message: message._id,
    details: { groupId: group._id.toString(), messageType },
  });
  logger.info("message_create_success", buildGroupTrace({
    targetId: group._id,
    currentUserId,
    clientId: payload.clientId || null,
    messageId: message._id,
    sequence: event.sequence,
    eventType: "message.created",
    httpStatus: 201,
  }));
  incrementCounter("chat_send_success_total", { targetType: "group", hasMedia: file ? "true" : "false" });

  return {
    message: sequencedMessage,
    groupId: group._id.toString(),
    participantIds: memberIds,
    event,
  };
};

const sendGroupMessage = async (currentUserId, groupId, payload, file = null) => {
  const group = await ensureGroupMember(groupId, currentUserId);
  return createMessageRecord({ currentUserId, group, payload, file });
};

const updateGroupMessageReaction = async (currentUserId, groupId, messageId, emoji) => {
  const group = await ensureGroupMember(groupId, currentUserId);
  const message = await GroupMessage.findOne({ _id: messageId, groupConversation: groupId });
  if (!message) throw new ApiError(404, "Group message not found");
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
    action: "group_chat.update_reaction",
    category: "chat",
    status: "success",
    conversation: groupId,
    message: messageId,
    details: { emoji },
  });

  const populatedMessage = await populateMessageRelations(GroupMessage.findById(messageId));
  const formattedMessage = formatGroupMessage(populatedMessage, currentUserId);
  const event = await recordChatEvent({
    targetType: "group",
    targetId: group._id,
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
  const participantIds = group.members.map((member) => member.user._id.toString());
  logger.info("message_reactions_updated", buildGroupTrace({
    targetId: group._id,
    currentUserId,
    clientId: message.clientId || null,
    messageId,
    sequence: event.sequence,
    eventType: "message.reactions",
  }));

  return {
    message: attachSequence(formattedMessage, event.sequence),
    groupId: group._id.toString(),
    participantIds,
    event,
  };
};

const removeGroupMessageReaction = async (currentUserId, groupId, messageId) => {
  const group = await ensureGroupMember(groupId, currentUserId);
  const message = await GroupMessage.findOne({ _id: messageId, groupConversation: groupId });
  if (!message) throw new ApiError(404, "Group message not found");

  message.reactions = message.reactions.filter((reaction) => reaction.user.toString() !== currentUserId.toString());
  await message.save();
  await createAuditLog({
    actor: currentUserId,
    action: "group_chat.remove_reaction",
    category: "chat",
    status: "success",
    conversation: groupId,
    message: messageId,
  });

  const populatedMessage = await populateMessageRelations(GroupMessage.findById(messageId));
  const formattedMessage = formatGroupMessage(populatedMessage, currentUserId);
  const event = await recordChatEvent({
    targetType: "group",
    targetId: group._id,
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
  const participantIds = group.members.map((member) => member.user._id.toString());
  logger.info("message_reactions_updated", buildGroupTrace({
    targetId: group._id,
    currentUserId,
    clientId: message.clientId || null,
    messageId,
    sequence: event.sequence,
    eventType: "message.reactions",
  }));

  return {
    message: attachSequence(formattedMessage, event.sequence),
    groupId: group._id.toString(),
    participantIds,
    event,
  };
};

const deleteGroupMessage = async (currentUserId, groupId, messageId, scope = "self") => {
  const group = await ensureGroupMember(groupId, currentUserId);
  const message = await GroupMessage.findOne({ _id: messageId, groupConversation: groupId });
  if (!message) throw new ApiError(404, "Group message not found");

  const senderId = (message.sender?._id || message.sender).toString();
  if (senderId !== currentUserId.toString()) {
    throw new ApiError(403, "You can delete only your own messages");
  }

  const deleteForEveryone = String(scope).toLowerCase() === "everyone";
  if (deleteForEveryone) {
    await GroupMessage.findByIdAndUpdate(messageId, {
      isDeletedForEveryone: true,
      deletedForEveryoneAt: new Date(),
      deletedForEveryoneBy: currentUserId,
    });
  } else {
    await GroupMessage.findByIdAndUpdate(messageId, { $addToSet: { deletedFor: currentUserId } }, { new: true });
  }
  const updatedMessage = await populateMessageRelations(GroupMessage.findById(messageId));
  const formattedMessage = formatGroupMessage(updatedMessage, currentUserId);
  const event = await recordChatEvent({
    targetType: "group",
    targetId: group._id,
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
    action: "group_chat.delete_message",
    category: "chat",
    status: "success",
    conversation: groupId,
    message: messageId,
  });
  logger.info("message_delete_success", buildGroupTrace({
    targetId: group._id,
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
    groupId: group._id.toString(),
    participantIds: group.members.map((member) => member.user._id.toString()),
    event,
  };
};

const editGroupMessage = async (currentUserId, groupId, messageId, payload) => {
  const group = await ensureGroupMember(groupId, currentUserId);
  const message = await populateMessageRelations(GroupMessage.findOne({ _id: messageId, groupConversation: groupId }));
  if (!message) throw new ApiError(404, "Group message not found");
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

  message.text = payload.text.trim();
  message.editedAt = new Date();
  message.editedBy = currentUserId;
  message.metadata = parseStructuredMetadata(message.text) || message.metadata;
  await message.save();

  const populatedMessage = await populateMessageRelations(GroupMessage.findById(message._id));
  const formattedMessage = formatGroupMessage(populatedMessage, currentUserId);
  const event = await recordChatEvent({
    targetType: "group",
    targetId: group._id,
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
  const participantIds = group.members.map((member) => member.user._id.toString());

  await createAuditLog({
    actor: currentUserId,
    action: "group_chat.edit_message",
    category: "chat",
    status: "success",
    conversation: groupId,
    message: messageId,
  });
  logger.info("message_edit_success", buildGroupTrace({
    targetId: group._id,
    currentUserId,
    clientId: message.clientId || null,
    messageId,
    sequence: event.sequence,
    eventType: "message.edited",
  }));

  return {
    message: attachSequence(formattedMessage, event.sequence),
    groupId: group._id.toString(),
    participantIds,
    event,
  };
};

const forwardGroupMessage = async (currentUserId, groupId, payload) => {
  const group = await ensureGroupMember(groupId, currentUserId);
  const sourceMessage = await GroupMessage.findById(payload.sourceMessageId)
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

  const message = await GroupMessage.create({
    groupConversation: group._id,
    sender: currentUserId,
    text: payload.comment?.trim() || "",
    messageType: sourceMessage.messageType,
    media: sourceMessage.media || null,
    metadata: sourceMessage.metadata || null,
    replyToMessage: null,
    forwardedFromMessageId: sourceMessage._id,
    forwardedFromSenderId: sourceMessage.sender?._id || sourceMessage.sender || null,
    forwardedFromConversationId: sourceMessage.groupConversation,
    forwardedAt: new Date(),
    seenBy: [currentUserId],
    seenByRecords: [{ user: currentUserId, seenAt: new Date() }],
    deletedFor: [],
  });

  await GroupConversation.findByIdAndUpdate(groupId, {
    lastMessage: buildMessagePreview(message),
    lastMessageAt: message.createdAt,
  });

  const populatedMessage = await populateMessageRelations(GroupMessage.findById(message._id));
  const formattedMessage = formatGroupMessage(populatedMessage, currentUserId);
  const event = await recordChatEvent({
    targetType: "group",
    targetId: group._id,
    type: "message.created",
    messageId: message._id,
    clientId: null,
    actorId: currentUserId,
    payload: (sequence) => ({
      message: attachSequence(formattedMessage, sequence),
    }),
  });
  const participantIds = group.members.map((member) => member.user._id.toString());

  await createAuditLog({
    actor: currentUserId,
    action: "group_chat.forward_message",
    category: "chat",
    status: "success",
    conversation: groupId,
    message: message._id,
    details: { sourceMessageId: payload.sourceMessageId },
  });

  return {
    message: attachSequence(formattedMessage, event.sequence),
    groupId: group._id.toString(),
    participantIds,
    event,
  };
};

const pinGroupMessage = async (currentUserId, groupId, messageId) => {
  const group = await ensureGroupMember(groupId, currentUserId);
  const message = await GroupMessage.findOne({ _id: messageId, groupConversation: groupId });
  if (!message) throw new ApiError(404, "Group message not found");
  if (message.isDeletedForEveryone) {
    throw new ApiError(400, "Deleted messages cannot be pinned");
  }

  await GroupConversation.findByIdAndUpdate(groupId, {
    pinnedMessage: message._id,
    pinnedBy: currentUserId,
    pinnedAt: new Date(),
  });

  const populatedGroup = await populateGroupConversationRelations(GroupConversation.findById(groupId));
  const participantIds = group.members.map((member) => member.user._id.toString());
  return {
    group: formatGroupConversation({
      group: populatedGroup,
      currentUserId,
      latestVisibleMessage: null,
      unreadCount: 0,
    }),
    groupId: group._id.toString(),
    participantIds,
  };
};

const unpinGroupMessage = async (currentUserId, groupId) => {
  const group = await ensureGroupMember(groupId, currentUserId);
  await GroupConversation.findByIdAndUpdate(groupId, {
    pinnedMessage: null,
    pinnedBy: null,
    pinnedAt: null,
  });

  const populatedGroup = await populateGroupConversationRelations(GroupConversation.findById(groupId));
  const participantIds = group.members.map((member) => member.user._id.toString());
  return {
    group: formatGroupConversation({
      group: populatedGroup,
      currentUserId,
      latestVisibleMessage: null,
      unreadCount: 0,
    }),
    groupId: group._id.toString(),
    participantIds,
  };
};

const addGroupMembers = async (currentUserId, groupId, payload) => {
  const group = await ensureGroupMember(groupId, currentUserId);
  ensureGroupOwner(group, currentUserId);

  const currentMemberIds = new Set(group.members.map((member) => member.user._id.toString()));
  const incomingMemberIds = [...new Set(payload.memberIds.map((id) => id.toString()))].filter(
    (id) => !currentMemberIds.has(id)
  );

  if (!incomingMemberIds.length) {
    return formatGroupConversation({ group, currentUserId, unreadCount: 0 });
  }

  const usersToAdd = await ensureMembersExist(incomingMemberIds);
  group.members.push(
    ...usersToAdd.map((member) => ({
      user: member._id,
    }))
  );
  await group.save();

  const updatedGroup = await populateGroupConversationRelations(GroupConversation.findById(group._id));
  return {
    group: formatGroupConversation({ group: updatedGroup, currentUserId, unreadCount: 0 }),
    addedMemberIds: usersToAdd.map((member) => member._id.toString()),
    membersCount: updatedGroup.members.length,
    participantIds: updatedGroup.members.map((member) => member.user._id.toString()),
  };
};

const updateGroupChat = async (currentUserId, groupId, payload, file = null) => {
  const group = await ensureGroupMember(groupId, currentUserId);
  ensureGroupOwner(group, currentUserId);

  const previousAvatarPublicId = group.avatarPublicId;
  let newAvatar = null;

  if (payload.name !== undefined) {
    group.name = payload.name.trim();
  }

  if (file) {
    newAvatar = await uploadBufferToCloudinary(file.buffer, "stugram/group-chats", "image");
    group.avatar = newAvatar.url;
    group.avatarPublicId = newAvatar.publicId;
  }

  try {
    await group.save();
  } catch (error) {
    if (newAvatar?.publicId) {
      await destroyCloudinaryAsset(newAvatar.publicId, "image").catch(() => null);
    }
    throw error;
  }

  if (file && previousAvatarPublicId && previousAvatarPublicId !== newAvatar.publicId) {
    await destroyCloudinaryAsset(previousAvatarPublicId, "image").catch(() => null);
  }

  const updatedGroup = await populateGroupConversationRelations(GroupConversation.findById(group._id));

  return {
    group: formatGroupConversation({ group: updatedGroup, currentUserId, unreadCount: 0 }),
    participantIds: updatedGroup.members.map((member) => member.user._id.toString()),
  };
};

const removeGroupMember = async (currentUserId, groupId, targetUserId) => {
  const group = await ensureGroupMember(groupId, currentUserId);
  ensureGroupOwner(group, currentUserId);

  if (group.owner._id.toString() === targetUserId.toString()) {
    throw new ApiError(400, "Group owner cannot be removed");
  }

  const existingMember = group.members.some((member) => member.user._id.toString() === targetUserId.toString());
  if (!existingMember) {
    throw new ApiError(404, "Group member not found");
  }

  group.members = group.members.filter((member) => member.user._id.toString() !== targetUserId.toString());
  await group.save();

  return {
    removed: true,
    userId: targetUserId,
    groupId: group._id.toString(),
    participantIds: group.members.map((member) => member.user._id.toString()),
  };
};

const leaveGroupChat = async (currentUserId, groupId) => {
  const group = await ensureGroupMember(groupId, currentUserId);

  if ((group.owner._id || group.owner).toString() === currentUserId.toString()) {
    throw new ApiError(400, "Group owner cannot leave without transferring ownership");
  }

  group.members = group.members.filter((member) => member.user._id.toString() !== currentUserId.toString());
  await group.save();

  return {
    left: true,
    groupId: group._id.toString(),
    userId: currentUserId.toString(),
    participantIds: group.members.map((member) => member.user._id.toString()),
  };
};

const markGroupMessageSeen = async (currentUserId, groupId, messageId) => {
  await ensureGroupMember(groupId, currentUserId);
  const message = await GroupMessage.findOne({ _id: messageId, groupConversation: groupId });
  if (!message) {
    throw new ApiError(404, "Group message not found");
  }

  const freshMessage = await GroupMessage.findById(messageId);
  const alreadySeen = freshMessage.seenBy.some((userId) => userId.toString() === currentUserId.toString());
  const seenAt = new Date();
  if (!alreadySeen) {
    freshMessage.seenBy.push(currentUserId);
    freshMessage.seenByRecords.push({ user: currentUserId, seenAt });
    await freshMessage.save();
  }
  const populatedMessage = await populateMessageRelations(GroupMessage.findById(freshMessage._id));
  const formattedMessage = formatGroupMessage(populatedMessage, currentUserId);
  const event = await recordChatEvent({
    targetType: "group",
    targetId: groupId,
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
  const participantIds = await getGroupAudienceUserIds(groupId);
  logger.info("message_seen_updated", buildGroupTrace({
    targetId: groupId,
    currentUserId,
    clientId: freshMessage.clientId || null,
    messageId: freshMessage._id,
    sequence: event.sequence,
    eventType: "message.seen",
  }));

  return {
    message: attachSequence(formattedMessage, event.sequence),
    groupId,
    participantIds,
    seenAt: seenAt.toISOString(),
    event,
  };
};

module.exports = {
  createGroupChat,
  getGroupChats,
  getGroupChatById,
  getGroupMembers,
  getGroupMessages,
  searchGroupMessages,
  getGroupEvents,
  getGroupAudienceUserIds,
  sendGroupMessage,
  updateGroupMessageReaction,
  removeGroupMessageReaction,
  deleteGroupMessage,
  editGroupMessage,
  forwardGroupMessage,
  pinGroupMessage,
  unpinGroupMessage,
  addGroupMembers,
  updateGroupChat,
  removeGroupMember,
  leaveGroupChat,
  markGroupMessageSeen,
};
