const { setupIntegrationTestSuite } = require("../helpers/integration");
const {
  authHeader,
  createAuthenticatedUser,
  createConversation,
} = require("../helpers/factories");

const callService = require("../../src/services/callService");

const { getClient } = setupIntegrationTestSuite();

describe("Calls integration", () => {
  it("returns call history and protects call detail access", async () => {
    const client = getClient();
    const { user: caller, accessToken: callerToken } = await createAuthenticatedUser({
      identity: "caller@example.com",
      username: "caller_user",
    });
    const { user: receiver, accessToken: receiverToken } = await createAuthenticatedUser({
      identity: "receiver@example.com",
      username: "receiver_user",
    });
    const { accessToken: strangerToken } = await createAuthenticatedUser({
      identity: "stranger@example.com",
      username: "stranger_user",
    });

    const conversation = await createConversation({
      participants: [caller._id, receiver._id],
      createdBy: caller._id,
    });

    const startedCall = await callService.startDirectCall(caller._id, {
      conversationId: conversation._id,
      callType: "video",
    });

    await callService.answerCall(receiver._id, startedCall.session._id);
    await callService.endCall(caller._id, startedCall.session._id);

    const historyResponse = await client
      .get("/api/v1/calls/history")
      .set(authHeader(callerToken));

    expect(historyResponse.statusCode).toBe(200);
    expect(historyResponse.body.data).toHaveLength(1);
    expect(historyResponse.body.data[0].status).toBe("ended");

    const detailResponse = await client
      .get(`/api/v1/calls/${startedCall.session._id}`)
      .set(authHeader(receiverToken));

    expect(detailResponse.statusCode).toBe(200);
    expect(detailResponse.body.data.callType).toBe("video");

    const forbiddenResponse = await client
      .get(`/api/v1/calls/${startedCall.session._id}`)
      .set(authHeader(strangerToken));

    expect(forbiddenResponse.statusCode).toBe(403);
  });
});
