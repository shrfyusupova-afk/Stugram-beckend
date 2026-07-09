jest.mock("../../src/socket/socketServer", () => {
  const ioTarget = {
    emit: jest.fn(),
  };

  const io = {
    to: jest.fn(() => ioTarget),
    emit: jest.fn(),
  };

  return {
    initSocketServer: jest.fn(() => io),
    getIo: jest.fn(() => io),
    closeSocketServer: jest.fn(async () => undefined),
  };
});

const { setupIntegrationTestSuite } = require("../helpers/integration");
const { authHeader, createAuthenticatedUser } = require("../helpers/factories");
const { env } = require("../../src/config/env");

const { getClient } = setupIntegrationTestSuite();

const mp4LikeBuffer = Buffer.from([
  0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70,
  0x4d, 0x34, 0x41, 0x20, 0x00, 0x00, 0x00, 0x00,
  0x4d, 0x34, 0x41, 0x20,
]);

const expectFeatureDisabledEnvelope = (response, code, feature) => {
  expect(response.statusCode).toBe(503);
  expect(response.body.success).toBe(false);
  expect(response.body.message).toBe("This feature is temporarily disabled.");
  expect(response.body.data).toBeNull();
  expect(response.body.meta).toEqual(
    expect.objectContaining({
      requestId: expect.any(String),
      timestamp: expect.any(String),
    })
  );
  expect(response.body.error).toEqual({
    code,
    details: { feature },
  });
};

describe("Kill switches", () => {
  afterEach(() => {
    env.chatGroupSendEnabled = true;
    env.chatMediaSendEnabled = true;
    env.chatReplaySyncEnabled = true;
    env.chatRealtimeEnabled = true;
    env.recommendationMode = "db-direct";
  });

  it("blocks group send when CHAT_GROUP_SEND_ENABLED=false", async () => {
    const client = getClient();
    const { accessToken: ownerToken } = await createAuthenticatedUser({
      identity: "kill-group-owner@example.com",
      username: "kill_group_owner",
    });

    env.chatGroupSendEnabled = false;

    const response = await client
      .post("/api/v1/group-chats/507f1f77bcf86cd799439011/messages")
      .set(authHeader(ownerToken))
      .send({ text: "blocked" });

    expectFeatureDisabledEnvelope(response, "FEATURE_GROUP_SEND_DISABLED", "group_send");
  });

  it("blocks chat media send when CHAT_MEDIA_SEND_ENABLED=false", async () => {
    const client = getClient();
    const { user: sender, accessToken: senderToken } = await createAuthenticatedUser({
      identity: "kill-media-one@example.com",
      username: "kill_media_one",
    });
    const { user: receiver } = await createAuthenticatedUser({
      identity: "kill-media-two@example.com",
      username: "kill_media_two",
    });

    const conversationResponse = await client
      .post("/api/v1/chats/conversations")
      .set(authHeader(senderToken))
      .send({ participantId: receiver._id.toString() });

    expect(conversationResponse.statusCode).toBe(201);

    env.chatMediaSendEnabled = false;

    const response = await client
      .post(`/api/v1/chats/conversations/${conversationResponse.body.data._id}/messages/media`)
      .set(authHeader(senderToken))
      .field("messageType", "voice")
      .attach("media", mp4LikeBuffer, {
        filename: "voice-note.m4a",
        contentType: "audio/mp4",
      });

    expectFeatureDisabledEnvelope(response, "FEATURE_MEDIA_SEND_DISABLED", "media_send");
  });

  it("blocks replay endpoint when CHAT_REPLAY_ENABLED=false", async () => {
    const client = getClient();
    const { accessToken } = await createAuthenticatedUser({
      identity: "kill-replay@example.com",
      username: "kill_replay",
    });

    env.chatReplaySyncEnabled = false;

    const response = await client
      .get("/api/v1/chats/events")
      .set(authHeader(accessToken));

    expectFeatureDisabledEnvelope(response, "FEATURE_CHAT_REPLAY_DISABLED", "chat_replay");
  });

  it("returns disabled envelope for recommendation endpoints when RECOMMENDATION_MODE=disabled", async () => {
    const client = getClient();
    const { accessToken } = await createAuthenticatedUser({
      identity: "kill-reco@example.com",
      username: "kill_reco",
    });

    env.recommendationMode = "disabled";

    const response = await client
      .get("/api/v1/feed/me")
      .set(authHeader(accessToken));

    expectFeatureDisabledEnvelope(response, "FEATURE_RECOMMENDATIONS_DISABLED", "recommendations");
  });

  it("keeps REST direct message send working when CHAT_REALTIME_ENABLED=false", async () => {
    const client = getClient();
    const { user: sender, accessToken: senderToken } = await createAuthenticatedUser({
      identity: "kill-rt-one@example.com",
      username: "kill_rt_one",
    });
    const { user: receiver } = await createAuthenticatedUser({
      identity: "kill-rt-two@example.com",
      username: "kill_rt_two",
    });

    const conversationResponse = await client
      .post("/api/v1/chats/conversations")
      .set(authHeader(senderToken))
      .send({ participantId: receiver._id.toString() });

    expect(conversationResponse.statusCode).toBe(201);

    env.chatRealtimeEnabled = false;

    const response = await client
      .post(`/api/v1/chats/conversations/${conversationResponse.body.data._id}/messages`)
      .set(authHeader(senderToken))
      .send({ text: "rest still works" });

    expect(response.statusCode).toBe(201);
    expect(response.body.success).toBe(true);
    expect(response.body.data.text).toBe("rest still works");
  });
});
