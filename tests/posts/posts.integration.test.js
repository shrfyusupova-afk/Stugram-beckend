const { setupIntegrationTestSuite } = require("../helpers/integration");
const {
  authHeader,
  createAuthenticatedUser,
  createPost,
  fakeImageBuffer,
  getModels,
} = require("../helpers/factories");

const { getClient } = setupIntegrationTestSuite();

describe("Posts and engagement integration", () => {
  it("creates a post, likes it, comments on it, and deletes the comment", async () => {
    const client = getClient();
    const { accessToken, user } = await createAuthenticatedUser({
      identity: "author@example.com",
      username: "post_author",
    });
    const { accessToken: viewerToken } = await createAuthenticatedUser({
      identity: "viewer@example.com",
      username: "post_viewer",
    });

    const createResponse = await client
      .post("/api/v1/posts")
      .set(authHeader(accessToken))
      .field("caption", "Launch day")
      .attach("media", fakeImageBuffer(), {
        filename: "post.jpg",
        contentType: "image/jpeg",
    });

    expect(createResponse.statusCode).toBe(201);
    expect(createResponse.body.data.author._id.toString()).toBe(user._id.toString());

    const postId = createResponse.body.data._id;

    const likeResponse = await client
      .post(`/api/v1/likes/posts/${postId}`)
      .set(authHeader(viewerToken));
    expect(likeResponse.statusCode).toBe(200);

    const commentResponse = await client
      .post(`/api/v1/comments/posts/${postId}`)
      .set(authHeader(viewerToken))
      .send({ content: "Great launch" });

    expect(commentResponse.statusCode).toBe(201);
    const commentId = commentResponse.body.data._id;

    const deleteCommentResponse = await client
      .delete(`/api/v1/comments/${commentId}`)
      .set(authHeader(viewerToken));

    expect(deleteCommentResponse.statusCode).toBe(200);
    expect(deleteCommentResponse.body.data.deleted).toBe(true);
  });

  it("replays idempotent post comments instead of duplicating them", async () => {
    const client = getClient();
    const { user: author } = await createAuthenticatedUser({
      identity: "idempotent-author@example.com",
      username: "idempotent_author",
    });
    const { accessToken: viewerToken } = await createAuthenticatedUser({
      identity: "idempotent-viewer@example.com",
      username: "idempotent_viewer",
    });

    const post = await createPost({ authorId: author._id, caption: "Idempotent post" });
    const idempotencyKey = "comment-once-key";

    const firstResponse = await client
      .post(`/api/v1/comments/posts/${post._id}`)
      .set(authHeader(viewerToken))
      .set("Idempotency-Key", idempotencyKey)
      .send({ content: "Only once" });

    const replayResponse = await client
      .post(`/api/v1/comments/posts/${post._id}`)
      .set(authHeader(viewerToken))
      .set("Idempotency-Key", idempotencyKey)
      .send({ content: "Only once" });

    expect(firstResponse.statusCode).toBe(201);
    expect(replayResponse.statusCode).toBe(201);
    expect(replayResponse.headers["x-idempotent-replay"]).toBe("true");
    expect(replayResponse.body.data._id).toBe(firstResponse.body.data._id);
  });

  it("saves and unsaves posts and exposes liked/saved history", async () => {
    const client = getClient();
    const { user: author } = await createAuthenticatedUser({
      identity: "history-author@example.com",
      username: "history_author",
    });
    const { accessToken: viewerToken } = await createAuthenticatedUser({
      identity: "history-viewer@example.com",
      username: "history_viewer",
    });

    const post = await createPost({ authorId: author._id, caption: "Saved post" });

    await client.post(`/api/v1/likes/posts/${post._id}`).set(authHeader(viewerToken));

    const likedHistoryResponse = await client
      .get("/api/v1/likes/posts/me")
      .set(authHeader(viewerToken));

    expect(likedHistoryResponse.statusCode).toBe(200);
    expect(likedHistoryResponse.body.data).toHaveLength(1);
    expect(likedHistoryResponse.body.data[0].post._id.toString()).toBe(post._id.toString());

    const saveResponse = await client
      .post(`/api/v1/posts/${post._id}/save`)
      .set(authHeader(viewerToken));

    expect(saveResponse.statusCode).toBe(201);

    const savedPostsResponse = await client
      .get("/api/v1/posts/saved/me")
      .set(authHeader(viewerToken));

    expect(savedPostsResponse.statusCode).toBe(200);
    expect(savedPostsResponse.body.data).toHaveLength(1);

    const unsaveResponse = await client
      .delete(`/api/v1/posts/${post._id}/save`)
      .set(authHeader(viewerToken));

    expect(unsaveResponse.statusCode).toBe(200);
    expect(unsaveResponse.body.data.saved).toBe(false);

    const { SavedPost } = getModels();
    expect(await SavedPost.countDocuments()).toBe(0);
  });
});
