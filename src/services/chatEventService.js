const ApiError = require("../utils/ApiError");
const ChatEvent = require("../models/ChatEvent");
const Conversation = require("../models/Conversation");
const GroupConversation = require("../models/GroupConversation");
const logger = require("../utils/logger");
const { incrementCounter } = require("./chatMetricsService");

const DEFAULT_EVENT_LIMIT = 100;
const MAX_EVENT_LIMIT = 500;

const getTargetModel = (targetType) => {
  if (targetType === "direct") return Conversation;
  if (targetType === "group") return GroupConversation;
  throw new ApiError(400, "Unsupported chat event target type");
};

const normalizeLimit = (value) => {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isFinite(parsed) || parsed <= 0) return DEFAULT_EVENT_LIMIT;
  return Math.min(parsed, MAX_EVENT_LIMIT);
};

const normalizeAfter = (value) => {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isFinite(parsed) || parsed < 0) return 0;
  return parsed;
};

const allocateSequence = async (targetType, targetId) => {
  const Model = getTargetModel(targetType);
  const target = await Model.findByIdAndUpdate(
    targetId,
    { $inc: { latestSequence: 1 } },
    { new: true, projection: "latestSequence" }
  );

  if (!target) {
    throw new ApiError(404, targetType === "group" ? "Group chat not found" : "Conversation not found");
  }

  return target.latestSequence;
};

const serializeEvent = (event) => ({
  sequence: event.sequence,
  type: event.type,
  targetType: event.targetType,
  targetId: event.targetId?.toString(),
  messageId: event.messageId ? event.messageId.toString() : null,
  clientId: event.clientId || null,
  actorId: event.actorId ? event.actorId.toString() : null,
  createdAt: event.createdAt?.toISOString?.() || event.createdAt,
  payload: event.payload || {},
});

const recordChatEvent = async ({
  targetType,
  targetId,
  type,
  messageId = null,
  clientId = null,
  actorId = null,
  payload = {},
}) => {
  const sequence = await allocateSequence(targetType, targetId);
  const resolvedPayload = typeof payload === "function" ? payload(sequence) : payload;
  const event = await ChatEvent.create({
    targetType,
    targetId,
    sequence,
    type,
    messageId,
    clientId,
    actorId,
    payload: resolvedPayload || {},
  });

  logger.info("chat_event_written", {
    targetType,
    targetId: targetId?.toString?.() || targetId,
    sequence,
    eventType: type,
    messageId: messageId?.toString?.() || null,
    clientId: clientId || null,
    actorId: actorId?.toString?.() || actorId || null,
  });

  return serializeEvent(event);
};

const listChatEvents = async ({ targetType, targetId, after = 0, limit = DEFAULT_EVENT_LIMIT }) => {
  const normalizedAfter = normalizeAfter(after);
  const normalizedLimit = normalizeLimit(limit);
  incrementCounter("chat_replay_sync_request_total", { targetType });
  const rows = await ChatEvent.find({
    targetType,
    targetId,
    sequence: { $gt: normalizedAfter },
  })
    .sort({ sequence: 1 })
    .limit(normalizedLimit + 1)
    .lean();

  const hasMore = rows.length > normalizedLimit;
  const events = rows.slice(0, normalizedLimit).map(serializeEvent);
  incrementCounter("chat_replay_sync_event_count", { targetType }, events.length);
  return {
    targetId: targetId.toString(),
    targetType,
    fromSequence: normalizedAfter,
    toSequence: events.length ? events[events.length - 1].sequence : normalizedAfter,
    events,
    hasMore,
  };
};

module.exports = {
  recordChatEvent,
  listChatEvents,
};
