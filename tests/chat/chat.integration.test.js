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
const {
  authHeader,
  createAuthenticatedUser,
} = require("../helpers/factories");
const Message = require("../../src/models/Message");
const GroupMessage = require("../../src/models/GroupMessage");
const logger = require("../../src/utils/logger");

const { getClient } = setupIntegrationTestSuite();
const mp4LikeBuffer = Buffer.from([
  0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70,
  0x4d, 0x34, 0x41, 0x20, 0x00, 0x00, 0x00, 0x00,
  0x4d, 0x34, 0x41, 0x20,
]);
const pdfLikeBuffer = Buffer.from("%PDF-1.4\n1 0 obj\n<<>>\nendobj\n");
const structuredPostShare = JSON.stringify({
  type: "POST",
  title: "Campus sunset",
  subtitle: "@creator",
  targetId: "post_123",
});

describe("Chat integration", () => {
  it("supports direct conversations, messages, pagination, and replies", async () => {
    const client = getClient();
    const { user: firstUser, accessToken: firstToken } = await createAuthenticatedUser({
      identity: "direct-one@example.com",
      username: "direct_one",
    });
    const { user: secondUser } = await createAuthenticatedUser({
      identity: "direct-two@example.com",
      username: "direct_two",
    });

    const conversationResponse = await client
      .post("/api/v1/chats/conversations")
      .set(authHeader(firstToken))
      .send({ participantId: secondUser._id.toString() });

    expect(conversationResponse.statusCode).toBe(201);
    const conversationId = conversationResponse.body.data._id;

    const firstMessageResponse = await client
      .post(`/api/v1/chats/conversations/${conversationId}/messages`)
      .set(authHeader(firstToken))
      .send({ text: "Hello there" });

    expect(firstMessageResponse.statusCode).toBe(201);

    const replyResponse = await client
      .post(`/api/v1/chats/conversations/${conversationId}/messages`)
      .set(authHeader(firstToken))
      .send({
        text: "Replying",
        replyToMessageId: firstMessageResponse.body.data._id,
      });

    expect(replyResponse.statusCode).toBe(201);
    expect(replyResponse.body.data.replyToMessage._id.toString()).toBe(
      firstMessageResponse.body.data._id.toString()
    );

    const messagesResponse = await client
      .get(`/api/v1/chats/conversations/${conversationId}/messages`)
      .set(authHeader(firstToken));

    expect(messagesResponse.statusCode).toBe(200);
    expect(messagesResponse.body.data.length).toBe(2);

    const conversationSearchResponse = await client
      .get("/api/v1/chats/conversations/search")
      .set(authHeader(firstToken))
      .query({ q: "direct_two" });

    expect(conversationSearchResponse.statusCode).toBe(200);
    expect(conversationSearchResponse.body.data).toHaveLength(1);

    const messageSearchResponse = await client
      .get(`/api/v1/chats/conversations/${conversationId}/search`)
      .set(authHeader(firstToken))
      .query({ q: "Replying" });

    expect(messageSearchResponse.statusCode).toBe(200);
    expect(messageSearchResponse.body.data).toHaveLength(1);
    expect(messageSearchResponse.body.data[0].text).toBe("Replying");
  });

  it("supports direct voice/file uploads and structured share metadata", async () => {
    const client = getClient();
    const { user: firstUser, accessToken: firstToken } = await createAuthenticatedUser({
      identity: "direct-media-one@example.com",
      username: "direct_media_one",
    });
    const { user: secondUser } = await createAuthenticatedUser({
      identity: "direct-media-two@example.com",
      username: "direct_media_two",
    });

    const conversationResponse = await client
      .post("/api/v1/chats/conversations")
      .set(authHeader(firstToken))
      .send({ participantId: secondUser._id.toString() });

    expect(conversationResponse.statusCode).toBe(201);
    const conversationId = conversationResponse.body.data._id;

    const voiceMessageResponse = await client
      .post(`/api/v1/chats/conversations/${conversationId}/messages/media`)
      .set(authHeader(firstToken))
      .field("messageType", "voice")
      .attach("media", mp4LikeBuffer, {
        filename: "voice-note.m4a",
        contentType: "audio/mp4",
      });

    expect(voiceMessageResponse.statusCode).toBe(201);
    expect(voiceMessageResponse.body.data.messageType).toBe("voice");
    expect(voiceMessageResponse.body.data.media.type).toBe("voice");
    expect(voiceMessageResponse.body.data.media.mimeType).toBe("audio/mp4");
    expect(voiceMessageResponse.body.data.media.fileName).toBe("voice-note.m4a");
    expect(voiceMessageResponse.body.data.media.durationSeconds).toBe(15);

    const fileMessageResponse = await client
      .post(`/api/v1/chats/conversations/${conversationId}/messages/media`)
      .set(authHeader(firstToken))
      .field("messageType", "file")
      .attach("media", pdfLikeBuffer, {
        filename: "notes.pdf",
        contentType: "application/pdf",
      });

    expect(fileMessageResponse.statusCode).toBe(201);
    expect(fileMessageResponse.body.data.messageType).toBe("file");
    expect(fileMessageResponse.body.data.media.type).toBe("file");
    expect(fileMessageResponse.body.data.media.mimeType).toBe("application/pdf");
    expect(fileMessageResponse.body.data.media.fileName).toBe("notes.pdf");

    const shareMessageResponse = await client
      .post(`/api/v1/chats/conversations/${conversationId}/messages`)
      .set(authHeader(firstToken))
      .send({
        text: `[[stugram-share:${structuredPostShare}]]`,
      });

    expect(shareMessageResponse.statusCode).toBe(201);
    expect(shareMessageResponse.body.data.messageType).toBe("text");
    expect(shareMessageResponse.body.data.metadata).toEqual({
      kind: "post_share",
      payload: {
        title: "Campus sunset",
        subtitle: "@creator",
        tertiary: null,
        imageUrl: null,
        targetId: "post_123",
      },
    });
  });

  it("resolves concurrent direct sends with the same clientId to one stored message", async () => {
    const infoSpy = jest.spyOn(logger, "info");
    const client = getClient();
    const { user: firstUser, accessToken: firstToken } = await createAuthenticatedUser({
      identity: "direct-idempotent-one@example.com",
      username: "direct_idempotent_one",
    });
    const { user: secondUser } = await createAuthenticatedUser({
      identity: "direct-idempotent-two@example.com",
      username: "direct_idempotent_two",
    });

    const conversationResponse = await client
      .post("/api/v1/chats/conversations")
      .set(authHeader(firstToken))
      .send({ participantId: secondUser._id.toString() });

    expect(conversationResponse.statusCode).toBe(201);
    const conversationId = conversationResponse.body.data._id;
    const clientId = "direct-idempotency-race-001";
    const payload = {
      text: "Only one direct row should exist",
      clientId,
    };

    await Message.syncIndexes();

    const [firstResponse, secondResponse] = await Promise.all([
      client
        .post(`/api/v1/chats/conversations/${conversationId}/messages`)
        .set(authHeader(firstToken))
        .send(payload),
      client
        .post(`/api/v1/chats/conversations/${conversationId}/messages`)
        .set(authHeader(firstToken))
        .send(payload),
    ]);

    expect(firstResponse.statusCode).toBe(201);
    expect(secondResponse.statusCode).toBe(201);
    expect(firstResponse.body.success).toBe(true);
    expect(secondResponse.body.success).toBe(true);
    expect(firstResponse.body.data._id.toString()).toBe(secondResponse.body.data._id.toString());
    expect(firstResponse.body.data.clientId).toBe(clientId);
    expect(secondResponse.body.data.clientId).toBe(clientId);

    const storedMessages = await Message.find({
      conversation: conversationId,
      sender: firstUser._id,
      clientId,
    });
    expect(storedMessages).toHaveLength(1);
    expect(infoSpy).toHaveBeenCalledWith(
      "message_duplicate_clientid_resolved",
      expect.objectContaining({
        clientId,
        targetType: "direct",
      })
    );
  });

  it("survives direct retry storm and replay gap without duplicate rows", async () => {
    const client = getClient();
    const { user: firstUser, accessToken: firstToken } = await createAuthenticatedUser({
      identity: "direct-chaos-one@example.com",
      username: "direct_chaos_one",
    });
    const { user: secondUser } = await createAuthenticatedUser({
      identity: "direct-chaos-two@example.com",
      username: "direct_chaos_two",
    });

    const conversationResponse = await client
      .post("/api/v1/chats/conversations")
      .set(authHeader(firstToken))
      .send({ participantId: secondUser._id.toString() });

    const conversationId = conversationResponse.body.data._id;
    const clientId = "direct-retry-storm-001";

    const responses = await Promise.all(
      Array.from({ length: 5 }, () =>
        client
          .post(`/api/v1/chats/conversations/${conversationId}/messages`)
          .set(authHeader(firstToken))
          .send({ text: "storm", clientId })
      )
    );

    responses.forEach((response) => {
      expect(response.statusCode).toBe(201);
      expect(response.body.data.clientId).toBe(clientId);
    });

    const createdId = responses[0].body.data._id.toString();

    const editResponse = await client
      .patch(`/api/v1/chats/messages/${createdId}`)
      .set(authHeader(firstToken))
      .send({ text: "storm-edited" });
    expect(editResponse.statusCode).toBe(200);

    const reactionResponse = await client
      .patch(`/api/v1/chats/messages/${createdId}/reaction`)
      .set(authHeader(firstToken))
      .send({ emoji: "🔥" });
    expect(reactionResponse.statusCode).toBe(200);

    const deleteResponse = await client
      .delete(`/api/v1/chats/messages/${createdId}`)
      .set(authHeader(firstToken))
      .send({ scope: "everyone" });
    expect(deleteResponse.statusCode).toBe(200);

    const replayResponse = await client
      .get("/api/v1/chats/events")
      .set(authHeader(firstToken))
      .query({ conversationId, after: 0, limit: 20 });

    expect(replayResponse.statusCode).toBe(200);
    const replay = replayResponse.body.data
    expect(replay.events.length).toBeGreaterThanOrEqual(4);
    expect(replay.events.map((event) => event.sequence)).toEqual(
      [...replay.events.map((event) => event.sequence)].sort((a, b) => a - b)
    );

    const storedMessages = await Message.find({
      conversation: conversationId,
      sender: firstUser._id,
      clientId,
    });
    expect(storedMessages).toHaveLength(1);
  });

  it("blocks existing direct conversation access after a user is blocked", async () => {
    const client = getClient();
    const { user: firstUser, accessToken: firstToken } = await createAuthenticatedUser({
      identity: "direct-block-owner@example.com",
      username: "direct_block_owner",
    });
    const { user: secondUser, accessToken: secondToken } = await createAuthenticatedUser({
      identity: "direct-block-target@example.com",
      username: "direct_block_target",
    });

    const conversationResponse = await client
      .post("/api/v1/chats/conversations")
      .set(authHeader(firstToken))
      .send({ participantId: secondUser._id.toString() });
    expect(conversationResponse.statusCode).toBe(201);

    const conversationId = conversationResponse.body.data._id;

    const blockResponse = await client
      .post(`/api/v1/chats/users/${secondUser._id}/block`)
      .set(authHeader(firstToken));
    expect(blockResponse.statusCode).toBe(201);

    const fetchConversationResponse = await client
      .get(`/api/v1/chats/conversations/${conversationId}`)
      .set(authHeader(firstToken));
    expect(fetchConversationResponse.statusCode).toBe(403);

    const searchConversationsResponse = await client
      .get("/api/v1/chats/conversations/search")
      .set(authHeader(firstToken))
      .query({ q: secondUser.username });
    expect(searchConversationsResponse.statusCode).toBe(200);
    expect(searchConversationsResponse.body.data).toHaveLength(0);

    const otherSideFetchResponse = await client
      .get(`/api/v1/chats/conversations/${conversationId}`)
      .set(authHeader(secondToken));
    expect(otherSideFetchResponse.statusCode).toBe(403);
  });

  it("supports group chat create, messaging, member management, and access control", async () => {
    const client = getClient();
    const { user: owner, accessToken: ownerToken } = await createAuthenticatedUser({
      identity: "group-owner@example.com",
      username: "group_owner",
    });
    const { user: memberOne } = await createAuthenticatedUser({
      identity: "group-member-one@example.com",
      username: "group_member_one",
    });
    const { user: memberTwo, accessToken: memberTwoToken } = await createAuthenticatedUser({
      identity: "group-member-two@example.com",
      username: "group_member_two",
    });
    const { user: memberThree, accessToken: memberThreeToken } = await createAuthenticatedUser({
      identity: "group-member-three@example.com",
      username: "group_member_three",
    });

    const createGroupResponse = await client
      .post("/api/v1/group-chats")
      .set(authHeader(ownerToken))
      .field("name", "Core Team")
      .field("memberIds", JSON.stringify([memberOne._id.toString()]));

    expect(createGroupResponse.statusCode).toBe(201);
    const groupId = createGroupResponse.body.data._id;

    const sendGroupMessageResponse = await client
      .post(`/api/v1/group-chats/${groupId}/messages`)
      .set(authHeader(ownerToken))
      .send({ text: "Welcome team" });

    expect(sendGroupMessageResponse.statusCode).toBe(201);

    const addMemberResponse = await client
      .post(`/api/v1/group-chats/${groupId}/members`)
      .set(authHeader(ownerToken))
      .send({ memberIds: [memberTwo._id.toString()] });

    expect(addMemberResponse.statusCode).toBe(200);
    expect(addMemberResponse.body.data.membersCount).toBe(3);

    const updateGroupResponse = await client
      .patch(`/api/v1/group-chats/${groupId}`)
      .set(authHeader(ownerToken))
      .field("name", "Core Team Updated");

    expect(updateGroupResponse.statusCode).toBe(200);
    expect(updateGroupResponse.body.data.group.name).toBe("Core Team Updated");

    const groupMembersResponse = await client
      .get(`/api/v1/group-chats/${groupId}/members`)
      .set(authHeader(ownerToken));

    expect(groupMembersResponse.statusCode).toBe(200);
    expect(groupMembersResponse.body.data).toHaveLength(3);

    const addThirdMemberResponse = await client
      .post(`/api/v1/group-chats/${groupId}/members`)
      .set(authHeader(ownerToken))
      .send({ memberIds: [memberThree._id.toString()] });

    expect(addThirdMemberResponse.statusCode).toBe(200);

    const leaveGroupResponse = await client
      .post(`/api/v1/group-chats/${groupId}/leave`)
      .set(authHeader(memberThreeToken));

    expect(leaveGroupResponse.statusCode).toBe(200);
    expect(leaveGroupResponse.body.data.left).toBe(true);

    const removeMemberResponse = await client
      .delete(`/api/v1/group-chats/${groupId}/members/${memberTwo._id}`)
      .set(authHeader(ownerToken));

    expect(removeMemberResponse.statusCode).toBe(200);

    const removedMemberAccess = await client
      .get(`/api/v1/group-chats/${groupId}/messages`)
      .set(authHeader(memberTwoToken));

    const leftMemberAccess = await client
      .get(`/api/v1/group-chats/${groupId}/messages`)
      .set(authHeader(memberThreeToken));

    expect(removedMemberAccess.statusCode).toBe(403);
    expect(leftMemberAccess.statusCode).toBe(403);
    expect(removeMemberResponse.body.data.removed).toBe(true);
    expect(removeMemberResponse.body.data.userId.toString()).toBe(memberTwo._id.toString());
    expect(owner._id).toBeTruthy();
  });

  it("supports group round-video and file uploads", async () => {
    const client = getClient();
    const { user: owner, accessToken: ownerToken } = await createAuthenticatedUser({
      identity: "group-media-owner@example.com",
      username: "group_media_owner",
    });
    const { user: memberOne } = await createAuthenticatedUser({
      identity: "group-media-member@example.com",
      username: "group_media_member",
    });

    const createGroupResponse = await client
      .post("/api/v1/group-chats")
      .set(authHeader(ownerToken))
      .field("name", "Media Team")
      .field("memberIds", JSON.stringify([memberOne._id.toString()]));

    expect(createGroupResponse.statusCode).toBe(201);
    const groupId = createGroupResponse.body.data._id;

    const roundVideoResponse = await client
      .post(`/api/v1/group-chats/${groupId}/messages`)
      .set(authHeader(ownerToken))
      .field("messageType", "round_video")
      .attach("media", mp4LikeBuffer, {
        filename: "video-note.mp4",
        contentType: "video/mp4",
      });

    expect(roundVideoResponse.statusCode).toBe(201);
    expect(roundVideoResponse.body.data.messageType).toBe("round_video");
    expect(roundVideoResponse.body.data.media.type).toBe("round_video");
    expect(roundVideoResponse.body.data.media.mimeType).toBe("video/mp4");
    expect(roundVideoResponse.body.data.media.durationSeconds).toBe(15);

    const fileMessageResponse = await client
      .post(`/api/v1/group-chats/${groupId}/messages`)
      .set(authHeader(ownerToken))
      .field("messageType", "file")
      .attach("media", pdfLikeBuffer, {
        filename: "brief.pdf",
        contentType: "application/pdf",
      });

    expect(fileMessageResponse.statusCode).toBe(201);
    expect(fileMessageResponse.body.data.messageType).toBe("file");
    expect(fileMessageResponse.body.data.media.type).toBe("file");
    expect(fileMessageResponse.body.data.media.fileName).toBe("brief.pdf");
  });

  it("resolves concurrent group sends with the same clientId to one stored message", async () => {
    const client = getClient();
    const { user: owner, accessToken: ownerToken } = await createAuthenticatedUser({
      identity: "group-idempotent-owner@example.com",
      username: "group_idempotent_owner",
    });
    const { user: memberOne } = await createAuthenticatedUser({
      identity: "group-idempotent-member@example.com",
      username: "group_idempotent_member",
    });

    const createGroupResponse = await client
      .post("/api/v1/group-chats")
      .set(authHeader(ownerToken))
      .field("name", "Idempotent Group")
      .field("memberIds", JSON.stringify([memberOne._id.toString()]));

    expect(createGroupResponse.statusCode).toBe(201);
    const groupId = createGroupResponse.body.data._id;
    const clientId = "group-idempotency-race-001";
    const payload = {
      text: "Only one group row should exist",
      clientId,
    };

    await GroupMessage.syncIndexes();

    const [firstResponse, secondResponse] = await Promise.all([
      client
        .post(`/api/v1/group-chats/${groupId}/messages`)
        .set(authHeader(ownerToken))
        .send(payload),
      client
        .post(`/api/v1/group-chats/${groupId}/messages`)
        .set(authHeader(ownerToken))
        .send(payload),
    ]);

    expect(firstResponse.statusCode).toBe(201);
    expect(secondResponse.statusCode).toBe(201);
    expect(firstResponse.body.success).toBe(true);
    expect(secondResponse.body.success).toBe(true);
    expect(firstResponse.body.data._id.toString()).toBe(secondResponse.body.data._id.toString());
    expect(firstResponse.body.data.clientId).toBe(clientId);
    expect(secondResponse.body.data.clientId).toBe(clientId);

    const storedMessages = await GroupMessage.find({
      groupConversation: groupId,
      sender: owner._id,
      clientId,
    });
    expect(storedMessages).toHaveLength(1);
  });

  it("survives group retry storm and removed-member send denial safely", async () => {
    const client = getClient();
    const { user: owner, accessToken: ownerToken } = await createAuthenticatedUser({
      identity: "group-chaos-owner@example.com",
      username: "group_chaos_owner",
    });
    const { user: member, accessToken: memberToken } = await createAuthenticatedUser({
      identity: "group-chaos-member@example.com",
      username: "group_chaos_member",
    });

    const createGroupResponse = await client
      .post("/api/v1/group-chats")
      .set(authHeader(ownerToken))
      .field("name", "Chaos Group")
      .field("memberIds", JSON.stringify([member._id.toString()]));

    const groupId = createGroupResponse.body.data._id;
    const clientId = "group-retry-storm-001";

    const responses = await Promise.all(
      Array.from({ length: 5 }, () =>
        client
          .post(`/api/v1/group-chats/${groupId}/messages`)
          .set(authHeader(ownerToken))
          .send({ text: "group-storm", clientId })
      )
    );

    responses.forEach((response) => {
      expect(response.statusCode).toBe(201);
      expect(response.body.data.clientId).toBe(clientId);
    });

    const storedMessages = await GroupMessage.find({
      groupConversation: groupId,
      sender: owner._id,
      clientId,
    });
    expect(storedMessages).toHaveLength(1);

    const removeMemberResponse = await client
      .delete(`/api/v1/group-chats/${groupId}/members/${member._id}`)
      .set(authHeader(ownerToken));
    expect(removeMemberResponse.statusCode).toBe(200);

    const deniedSend = await client
      .post(`/api/v1/group-chats/${groupId}/messages`)
      .set(authHeader(memberToken))
      .send({ text: "should fail after removal" });

    expect(deniedSend.statusCode).toBe(403);

    const replayResponse = await client
      .get("/api/v1/group-chats/events")
      .set(authHeader(ownerToken))
      .query({ groupId, after: 0, limit: 20 });

    expect(replayResponse.statusCode).toBe(200);
    expect(replayResponse.body.data.events.map((event) => event.sequence)).toEqual(
      [...replayResponse.body.data.events.map((event) => event.sequence)].sort((a, b) => a - b)
    );
  });
});
