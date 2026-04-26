const mongoose = require("mongoose");
jest.mock("../../src/utils/logger", () => ({
  info: jest.fn(),
  warn: jest.fn(),
  error: jest.fn(),
}));
jest.mock("../../src/services/chatMetricsService", () => ({
  incrementCounter: jest.fn(),
}));
const {
  initializeTestEnvironment,
  resetDatabase,
  closeTestEnvironment,
} = require("../helpers/testEnvironment");
const Conversation = require("../../src/models/Conversation");
const GroupConversation = require("../../src/models/GroupConversation");
const { recordChatEvent, listChatEvents } = require("../../src/services/chatEventService");
const logger = require("../../src/utils/logger");
const { incrementCounter } = require("../../src/services/chatMetricsService");

describe("chatEventService", () => {
  beforeAll(async () => {
    await initializeTestEnvironment();
  });

  afterEach(async () => {
    await resetDatabase();
  });

  afterAll(async () => {
    await closeTestEnvironment();
  });

  it("allocates strictly increasing direct conversation sequences and replays ordered subsets", async () => {
    const userA = new mongoose.Types.ObjectId();
    const userB = new mongoose.Types.ObjectId();
    const conversation = await Conversation.create({
      participants: [userA, userB],
      createdBy: userA,
    });

    const events = await Promise.all([
      recordChatEvent({
        targetType: "direct",
        targetId: conversation._id,
        type: "message.created",
        actorId: userA,
        payload: (sequence) => ({ serverSequence: sequence, ordinal: 1 }),
      }),
      recordChatEvent({
        targetType: "direct",
        targetId: conversation._id,
        type: "message.edited",
        actorId: userA,
        payload: (sequence) => ({ serverSequence: sequence, ordinal: 2 }),
      }),
      recordChatEvent({
        targetType: "direct",
        targetId: conversation._id,
        type: "message.reactions",
        actorId: userB,
        payload: (sequence) => ({ serverSequence: sequence, ordinal: 3 }),
      }),
    ]);

    expect(new Set(events.map((event) => event.sequence)).size).toBe(3);
    expect([...events.map((event) => event.sequence)].sort((a, b) => a - b)).toEqual([1, 2, 3]);

    const replay = await listChatEvents({
      targetType: "direct",
      targetId: conversation._id,
      after: 1,
      limit: 10,
    });

    expect(replay.events.map((event) => event.sequence)).toEqual([2, 3]);
    expect(replay.fromSequence).toBe(1);
    expect(replay.toSequence).toBe(3);
    expect(replay.hasMore).toBe(false);
    expect(logger.info).toHaveBeenCalledWith(
      "chat_event_written",
      expect.objectContaining({
        targetType: "direct",
        sequence: expect.any(Number),
      })
    );
    expect(incrementCounter).toHaveBeenCalledWith(
      "chat_replay_sync_request_total",
      expect.objectContaining({ targetType: "direct" })
    );
    expect(incrementCounter).toHaveBeenCalledWith(
      "chat_replay_sync_event_count",
      expect.objectContaining({ targetType: "direct" }),
      2
    );
  });

  it("keeps group conversation sequences isolated from direct conversations", async () => {
    const owner = new mongoose.Types.ObjectId();
    const member = new mongoose.Types.ObjectId();
    const group = await GroupConversation.create({
      name: "Replay group",
      owner,
      members: [{ user: owner }, { user: member }],
    });

    const first = await recordChatEvent({
      targetType: "group",
      targetId: group._id,
      type: "message.created",
      actorId: owner,
      payload: (sequence) => ({ serverSequence: sequence }),
    });
    const second = await recordChatEvent({
      targetType: "group",
      targetId: group._id,
      type: "message.deleted",
      actorId: owner,
      payload: (sequence) => ({ serverSequence: sequence }),
    });

    const replay = await listChatEvents({
      targetType: "group",
      targetId: group._id,
      after: 0,
      limit: 1,
    });

    expect(first.sequence).toBe(1);
    expect(second.sequence).toBe(2);
    expect(replay.events.map((event) => event.sequence)).toEqual([1]);
    expect(replay.hasMore).toBe(true);
  });
});
