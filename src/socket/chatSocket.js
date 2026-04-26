const chatService = require("../services/chatService");
const groupChatService = require("../services/groupChatService");
const callService = require("../services/callService");
const Conversation = require("../models/Conversation");
const GroupConversation = require("../models/GroupConversation");
const User = require("../models/User");
const { env } = require("../config/env");
const logger = require("../utils/logger");
const { takeSocketToken } = require("./socketRateLimit");
const { incrementCounter } = require("../services/chatMetricsService");

const joinConversationRoom = (conversationId) => `conversation:${conversationId}`;
const joinGroupRoom = (groupId) => `group:${groupId}`;
const joinCallRoom = (callId) => `call:${callId}`;
const joinUserRoom = (userId) => `user:${userId}`;
const isChatRealtimeEnabled = () => env.chatRealtimeEnabled;

const recordSocketEmit = ({ eventName, userIds, targetType = null, targetId = null, payload = null }) => {
  const event = payload?.event || null;
  const message = payload?.message || payload || null;
  logger.info("socket_event_emitted", {
    eventType: eventName,
    targetType,
    targetId: targetId || payload?.conversationId || payload?.groupId || null,
    sequence: event?.sequence || payload?.sequence || null,
    messageId: message?._id || payload?.messageId || event?.messageId || null,
    recipientCount: userIds.length,
  });
  incrementCounter("chat_socket_emit_total", {
    eventType: eventName,
    targetType: targetType || "unknown",
  });
};

const emitConversationUpdated = async (io, userIds, conversationId) => {
  if (!isChatRealtimeEnabled()) return;
  await Promise.all(
    userIds.map(async (userId) => {
      const conversation = await chatService.getConversationByIdForUser(userId, conversationId);
      io.to(joinUserRoom(userId)).emit("conversation_updated", conversation);
    })
  );
};

const emitNewMessage = (io, userIds, message) => {
  if (!isChatRealtimeEnabled()) return;
  recordSocketEmit({
    eventName: "new_message",
    userIds,
    targetType: "direct",
    targetId: message?.conversation || null,
    payload: message,
  });
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("new_message", message);
  });
};

const emitMessageSeen = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  recordSocketEmit({ eventName: "message_seen", userIds, targetType: "direct", payload });
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("message_seen", payload);
  });
};

const emitMessageReactionUpdated = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  recordSocketEmit({ eventName: "message_reaction_updated", userIds, targetType: "direct", payload });
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("message_reaction_updated", payload);
  });
};

const emitMessageDeleted = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  recordSocketEmit({ eventName: "message_deleted", userIds, targetType: "direct", payload });
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("message_deleted", payload);
  });
};

const emitMessageDeletedForEveryone = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  recordSocketEmit({ eventName: "message_deleted_for_everyone", userIds, targetType: "direct", payload });
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("message_deleted_for_everyone", payload);
  });
};

const emitMessageEdited = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  recordSocketEmit({ eventName: "message_edited", userIds, targetType: "direct", payload });
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("message_edited", payload);
  });
};

const emitMessageForwarded = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  recordSocketEmit({ eventName: "message_forwarded", userIds, targetType: "direct", payload });
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("message_forwarded", payload);
  });
};

const emitMessagePinned = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("message_pinned", payload);
  });
};

const emitMessageUnpinned = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("message_unpinned", payload);
  });
};

const emitMessageDelivered = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  recordSocketEmit({ eventName: "message_delivered", userIds, targetType: payload?.groupId ? "group" : "direct", payload });
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("message_delivered", payload);
  });
};

const emitGroupConversationUpdated = async (io, userIds, groupId) => {
  if (!isChatRealtimeEnabled()) return;
  await Promise.all(
    userIds.map(async (userId) => {
      try {
        const group = await groupChatService.getGroupChatById(userId, groupId);
        io.to(joinUserRoom(userId)).emit("group_conversation_updated", group);
      } catch (_error) {
        return null;
      }
      return null;
    })
  );
};

const emitNewGroupMessage = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  recordSocketEmit({ eventName: "group_message", userIds, targetType: "group", targetId: payload?.groupId, payload });
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("group_message", payload);
  });
};

const emitGroupMessageSeen = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  recordSocketEmit({ eventName: "group_message_seen", userIds, targetType: "group", targetId: payload?.groupId, payload });
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("group_message_seen", payload);
  });
};

const emitGroupMessageReactionUpdated = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  recordSocketEmit({ eventName: "group_message_reaction_updated", userIds, targetType: "group", targetId: payload?.groupId, payload });
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("group_message_reaction_updated", payload);
  });
};

