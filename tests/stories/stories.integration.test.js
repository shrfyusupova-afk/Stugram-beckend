const { setupIntegrationTestSuite } = require("../helpers/integration");
const {
  authHeader,
  createAuthenticatedUser,
  fakeImageBuffer,
  getModels,
} = require("../helpers/factories");

const { getClient } = setupIntegrationTestSuite();

describe("Stories integration", () => {
  it("creates a story and supports view, like, comment, viewers, and insights permissions", async () => {
    const client = getClient();
    const { accessToken: ownerToken } = await createAuthenticatedUser({
      identity: "story-owner@example.com",
      username: "story_owner",
    });
    const { accessToken: viewerToken } = await createAuthenticatedUser({
      identity: "story-viewer@example.com",
      username: "story_viewer",
    });

    const createResponse = await client
      .post("/api/v1/stories")
      .set(authHeader(ownerToken))
      .field("caption", "Story launch")
      .attach("media", fakeImageBuffer(), {
        filename: "story.jpg",
        contentType: "image/jpeg",
      });

    expect(createResponse.statusCode).toBe(201);
    const storyId = createResponse.body.data._id;

    const viewResponse = await client
      .post(`/api/v1/stories/${storyId}/view`)
      .set(authHeader(viewerToken));
    expect(viewResponse.statusCode).toBe(200);

    const likeResponse = await client
      .post(`/api/v1/stories/${storyId}/like`)
      .set(authHeader(viewerToken));
    expect(likeResponse.statusCode).toBe(200);

    const commentResponse = await client
      .post(`/api/v1/stories/${storyId}/comments`)
      .set(authHeader(viewerToken))
      .send({ content: "Nice story" });
    expect(commentResponse.statusCode).toBe(201);

    const viewersForbidden = await client
      .get(`/api/v1/stories/${storyId}/viewers`)
      .set(authHeader(viewerToken));
    expect(viewersForbidden.statusCode).toBe(403);

    const viewersResponse = await client
      .get(`/api/v1/stories/${storyId}/viewers`)
      .set(authHeader(ownerToken));
    expect(viewersResponse.statusCode).toBe(200);
    expect(viewersResponse.body.data).toHaveLength(1);

    const insightsForbidden = await client
      .get(`/api/v1/stories/${storyId}/insights`)
      .set(authHeader(viewerToken));
    expect(insightsForbidden.statusCode).toBe(403);

    const insightsResponse = await client
      .get(`/api/v1/stories/${storyId}/insights`)
      .set(authHeader(ownerToken));
    expect(insightsResponse.statusCode).toBe(200);
    expect(insightsResponse.body.data.totalViews).toBe(1);
    expect(insightsResponse.body.data.totalLikes).toBe(1);
    expect(insightsResponse.body.data.totalComments).toBe(1);
  });

  it("supports story reply and comment deletion by story owner", async () => {
    const client = getClient();
    const { user: owner, accessToken: ownerToken } = await createAuthenticatedUser({
      identity: "story-reply-owner@example.com",
      username: "story_reply_owner",
    });
    const { accessToken: viewerToken } = await createAuthenticatedUser({
      identity: "story-reply-viewer@example.com",
      username: "story_reply_viewer",
    });

    const createResponse = await client
      .post("/api/v1/stories")
      .set(authHeader(ownerToken))
      .field("caption", "Reply story")
      .attach("media", fakeImageBuffer(), {
        filename: "story.jpg",
        contentType: "image/jpeg",
      });

    const storyId = createResponse.body.data._id;

    const commentResponse = await client
      .post(`/api/v1/stories/${storyId}/comments`)
      .set(authHeader(viewerToken))
      .send({ content: "Comment to remove" });

    const deleteCommentResponse = await client
      .delete(`/api/v1/stories/${storyId}/comments/${commentResponse.body.data._id}`)
      .set(authHeader(ownerToken));

    expect(deleteCommentResponse.statusCode).toBe(200);
    expect(deleteCommentResponse.body.data.deleted).toBe(true);

    const replyResponse = await client
      .post(`/api/v1/stories/${storyId}/reply`)
      .set(authHeader(viewerToken))
      .send({ text: "Direct reply" });

    expect(replyResponse.statusCode).toBe(201);
    expect(replyResponse.body.data.conversationId).toBeTruthy();
    expect(replyResponse.body.data.message.text).toBe("Direct reply");

    const { Conversation } = getModels();
    const conversation = await Conversation.findById(replyResponse.body.data.conversationId);
    expect(conversation.participants.map(String)).toEqual(
      expect.arrayContaining([owner._id.toString()])
    );
  });
});
