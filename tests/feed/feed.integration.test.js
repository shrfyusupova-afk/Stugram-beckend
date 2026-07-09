const { setupIntegrationTestSuite } = require("../helpers/integration");
const { authHeader, createAuthenticatedUser, createPost, createFollow } = require("../helpers/factories");

const { getClient } = setupIntegrationTestSuite();

describe("Feed integration", () => {
  it("returns only real feed items and supports pagination", async () => {
    const client = getClient();
    const { user: authorA } = await createAuthenticatedUser({
      identity: "feed-author-a@example.com",
      username: "feed_author_a",
    });
    const { user: authorB } = await createAuthenticatedUser({
      identity: "feed-author-b@example.com",
      username: "feed_author_b",
    });
    const { user: viewer, accessToken } = await createAuthenticatedUser({
      identity: "feed-viewer@example.com",
      username: "feed_viewer",
    });

    await createFollow({ followerId: viewer._id, followingId: authorA._id });
    await createFollow({ followerId: viewer._id, followingId: authorB._id });

    await createPost({ authorId: authorA._id, caption: "A1" });
    await createPost({ authorId: authorA._id, caption: "A2" });
    await createPost({ authorId: authorB._id, caption: "B1" });

    const response = await client
      .get("/api/v1/posts/feed/me?page=1&limit=2")
      .set(authHeader(accessToken));

    expect(response.statusCode).toBe(200);
    expect(response.body.meta.requestId).toBeTruthy();
    expect(Array.isArray(response.body.data)).toBe(true);
    expect(response.body.data.length).toBe(2);
    expect(response.body.meta.total).toBeGreaterThanOrEqual(3);
  });
});

