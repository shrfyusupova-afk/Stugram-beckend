const { setupIntegrationTestSuite } = require("../helpers/integration");
const { createSocketTestHarness } = require("../helpers/socketIntegration");
const {
  authHeader,
  createAuthenticatedUser,
  createConversation,
  createGroupConversation,
} = require("../helpers/factories");

const { denylistToken } = require("../../src/services/redisSecurityService");
const { verifyAccessToken } = require("../../src/utils/token");

const { getApp, getClient } = setupIntegrationTestSuite();

describe("Realtime socket integration", () => {
  let socketHarness;

  beforeAll(async () => {
    socketHarness = await createSocketTestHarness(getApp());
  });

  afterEach(async () => {
    await socketHarness.closeAllSockets();
  });

  afterAll(async () => {
    await socketHarness.close();
  });

  it("supports direct chat realtime events", async () => {
    const client = getClient();
    const { user: firstUser, accessToken: firstToken } = await createAuthenticatedUser({
      identity: "rt-direct-1@example.com",
      username: "rt_direct_1",
    });
    const { user: secondUser, accessToken: secondToken } = await createAuthenticatedUser({
      identity: "rt-direct-2@example.com",
      username: "rt_direct_2",
    });

    const conversation = await createConversation({
      participants: [firstUser._id, secondUser._id],
      createdBy: firstUser._id,
    });

    const firstSocket = await socketHarness.connectSocket({ token: firstToken });
    const secondSocket = await socketHarness.connectSocket({ token: secondToken });

    const firstJoinAck = await socketHarness.emitWithAck(firstSocket, "conversation:join", {
      conversationId: conversation._id.toString(),
    });
    const secondJoinAck = await socketHarness.emitWithAck(secondSocket, "conversation:join", {
      conversationId: conversation._id.toString(),
    });

    expect(firstJoinAck.ok).toBe(true);
    expect(secondJoinAck.ok).toBe(true);

    const newMessagePromise = socketHarness.waitForEvent(secondSocket, "new_message");
    const sendMessageResponse = await client
      .post(`/api/v1/chats/conversations/${conversation._id}/messages`)
      .set(authHeader(firstToken))
      .send({ text: "Realtime hello" });

    expect(sendMessageResponse.statusCode).toBe(201);
    const newMessagePayload = await newMessagePromise;
    expect(newMessagePayload.text).toBe("Realtime hello");
    expect(newMessagePayload.conversation.toString()).toBe(conversation._id.toString());

    const typingStartPromise = socketHarness.waitForEvent(secondSocket, "typing_start");
    const typingStartAck = await socketHarness.emitWithAck(firstSocket, "typing_start", {
      conversationId: conversation._id.toString(),
    });
    expect(typingStartAck.ok).toBe(true);
    const typingStartPayload = await typingStartPromise;
    expect(typingStartPayload.type).toBe("direct");
    expect(typingStartPayload.conversationId.toString()).toBe(conversation._id.toString());

    const typingStopPromise = socketHarness.waitForEvent(secondSocket, "typing_stop");
    const typingStopAck = await socketHarness.emitWithAck(firstSocket, "typing_stop", {
      conversationId: conversation._id.toString(),
    });
    expect(typingStopAck.ok).toBe(true);
    const typingStopPayload = await typingStopPromise;
    expect(typingStopPayload.type).toBe("direct");
    expect(typingStopPayload.userId.toString()).toBe(firstUser._id.toString());

    const seenPromise = socketHarness.waitForEvent(firstSocket, "message_seen");
    const seenResponse = await client
      .patch(`/api/v1/chats/messages/${sendMessageResponse.body.data._id}/seen`)
      .set(authHeader(secondToken));

    expect(seenResponse.statusCode).toBe(200);
    const seenPayload = await seenPromise;
    expect(seenPayload.seenByUserId.toString()).toBe(secondUser._id.toString());
    expect(seenPayload.message._id.toString()).toBe(sendMessageResponse.body.data._id.toString());

    const reactionPromise = socketHarness.waitForEvent(secondSocket, "message_reaction_updated");
    const reactionResponse = await client
      .patch(`/api/v1/chats/messages/${sendMessageResponse.body.data._id}/reaction`)
      .set(authHeader(firstToken))
      .send({ emoji: "🔥" });

    expect(reactionResponse.statusCode).toBe(200);
    const reactionPayload = await reactionPromise;
    expect(reactionPayload.conversationId.toString()).toBe(conversation._id.toString());
    expect(reactionPayload.message.reactions[0].emoji).toBe("🔥");
  });

  it("supports group chat realtime events and membership protections", async () => {
    const client = getClient();
    const { user: owner, accessToken: ownerToken } = await createAuthenticatedUser({
      identity: "rt-group-owner@example.com",
      username: "rt_group_owner",
    });
    const { user: member, accessToken: memberToken } = await createAuthenticatedUser({
      identity: "rt-group-member@example.com",
      username: "rt_group_member",
    });
    const { user: outsider, accessToken: outsiderToken } = await createAuthenticatedUser({
      identity: "rt-group-outsider@example.com",
      username: "rt_group_outsider",
    });
    const { user: newMember, accessToken: newMemberToken } = await createAuthenticatedUser({
      identity: "rt-group-new@example.com",
      username: "rt_group_new",
    });

    const group = await createGroupConversation({
      ownerId: owner._id,
      memberIds: [member._id],
      name: "Realtime Group",
    });

    const ownerSocket = await socketHarness.connectSocket({ token: ownerToken });
    const memberSocket = await socketHarness.connectSocket({ token: memberToken });
    const outsiderSocket = await socketHarness.connectSocket({ token: outsiderToken });
    const newMemberSocket = await socketHarness.connectSocket({ token: newMemberToken });

    const ownerJoinAck = await socketHarness.emitWithAck(ownerSocket, "group_chat:join", {
      groupId: group._id.toString(),
    });
    const memberJoinAck = await socketHarness.emitWithAck(memberSocket, "group_chat:join", {
      groupId: group._id.toString(),
    });
    const outsiderJoinAck = await socketHarness.emitWithAck(outsiderSocket, "group_chat:join", {
      groupId: group._id.toString(),
    });

    expect(ownerJoinAck.ok).toBe(true);
    expect(memberJoinAck.ok).toBe(true);
    expect(outsiderJoinAck.ok).toBe(false);

    const groupMessagePromise = socketHarness.waitForEvent(memberSocket, "group_message");
    const groupMessageResponse = await client
      .post(`/api/v1/group-chats/${group._id}/messages`)
      .set(authHeader(ownerToken))
      .send({ text: "Hello group realtime" });

    expect(groupMessageResponse.statusCode).toBe(201);
    const groupMessagePayload = await groupMessagePromise;
    expect(groupMessagePayload.groupId.toString()).toBe(group._id.toString());
    expect(groupMessagePayload.message.text).toBe("Hello group realtime");

    const groupTypingPromise = socketHarness.waitForEvent(memberSocket, "typing_start");
    const groupTypingAck = await socketHarness.emitWithAck(ownerSocket, "typing_start", {
      groupId: group._id.toString(),
    });
    expect(groupTypingAck.ok).toBe(true);
    const groupTypingPayload = await groupTypingPromise;
    expect(groupTypingPayload.type).toBe("group");
    expect(groupTypingPayload.groupId.toString()).toBe(group._id.toString());

    const groupSeenPromise = socketHarness.waitForEvent(ownerSocket, "group_message_seen");
    const groupSeenResponse = await client
      .patch(`/api/v1/group-chats/${group._id}/messages/${groupMessageResponse.body.data._id}/seen`)
      .set(authHeader(memberToken));

    expect(groupSeenResponse.statusCode).toBe(200);
    const groupSeenPayload = await groupSeenPromise;
    expect(groupSeenPayload.seenByUserId.toString()).toBe(member._id.toString());
    expect(groupSeenPayload.groupId.toString()).toBe(group._id.toString());

    const memberAddedPromise = socketHarness.waitForEvent(newMemberSocket, "group_member_added");
    const addMemberResponse = await client
      .post(`/api/v1/group-chats/${group._id}/members`)
      .set(authHeader(ownerToken))
      .send({ memberIds: [newMember._id.toString()] });

    expect(addMemberResponse.statusCode).toBe(200);
    const memberAddedPayload = await memberAddedPromise;
    expect(memberAddedPayload.groupId.toString()).toBe(group._id.toString());

    const memberRemovedPromise = socketHarness.waitForEvent(memberSocket, "group_member_removed");
    const removeMemberResponse = await client
      .delete(`/api/v1/group-chats/${group._id}/members/${member._id}`)
      .set(authHeader(ownerToken));

    expect(removeMemberResponse.statusCode).toBe(200);
    const memberRemovedPayload = await memberRemovedPromise;
    expect(memberRemovedPayload.groupId.toString()).toBe(group._id.toString());
    expect(memberRemovedPayload.userId.toString()).toBe(member._id.toString());

    const removedJoinAck = await socketHarness.emitWithAck(memberSocket, "group_chat:join", {
      groupId: group._id.toString(),
    });
    expect(removedJoinAck.ok).toBe(false);
  });

  it("supports call signaling events and rejects invalid participants", async () => {
    const { user: caller, accessToken: callerToken } = await createAuthenticatedUser({
      identity: "rt-call-caller@example.com",
      username: "rt_call_caller",
    });
    const { user: callee, accessToken: calleeToken } = await createAuthenticatedUser({
      identity: "rt-call-callee@example.com",
      username: "rt_call_callee",
    });
    const { user: outsider, accessToken: outsiderToken } = await createAuthenticatedUser({
      identity: "rt-call-outsider@example.com",
      username: "rt_call_outsider",
    });

    const conversation = await createConversation({
      participants: [caller._id, callee._id],
      createdBy: caller._id,
    });

    const callerSocket = await socketHarness.connectSocket({ token: callerToken });
    const calleeSocket = await socketHarness.connectSocket({ token: calleeToken });
    const outsiderSocket = await socketHarness.connectSocket({ token: outsiderToken });

    const offerPromise = socketHarness.waitForEvent(calleeSocket, "call_offer");
    const offerAck = await socketHarness.emitWithAck(callerSocket, "call_offer", {
      conversationId: conversation._id.toString(),
      callType: "audio",
      offer: { type: "offer", sdp: "caller-offer" },
    });

    expect(offerAck.ok).toBe(true);
    const offerPayload = await offerPromise;
    expect(offerPayload.callType).toBe("audio");
    expect(offerPayload.conversationId.toString()).toBe(conversation._id.toString());

    const callId = offerPayload.callId.toString();

    const invalidOfferAck = await socketHarness.emitWithAck(outsiderSocket, "call_offer", {
      conversationId: conversation._id.toString(),
      callType: "audio",
      offer: { type: "offer", sdp: "outsider-offer" },
    });
    expect(invalidOfferAck.ok).toBe(false);

    const declinePromise = socketHarness.waitForEvent(callerSocket, "call_decline");
    const declineAck = await socketHarness.emitWithAck(calleeSocket, "call_decline", { callId });
    expect(declineAck.ok).toBe(true);
    const declinePayload = await declinePromise;
    expect(declinePayload.callId.toString()).toBe(callId);

    const secondOfferPromise = socketHarness.waitForEvent(calleeSocket, "call_offer");
    const secondOfferAck = await socketHarness.emitWithAck(callerSocket, "call_offer", {
      conversationId: conversation._id.toString(),
      callType: "video",
      offer: { type: "offer", sdp: "caller-offer-2" },
    });
    expect(secondOfferAck.ok).toBe(true);
    const secondOfferPayload = await secondOfferPromise;
    const secondCallId = secondOfferPayload.callId.toString();

    const answerPromise = socketHarness.waitForEvent(callerSocket, "call_answer");
    const answerAck = await socketHarness.emitWithAck(calleeSocket, "call_answer", {
      callId: secondCallId,
      answer: { type: "answer", sdp: "callee-answer" },
    });
    expect(answerAck.ok).toBe(true);
    const answerPayload = await answerPromise;
    expect(answerPayload.callId.toString()).toBe(secondCallId);

    const icePromise = socketHarness.waitForEvent(calleeSocket, "call_ice_candidate");
    const iceAck = await socketHarness.emitWithAck(callerSocket, "call_ice_candidate", {
      callId: secondCallId,
      targetUserId: callee._id.toString(),
      candidate: { candidate: "abc", sdpMid: "0", sdpMLineIndex: 0 },
    });
    expect(iceAck.ok).toBe(true);
    const icePayload = await icePromise;
    expect(icePayload.callId.toString()).toBe(secondCallId);
    expect(icePayload.fromUserId.toString()).toBe(caller._id.toString());

    const invalidIceAck = await socketHarness.emitWithAck(outsiderSocket, "call_ice_candidate", {
      callId: secondCallId,
      targetUserId: caller._id.toString(),
      candidate: { candidate: "bad", sdpMid: "0", sdpMLineIndex: 0 },
    });
    expect(invalidIceAck.ok).toBe(false);

    const endPromise = socketHarness.waitForEvent(calleeSocket, "call_end");
    const endAck = await socketHarness.emitWithAck(callerSocket, "call_end", { callId: secondCallId });
    expect(endAck.ok).toBe(true);
    const endPayload = await endPromise;
    expect(endPayload.callId.toString()).toBe(secondCallId);
  });

  it("rejects invalid and revoked socket authentication", async () => {
    const { user, accessToken } = await createAuthenticatedUser({
      identity: "rt-auth-user@example.com",
      username: "rt_auth_user",
    });

    const invalidConnection = await socketHarness.connectSocketExpectError({ token: "invalid-token" });
    expect(invalidConnection.error.message).toBe("Invalid or expired token");
    invalidConnection.socket.close();

    const payload = verifyAccessToken(accessToken);
    await denylistToken({
      jti: payload.jti,
      tokenType: "access",
      expiresAt: new Date(payload.exp * 1000),
    });

    const revokedConnection = await socketHarness.connectSocketExpectError({ token: accessToken });
    expect(revokedConnection.error.message).toBe("Token has been revoked");
    revokedConnection.socket.close();

    expect(user._id).toBeTruthy();
  });
});
