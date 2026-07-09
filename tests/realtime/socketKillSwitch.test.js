const { env } = require("../../src/config/env");

jest.mock("../../src/services/chatService", () => ({
  ensureConversationParticipant: jest.fn(async () => ({ id: "conv-1" })),
}));

jest.mock("../../src/services/groupChatService", () => ({
  getGroupChatById: jest.fn(async () => ({ _id: "group-1" })),
}));

jest.mock("../../src/services/callService", () => ({
  startDirectCall: jest.fn(),
  answerCall: jest.fn(),
  relayIceCandidate: jest.fn(),
  declineCall: jest.fn(),
  endCall: jest.fn(),
  timeoutCall: jest.fn(),
}));

jest.mock("../../src/models/Conversation", () => ({
  find: jest.fn(async () => []),
}));

jest.mock("../../src/models/GroupConversation", () => ({
  find: jest.fn(async () => []),
}));

jest.mock("../../src/models/User", () => ({
  findByIdAndUpdate: jest.fn(async () => null),
}));

jest.mock("../../src/socket/socketRateLimit", () => ({
  takeSocketToken: jest.fn(async () => true),
}));

jest.mock("../../src/services/chatMetricsService", () => ({
  incrementCounter: jest.fn(),
}));

const { registerChatSocket } = require("../../src/socket/chatSocket");

describe("Socket kill switches", () => {
  afterEach(() => {
    env.socketJoinConversationEnabled = true;
    jest.clearAllMocks();
  });

  const setupConnection = () => {
    const handlers = {};
    const socket = {
      id: "sock-1",
      user: { id: "user-1", username: "u1", fullName: "U1", avatar: null },
      on: jest.fn((event, cb) => {
        handlers[event] = cb;
      }),
      join: jest.fn(),
    };

    const io = {
      on: jest.fn((event, cb) => {
        if (event === "connection") cb(socket);
      }),
      to: jest.fn(() => ({ emit: jest.fn() })),
    };

    registerChatSocket(io);
    return { handlers, socket };
  };

  it("blocks conversation and group room joins when SOCKET_JOIN_CONVERSATION_ENABLED=false", async () => {
    env.socketJoinConversationEnabled = false;
    const { handlers } = setupConnection();

    const conversationAck = jest.fn();
    await handlers["conversation:join"]({ conversationId: "conv-1" }, conversationAck);
    expect(conversationAck).toHaveBeenCalledWith(
      expect.objectContaining({
        ok: false,
        code: "FEATURE_SOCKET_JOIN_CONVERSATION_DISABLED",
      })
    );

    const groupAck = jest.fn();
    await handlers["group_chat:join"]({ groupId: "group-1" }, groupAck);
    expect(groupAck).toHaveBeenCalledWith(
      expect.objectContaining({
        ok: false,
        code: "FEATURE_SOCKET_JOIN_CONVERSATION_DISABLED",
      })
    );
  });
});