const emitGroupMessageDeleted = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  recordSocketEmit({ eventName: "group_message_deleted", userIds, targetType: "group", targetId: payload?.groupId, payload });
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("group_message_deleted", payload);
  });
};

const emitGroupMessageDeletedForEveryone = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  recordSocketEmit({ eventName: "group_message_deleted_for_everyone", userIds, targetType: "group", targetId: payload?.groupId, payload });
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("group_message_deleted_for_everyone", payload);
  });
};

const emitGroupMessageEdited = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  recordSocketEmit({ eventName: "group_message_edited", userIds, targetType: "group", targetId: payload?.groupId, payload });
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("group_message_edited", payload);
  });
};

const emitGroupMessageForwarded = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  recordSocketEmit({ eventName: "group_message_forwarded", userIds, targetType: "group", targetId: payload?.groupId, payload });
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("group_message_forwarded", payload);
  });
};

const emitGroupMessagePinned = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("group_message_pinned", payload);
  });
};

const emitGroupMessageUnpinned = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("group_message_unpinned", payload);
  });
};

const emitGroupMemberAdded = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("group_member_added", payload);
  });
};

const emitGroupMemberRemoved = (io, userIds, payload) => {
  if (!isChatRealtimeEnabled()) return;
  userIds.forEach((userId) => {
    io.to(joinUserRoom(userId)).emit("group_member_removed", payload);
  });
};

const onlineUsers = new Map();

const getUserSocketSet = (userId) => onlineUsers.get(userId) || new Set();

const registerUserSocket = (userId, socketId) => {
  const userKey = userId.toString();
  const sockets = getUserSocketSet(userKey);
  sockets.add(socketId);
  onlineUsers.set(userKey, sockets);
  return sockets.size;
};

const unregisterUserSocket = (userId, socketId) => {
  const userKey = userId.toString();
  const sockets = getUserSocketSet(userKey);
  sockets.delete(socketId);
  if (sockets.size === 0) {
    onlineUsers.delete(userKey);
    return 0;
  }
  onlineUsers.set(userKey, sockets);
  return sockets.size;
};

const isUserConnected = (userId) => {
  const sockets = onlineUsers.get(userId.toString());
  return Boolean(sockets && sockets.size > 0);
};

const getPresenceAudience = async (userId) => {
  const [directConversations, groups] = await Promise.all([
    Conversation.find({ participants: userId }).select("participants").lean(),
    GroupConversation.find({ "members.user": userId }).select("members.user").lean(),
  ]);

  const audience = new Set();
  directConversations.forEach((conversation) => {
    conversation.participants.forEach((participantId) => {
      const normalized = participantId.toString();
      if (normalized !== userId.toString()) {
        audience.add(normalized);
      }
    });
  });
  groups.forEach((group) => {
    group.members.forEach((member) => {
      const normalized = member.user.toString();
      if (normalized !== userId.toString()) {
        audience.add(normalized);
      }
    });
  });

  return [...audience];
};

const emitPresence = async (io, userId, eventName) => {
  const audience = await getPresenceAudience(userId);
  const payload = {
    userId,
    lastSeenAt: eventName === "user_offline" ? new Date().toISOString() : null,
  };

  audience.forEach((recipientId) => {
    io.to(joinUserRoom(recipientId)).emit(eventName, payload);
  });
};

