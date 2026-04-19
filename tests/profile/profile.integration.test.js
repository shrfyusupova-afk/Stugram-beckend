const { setupIntegrationTestSuite } = require("../helpers/integration");
const { authHeader, createAuthenticatedUser, createPost, createUser, fakeImageBuffer } = require("../helpers/factories");

const { getClient } = setupIntegrationTestSuite();

describe("Profile integration", () => {
  it("fetches public profile and updates extended profile fields", async () => {
    const client = getClient();
    const { user, accessToken } = await createAuthenticatedUser({
      identity: "profile@example.com",
      username: "profile_user",
      fullName: "Profile User",
    });

    const getProfileResponse = await client.get(`/api/v1/profiles/${user.username}`);
    expect(getProfileResponse.statusCode).toBe(200);
    expect(getProfileResponse.body.data.username).toBe(user.username);

    const updateResponse = await client
      .patch("/api/v1/profiles/me")
      .set(authHeader(accessToken))
      .send({
        fullName: "Updated Profile User",
        bio: "Updated bio",
        birthday: "2001-05-20T00:00:00.000Z",
        location: "Tashkent",
        school: "TATU",
        region: "Toshkent",
        district: "Chilonzor",
        grade: "4-kurs",
        group: "A-12",
        isPrivateAccount: true,
      });

    expect(updateResponse.statusCode).toBe(200);
    expect(updateResponse.body.data.location).toBe("Tashkent");
    expect(updateResponse.body.data.school).toBe("TATU");
    expect(updateResponse.body.data.isPrivateAccount).toBe(true);
  });

  it("uploads avatar and banner", async () => {
    const client = getClient();
    const { accessToken } = await createAuthenticatedUser({
      identity: "uploads@example.com",
      username: "uploads_user",
    });

    const avatarResponse = await client
      .post("/api/v1/profiles/me/avatar")
      .set(authHeader(accessToken))
      .attach("avatar", fakeImageBuffer(), {
        filename: "avatar.jpg",
        contentType: "image/jpeg",
      });

    expect(avatarResponse.statusCode).toBe(200);
    expect(avatarResponse.body.data.avatar).toContain("stugram/avatars");

    const bannerResponse = await client
      .post("/api/v1/profiles/me/banner")
      .set(authHeader(accessToken))
      .attach("banner", fakeImageBuffer(), {
        filename: "banner.jpg",
        contentType: "image/jpeg",
      });

    expect(bannerResponse.statusCode).toBe(200);
    expect(bannerResponse.body.data.banner).toContain("stugram/banners");
  });

  it("enforces auth for profile ownership operations", async () => {
    const client = getClient();
    await createUser({
      identity: "owner@example.com",
      username: "owner_user",
    });

    const patchResponse = await client.patch("/api/v1/profiles/me").send({
      fullName: "Hacker",
    });
    expect(patchResponse.statusCode).toBe(401);

    const avatarResponse = await client
      .post("/api/v1/profiles/me/avatar")
      .attach("avatar", fakeImageBuffer(), {
        filename: "avatar.jpg",
        contentType: "image/jpeg",
    });
    expect(avatarResponse.statusCode).toBe(401);
  });

  it("returns a profile reels feed with only video posts", async () => {
    const client = getClient();
    const { user } = await createAuthenticatedUser({
      identity: "reels-profile@example.com",
      username: "reels_profile",
    });

    await createPost({
      authorId: user._id,
      media: [
        {
          url: "https://example.test/reel.mp4",
          publicId: "posts/reel-test",
          type: "video",
        },
      ],
      caption: "Video post",
    });

    await createPost({
      authorId: user._id,
      media: [
        {
          url: "https://example.test/image.jpg",
          publicId: "posts/image-test",
          type: "image",
        },
      ],
      caption: "Image post",
    });

    const reelsResponse = await client.get(`/api/v1/profiles/${user.username}/reels`);

    expect(reelsResponse.statusCode).toBe(200);
    expect(reelsResponse.body.data).toHaveLength(1);
    expect(reelsResponse.body.data[0].media[0].type).toBe("video");
  });

  it("returns tagged posts based on caption mentions", async () => {
    const client = getClient();
    const { user } = await createAuthenticatedUser({
      identity: "tagged-profile@example.com",
      username: "tagged_profile",
    });
    const { user: author } = await createAuthenticatedUser({
      identity: "tagged-author@example.com",
      username: "tagged_author",
    });

    await createPost({
      authorId: author._id,
      caption: `Hello @${user.username}`,
    });

    await createPost({
      authorId: author._id,
      caption: "No mention here",
    });

    const taggedResponse = await client.get(`/api/v1/profiles/${user.username}/tagged`);

    expect(taggedResponse.statusCode).toBe(200);
    expect(taggedResponse.body.data).toHaveLength(1);
    expect(taggedResponse.body.data[0].caption).toContain(`@${user.username}`);
  });
});
