const { setupIntegrationTestSuite } = require("../helpers/integration");
const { authHeader, createAuthenticatedUser, createFollow, getModels } = require("../helpers/factories");

const { getClient } = setupIntegrationTestSuite();

describe("Follow integration", () => {
  it("follows and unfollows user with count updates", async () => {
    const client = getClient();
    const { user: follower, accessToken } = await createAuthenticatedUser({ username: "follow_a" });
    const { user: target } = await createAuthenticatedUser({ username: "follow_b" });

    const followResponse = await client.post(`/api/v1/follows/${target._id}`).set(authHeader(accessToken));
    expect(followResponse.statusCode).toBe(200);
    expect(followResponse.body.meta?.requestId).toBeTruthy();

    const { User } = getModels();
    const [followerAfterFollow, targetAfterFollow] = await Promise.all([
      User.findById(follower._id).lean(),
      User.findById(target._id).lean(),
    ]);
    expect(followerAfterFollow.followingCount).toBe(1);
    expect(targetAfterFollow.followersCount).toBe(1);

    const unfollowResponse = await client.delete(`/api/v1/follows/${target._id}`).set(authHeader(accessToken));
    expect(unfollowResponse.statusCode).toBe(200);

    const [followerAfterUnfollow, targetAfterUnfollow] = await Promise.all([
      User.findById(follower._id).lean(),
      User.findById(target._id).lean(),
    ]);
    expect(followerAfterUnfollow.followingCount).toBe(0);
    expect(targetAfterUnfollow.followersCount).toBe(0);
  });

  it("prevents self follow and duplicate follow", async () => {
    const client = getClient();
    const { user, accessToken } = await createAuthenticatedUser({ username: "follow_self" });
    const { user: target } = await createAuthenticatedUser({ username: "follow_dup" });

    const selfResponse = await client.post(`/api/v1/follows/${user._id}`).set(authHeader(accessToken));
    expect(selfResponse.statusCode).toBe(400);

    await createFollow({ followerId: user._id, followingId: target._id });
    const duplicateResponse = await client.post(`/api/v1/follows/${target._id}`).set(authHeader(accessToken));
    expect(duplicateResponse.statusCode).toBe(409);
  });

  it("blocks follow action when users are blocked", async () => {
    const client = getClient();
    const { user: actor, accessToken } = await createAuthenticatedUser({ username: "blocked_actor" });
    const { user: target } = await createAuthenticatedUser({ username: "blocked_target" });

    await client.post(`/api/v1/chats/users/${target._id}/block`).set(authHeader(accessToken));

    const response = await client.post(`/api/v1/follows/${target._id}`).set(authHeader(accessToken));
    expect(response.statusCode).toBe(403);
  });
});
