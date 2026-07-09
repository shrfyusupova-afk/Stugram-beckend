const { setupIntegrationTestSuite } = require("../helpers/integration");
const { authHeader, createAuthenticatedUser, getModels } = require("../helpers/factories");

const { getClient } = setupIntegrationTestSuite();

describe("Requests integration", () => {
  it("returns only current user's pending requests", async () => {
    const client = getClient();
    const { FollowRequest } = getModels();
    const { user: recipient, accessToken } = await createAuthenticatedUser({ username: "recipient_requests_1" });
    const { user: requesterA } = await createAuthenticatedUser({ username: "requester_requests_a" });
    const { user: requesterB } = await createAuthenticatedUser({ username: "requester_requests_b" });
    const { user: outsider } = await createAuthenticatedUser({ username: "outsider_requests_1" });

    await FollowRequest.create({ requester: requesterA._id, recipient: recipient._id, status: "pending" });
    await FollowRequest.create({ requester: requesterB._id, recipient: recipient._id, status: "pending" });
    await FollowRequest.create({ requester: recipient._id, recipient: outsider._id, status: "pending" });

    const response = await client.get("/api/v1/requests").set(authHeader(accessToken));
    expect(response.statusCode).toBe(200);
    expect(Array.isArray(response.body.data.requests)).toBe(true);
    expect(response.body.data.requests).toHaveLength(2);
    expect(response.body.meta.requestId).toBeDefined();
  });

  it("add-to-chat creates conversation once and resolves request", async () => {
    const client = getClient();
    const { FollowRequest, Conversation } = getModels();
    const { user: recipient, accessToken } = await createAuthenticatedUser({ username: "recipient_requests_2" });
    const { user: requester } = await createAuthenticatedUser({ username: "requester_requests_c" });

    const request = await FollowRequest.create({ requester: requester._id, recipient: recipient._id, status: "pending" });

    const response = await client
      .post(`/api/v1/requests/${request.id}/add-to-chat`)
      .set(authHeader(accessToken))
      .send({});

    expect(response.statusCode).toBe(200);
    expect(response.body.data.conversation).toBeDefined();

    const updatedRequest = await FollowRequest.findById(request.id).lean();
    expect(updatedRequest.status).toBe("cancelled");

    const conversations = await Conversation.find({
      participants: { $all: [recipient._id, requester._id] },
    }).lean();
    expect(conversations).toHaveLength(1);
  });

  it("block and delete request resolve pending request", async () => {
    const client = getClient();
    const { FollowRequest, Block } = getModels();
    const { user: recipient, accessToken } = await createAuthenticatedUser({ username: "recipient_requests_3" });
    const { user: requesterOne } = await createAuthenticatedUser({ username: "requester_requests_d" });
    const { user: requesterTwo } = await createAuthenticatedUser({ username: "requester_requests_e" });

    const blockRequest = await FollowRequest.create({ requester: requesterOne._id, recipient: recipient._id, status: "pending" });
    const deleteRequest = await FollowRequest.create({ requester: requesterTwo._id, recipient: recipient._id, status: "pending" });

    const blockResponse = await client
      .post(`/api/v1/requests/${blockRequest.id}/block`)
      .set(authHeader(accessToken))
      .send({});
    expect(blockResponse.statusCode).toBe(200);

    const blockRecord = await Block.findOne({ blocker: recipient._id, blocked: requesterOne._id }).lean();
    expect(blockRecord).toBeTruthy();

    const deleteResponse = await client
      .delete(`/api/v1/requests/${deleteRequest.id}`)
      .set(authHeader(accessToken));
    expect(deleteResponse.statusCode).toBe(200);

    const updatedDeleteRequest = await FollowRequest.findById(deleteRequest.id).lean();
    expect(updatedDeleteRequest.status).toBe("rejected");
  });
});
