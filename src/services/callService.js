const ApiError = require("../utils/ApiError");
const CallSession = require("../models/CallSession");
const User = require("../models/User");
const { getPagination } = require("../utils/pagination");
const chatService = require("./chatService");
const { createAuditLog } = require("./auditLogService");
const { sendPushToUser } = require("./pushNotificationService");
const logger = require("../utils/logger");

const participantProjection = "username fullName avatar";

const buildUserPreview = (user) => ({
  _id: user._id,
  username: user.username,
  fullName: user.fullName,
  avatar: user.avatar,
});

const formatCallSession = (session, currentUserId) => ({
  _id: session._id,
  initiator: session.initiator?._id ? buildUserPreview(session.initiator) : session.initiator,
  participants: session.participants.map((participant) =>
    participant?._id ? buildUserPreview(participant) : participant
  ),
  conversationId: session.conversationId,
  groupId: session.groupId,
  callType: session.callType,
  status: session.status,
  startedAt: session.startedAt,
  answeredAt: session.answeredAt,
  endedAt: session.endedAt,
  lastSignalAt: session.lastSignalAt,
  createdAt: session.createdAt,
  updatedAt: session.updatedAt,
  isInitiator: session.initiator?._id
    ? session.initiator._id.toString() === currentUserId.toString()
    : session.initiator.toString() === currentUserId.toString(),
});

const getCallSessionOrThrow = async (callId) => {
  const session = await CallSession.findById(callId)
    .populate("initiator", participantProjection)
    .populate("participants", participantProjection);

  if (!session) {
    throw new ApiError(404, "Call session not found");
  }

  return session;
};

const ensureCallParticipant = (session, currentUserId) => {
  const isParticipant = session.participants.some((participant) => participant._id.toString() === currentUserId.toString());
  if (!isParticipant) {
    throw new ApiError(403, "Only call participants can access this call");
  }
};

const startDirectCall = async (currentUserId, payload) => {
  const conversation = await chatService.ensureConversationParticipant(payload.conversationId, currentUserId);
  const participantIds = conversation.participants.map((participant) => participant._id.toString());
  const recipientIds = participantIds.filter((participantId) => participantId !== currentUserId.toString());

  const session = await CallSession.create({
    initiator: currentUserId,
    participants: participantIds,
    conversationId: conversation._id,
    groupId: null,
    callType: payload.callType,
    status: "ringing",
    startedAt: new Date(),
    lastSignalAt: new Date(),
  });

  const hydratedSession = await getCallSessionOrThrow(session._id);
  await createAuditLog({
    actor: currentUserId,
    action: "call.offer",
    category: "call",
    status: "success",
    conversation: conversation._id,
    details: { callId: session._id.toString(), callType: payload.callType },
  });

  const caller = hydratedSession.initiator;
  await Promise.all(
    recipientIds.map((recipientId) =>
      sendPushToUser(
        recipientId,
        {
          title: caller.fullName || caller.username || "Incoming call",
          body: `Incoming ${payload.callType} call`,
          data: {
            callId: hydratedSession._id,
            conversationId: conversation._id,
            callType: payload.callType,
            action: "incoming_call",
          },
          notificationType: "call",
        },
        {
          notificationType: "call",
        }
      ).catch((error) => {
        logger.error("Incoming call push failed", {
          callId: hydratedSession._id.toString(),
          recipientId,
          error: error.message,
        });
      })
    )
  );

  return {
    session: formatCallSession(hydratedSession, currentUserId),
    participantIds,
    recipientIds,
  };
};

const answerCall = async (currentUserId, callId) => {
  const session = await getCallSessionOrThrow(callId);
  ensureCallParticipant(session, currentUserId);

  if (session.status !== "ringing" && session.status !== "accepted") {
    throw new ApiError(400, "Call is no longer active");
  }

  if (session.status === "ringing") {
    session.status = "accepted";
    session.answeredAt = new Date();
  }
  session.lastSignalAt = new Date();
  await session.save();

  return {
    session: formatCallSession(session, currentUserId),
    participantIds: session.participants.map((participant) => participant._id.toString()),
  };
};