const registerChatSocket = (io) => {
  io.on("connection", (socket) => {
    logger.info("chat_socket_connected", {
      userId: socket.user.id,
      socketId: socket.id,
    });
    const presenceCount = registerUserSocket(socket.user.id.toString(), socket.id);
    if (presenceCount === 1) {
      emitPresence(io, socket.user.id.toString(), "user_online").catch(() => null);
    }

    socket.on("conversation:join", async ({ conversationId }, ack) => {
      try {
        if (!(await takeSocketToken({ userId: socket.user.id, eventName: "conversation_join", limit: 20, windowMs: 60 * 1000 }))) {
          ack?.({ ok: false, message: "Too many join requests" });
          return;
        }
        const conversation = await chatService.ensureConversationParticipant(conversationId, socket.user.id);
        socket.join(joinConversationRoom(conversation.id));
        ack?.({ ok: true });
      } catch (error) {
        ack?.({ ok: false, message: error.message });
      }
    });

    socket.on("group_chat:join", async ({ groupId }, ack) => {
      try {
        if (!(await takeSocketToken({ userId: socket.user.id, eventName: "group_chat_join", limit: 20, windowMs: 60 * 1000 }))) {
          ack?.({ ok: false, message: "Too many join requests" });
          return;
        }
        await groupChatService.getGroupChatById(socket.user.id, groupId);
        socket.join(joinGroupRoom(groupId));
        ack?.({ ok: true });
      } catch (error) {
        ack?.({ ok: false, message: error.message });
      }
    });

    socket.on("call_offer", async ({ conversationId, callType, offer }, ack) => {
      try {
        if (!(await takeSocketToken({ userId: socket.user.id, eventName: "call_offer", limit: 6, windowMs: 60 * 1000 }))) {
          ack?.({ ok: false, message: "Too many call attempts" });
          return;
        }
        if (!conversationId || !["audio", "video"].includes(callType)) {
          ack?.({ ok: false, message: "conversationId and valid callType are required" });
          return;
        }

        const result = await callService.startDirectCall(socket.user.id, { conversationId, callType });
        socket.join(joinCallRoom(result.session._id));

        result.recipientIds.forEach((recipientId) => {
          io.to(joinUserRoom(recipientId)).emit("call_offer", {
            callId: result.session._id,
            conversationId: result.session.conversationId,
            callType: result.session.callType,
            from: {
              _id: socket.user.id,
              username: socket.user.username,
              fullName: socket.user.fullName,
              avatar: socket.user.avatar,
            },
            offer: offer || null,
            session: result.session,
          });
        });

        ack?.({ ok: true, data: result.session });
      } catch (error) {
        ack?.({ ok: false, message: error.message });
      }
    });

    socket.on("call_answer", async ({ callId, answer }, ack) => {
      try {
        if (!(await takeSocketToken({ userId: socket.user.id, eventName: "call_answer", limit: 10, windowMs: 60 * 1000 }))) {
          ack?.({ ok: false, message: "Too many call answer attempts" });
          return;
        }
        const result = await callService.answerCall(socket.user.id, callId);
        socket.join(joinCallRoom(callId));

        result.participantIds
          .filter((participantId) => participantId !== socket.user.id.toString())
          .forEach((participantId) => {
            io.to(joinUserRoom(participantId)).emit("call_answer", {
              callId,
              answer: answer || null,
              answeredBy: {
                _id: socket.user.id,
                username: socket.user.username,
                fullName: socket.user.fullName,
                avatar: socket.user.avatar,
              },
              session: result.session,
            });
          });

        ack?.({ ok: true, data: result.session });
      } catch (error) {
        ack?.({ ok: false, message: error.message });
      }
    });

    socket.on("call_ice_candidate", async ({ callId, candidate, targetUserId }, ack) => {
      try {
        if (!(await takeSocketToken({ userId: socket.user.id, eventName: "call_ice_candidate", limit: 120, windowMs: 60 * 1000 }))) {
          ack?.({ ok: false, message: "Too many ICE candidate events" });
          return;
        }
        const result = await callService.relayIceCandidate(socket.user.id, callId, { targetUserId });

        result.recipientIds.forEach((recipientId) => {
          io.to(joinUserRoom(recipientId)).emit("call_ice_candidate", {
            callId,
            fromUserId: socket.user.id,
            targetUserId: recipientId,
            candidate: candidate || null,
          });
        });

        ack?.({ ok: true });
      } catch (error) {
        ack?.({ ok: false, message: error.message });
      }
    });

    socket.on("call_decline", async ({ callId }, ack) => {
      try {
        const result = await callService.declineCall(socket.user.id, callId);

        result.participantIds
          .filter((participantId) => participantId !== socket.user.id.toString())
          .forEach((participantId) => {
            io.to(joinUserRoom(participantId)).emit("call_decline", {
              callId,
              declinedByUserId: socket.user.id,
              session: result.session,
            });
          });

        ack?.({ ok: true, data: result.session });
      } catch (error) {
        ack?.({ ok: false, message: error.message });
      }
    });

    socket.on("call_end", async ({ callId }, ack) => {
      try {
        const result = await callService.endCall(socket.user.id, callId);

        result.participantIds
          .filter((participantId) => participantId !== socket.user.id.toString())
          .forEach((participantId) => {
            io.to(joinUserRoom(participantId)).emit("call_end", {
              callId,
              endedByUserId: socket.user.id,
              session: result.session,
            });
          });

        ack?.({ ok: true, data: result.session });
      } catch (error) {
        ack?.({ ok: false, message: error.message });
      }
    });

    socket.on("call_timeout", async ({ callId }, ack) => {
      try {
        const result = await callService.timeoutCall(socket.user.id, callId);

        result.participantIds
          .filter((participantId) => participantId !== socket.user.id.toString())
          .forEach((participantId) => {
            io.to(joinUserRoom(participantId)).emit("call_timeout", {
              callId,
              session: result.session,
            });
          });

        ack?.({ ok: true, data: result.session });
      } catch (error) {
        ack?.({ ok: false, message: error.message });
      }
    });

    socket.on("typing_start", async ({ conversationId, groupId }, ack) => {
      try {
        if (!(await takeSocketToken({ userId: socket.user.id, eventName: "typing_start", limit: 10, windowMs: 5 * 1000 }))) {
          ack?.({ ok: false, message: "Typing event rate limit exceeded" });
          return;
        }
        if (conversationId) {
          const conversation = await chatService.ensureConversationParticipant(conversationId, socket.user.id);
          const receivers = conversation.participants
            .map((participant) => participant._id.toString())
            .filter((participantId) => participantId !== socket.user.id.toString());

          receivers.forEach((participantId) => {
            io.to(joinUserRoom(participantId)).emit("typing_start", {
              conversationId: conversation.id,
              type: "direct",
              user: {
                _id: socket.user.id,
                username: socket.user.username,
                fullName: socket.user.fullName,
                avatar: socket.user.avatar,
              },
            });
          });
        } else if (groupId) {
          const group = await groupChatService.getGroupChatById(socket.user.id, groupId);
          const receivers = group.members
            .map((member) => member._id.toString())
            .filter((memberId) => memberId !== socket.user.id.toString());

          receivers.forEach((memberId) => {
            io.to(joinUserRoom(memberId)).emit("typing_start", {
              groupId,
              type: "group",
              user: {
                _id: socket.user.id,
                username: socket.user.username,
                fullName: socket.user.fullName,
                avatar: socket.user.avatar,
              },
            });
          });
        } else {
          ack?.({ ok: false, message: "conversationId or groupId is required" });
          return;
        }
        ack?.({ ok: true });
      } catch (error) {
        ack?.({ ok: false, message: error.message });
      }
    });

    socket.on("typing_stop", async ({ conversationId, groupId }, ack) => {
      try {
        if (!(await takeSocketToken({ userId: socket.user.id, eventName: "typing_stop", limit: 10, windowMs: 5 * 1000 }))) {
          ack?.({ ok: false, message: "Typing event rate limit exceeded" });
          return;
        }
        if (conversationId) {
          const conversation = await chatService.ensureConversationParticipant(conversationId, socket.user.id);
          const receivers = conversation.participants
            .map((participant) => participant._id.toString())
            .filter((participantId) => participantId !== socket.user.id.toString());

          receivers.forEach((participantId) => {
            io.to(joinUserRoom(participantId)).emit("typing_stop", {
              conversationId: conversation.id,
              type: "direct",
              userId: socket.user.id,
            });
          });
        } else if (groupId) {
          const group = await groupChatService.getGroupChatById(socket.user.id, groupId);
          const receivers = group.members
            .map((member) => member._id.toString())
            .filter((memberId) => memberId !== socket.user.id.toString());

          receivers.forEach((memberId) => {
            io.to(joinUserRoom(memberId)).emit("typing_stop", {
              groupId,
              type: "group",
              userId: socket.user.id,
            });
          });
        } else {
          ack?.({ ok: false, message: "conversationId or groupId is required" });
          return;
        }
        ack?.({ ok: true });
      } catch (error) {
        ack?.({ ok: false, message: error.message });
      }
    });

    socket.on("disconnect", async (reason) => {
      const remaining = socket.user?.id ? unregisterUserSocket(socket.user.id.toString(), socket.id) : 0;
      if (!remaining && socket.user?.id) {
        emitPresence(io, socket.user.id.toString(), "user_offline").catch(() => null);
        await User.findByIdAndUpdate(socket.user.id, { lastSeenAt: new Date() }).catch(() => null);
      }
      logger.info("chat_socket_disconnected", {
        userId: socket.user?.id || "unknown",
        socketId: socket.id,
        reason,
      });
    });
  });
};

module.exports = {
  registerChatSocket,
  emitConversationUpdated,
  emitNewMessage,
  emitMessageSeen,
  emitMessageReactionUpdated,
  emitMessageDeleted,
  emitMessageDeletedForEveryone,
  emitMessageEdited,
  emitMessageForwarded,
  emitMessagePinned,
  emitMessageUnpinned,
  emitMessageDelivered,
  emitGroupConversationUpdated,
  emitNewGroupMessage,
  emitGroupMessageSeen,
  emitGroupMessageReactionUpdated,
  emitGroupMessageDeleted,
  emitGroupMessageDeletedForEveryone,
  emitGroupMessageEdited,
  emitGroupMessageForwarded,
  emitGroupMessagePinned,
  emitGroupMessageUnpinned,
  emitGroupMemberAdded,
  emitGroupMemberRemoved,
  joinConversationRoom,
  joinGroupRoom,
  joinCallRoom,
  joinUserRoom,
  isUserConnected,
  registerUserSocket,
  unregisterUserSocket,
};