const relayIceCandidate = async (currentUserId, callId, payload = {}) => {
  const session = await getCallSessionOrThrow(callId);
  ensureCallParticipant(session, currentUserId);

  if (!["ringing", "accepted"].includes(session.status)) {
    throw new ApiError(400, "Call is not active");
  }

  session.lastSignalAt = new Date();
  await session.save();

  const participantIds = session.participants.map((participant) => participant._id.toString());
  const recipientIds = payload.targetUserId
    ? participantIds.filter((participantId) => participantId === payload.targetUserId.toString())
    : participantIds.filter((participantId) => participantId !== currentUserId.toString());

  if (!recipientIds.length) {
    throw new ApiError(400, "Valid target participant is required");
  }

  return {
    session: formatCallSession(session, currentUserId),
    participantIds,
    recipientIds,
  };
};

const declineCall = async (currentUserId, callId) => {
  const session = await getCallSessionOrThrow(callId);
  ensureCallParticipant(session, currentUserId);

  if (session.status !== "ringing") {
    throw new ApiError(400, "Only ringing calls can be declined");
  }

  session.status = "declined";
  session.endedAt = new Date();
  session.lastSignalAt = new Date();
  await session.save();

  return {
    session: formatCallSession(session, currentUserId),
    participantIds: session.participants.map((participant) => participant._id.toString()),
  };
};

const endCall = async (currentUserId, callId) => {
  const session = await getCallSessionOrThrow(callId);
  ensureCallParticipant(session, currentUserId);

  if (["declined", "ended", "missed", "cancelled"].includes(session.status)) {
    return {
      session: formatCallSession(session, currentUserId),
      participantIds: session.participants.map((participant) => participant._id.toString()),
    };
  }

  if (session.status === "ringing") {
    const initiatorId = session.initiator._id.toString();
    session.status = initiatorId === currentUserId.toString() ? "cancelled" : "declined";
  } else {
    session.status = "ended";
  }

  session.endedAt = new Date();
  session.lastSignalAt = new Date();
  await session.save();

  return {
    session: formatCallSession(session, currentUserId),
    participantIds: session.participants.map((participant) => participant._id.toString()),
  };
};

const timeoutCall = async (currentUserId, callId) => {
  const session = await getCallSessionOrThrow(callId);
  ensureCallParticipant(session, currentUserId);

  if (session.status !== "ringing") {
    throw new ApiError(400, "Only ringing calls can be timed out");
  }

  session.status = "missed";
  session.endedAt = new Date();
  session.lastSignalAt = new Date();
  await session.save();

  return {
    session: formatCallSession(session, currentUserId),
    participantIds: session.participants.map((participant) => participant._id.toString()),
  };
};

const getCallHistory = async (currentUserId, query = {}) => {
  const { page, limit, skip } = getPagination(query);
  const filter = { participants: currentUserId };

  const [items, total] = await Promise.all([
    CallSession.find(filter)
      .populate("initiator", participantProjection)
      .populate("participants", participantProjection)
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit),
    CallSession.countDocuments(filter),
  ]);

  return {
    items: items.map((item) => formatCallSession(item, currentUserId)),
    meta: {
      page,
      limit,
      total,
      totalPages: Math.ceil(total / limit) || 1,
    },
  };
};

const getCallById = async (currentUserId, callId) => {
  const session = await getCallSessionOrThrow(callId);
  ensureCallParticipant(session, currentUserId);
  return formatCallSession(session, currentUserId);
};

module.exports = {
  startDirectCall,
  answerCall,
  relayIceCandidate,
  declineCall,
  endCall,
  timeoutCall,
  getCallHistory,
  getCallById,
};
