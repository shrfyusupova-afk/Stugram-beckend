const { setupIntegrationTestSuite } = require("../helpers/integration");
const {
  authHeader,
  createAuthenticatedUser,
  createFollow,
  createPost,
  createUser,
} = require("../helpers/factories");

const { getClient } = setupIntegrationTestSuite();

describe("Search integration", () => {
  it("supports basic search and advanced user filters", async () => {
    const client = getClient();
    const user = await createUser({
      identity: "ali@example.com",
      username: "ali_chilonzor",
      fullName: "Ali Valiyev",
      region: "Toshkent",
      district: "Chilonzor",
      school: "TATU",
      grade: "4-kurs",
      group: "A-12",
      location: "Tashkent",
    });

    await createPost({
      authorId: user._id,
      caption: "ali startup life",
      hashtags: ["startup"],
      location: "Tashkent",
    });

    const usersSearchResponse = await client.get("/api/v1/search/users").query({ q: "ali" });
    expect(usersSearchResponse.statusCode).toBe(200);
    expect(usersSearchResponse.body.data.length).toBeGreaterThan(0);

    const advancedSearchResponse = await client.get("/api/v1/search/users/advanced").query({
      region: "Toshkent",
      district: "Chilonzor",
      school: "TATU",
      grade: "4-kurs",
    });

    expect(advancedSearchResponse.statusCode).toBe(200);
    expect(advancedSearchResponse.body.data).toHaveLength(1);
    expect(advancedSearchResponse.body.data[0].username).toBe("ali_chilonzor");
  });

  it("stores, lists, deletes, and clears search history without duplicates", async () => {
    const client = getClient();
    const { accessToken } = await createAuthenticatedUser({
      identity: "history-search@example.com",
      username: "history_search",
    });

    const firstSave = await client
      .post("/api/v1/search/history")
      .set(authHeader(accessToken))
      .send({
        queryText: "ali",
        searchType: "user",
      });

    const secondSave = await client
      .post("/api/v1/search/history")
      .set(authHeader(accessToken))
      .send({
        queryText: "ali",
        searchType: "user",
      });

    expect(firstSave.statusCode).toBe(201);
    expect(secondSave.statusCode).toBe(201);

    const listResponse = await client
      .get("/api/v1/search/history")
      .set(authHeader(accessToken));

    expect(listResponse.statusCode).toBe(200);
    expect(listResponse.body.data).toHaveLength(1);

    const deleteResponse = await client
      .delete(`/api/v1/search/history/${listResponse.body.data[0]._id}`)
      .set(authHeader(accessToken));

    expect(deleteResponse.statusCode).toBe(200);

    await client
      .post("/api/v1/search/history")
      .set(authHeader(accessToken))
      .send({
        queryText: "startup",
        searchType: "keyword",
      });

    const clearResponse = await client
      .delete("/api/v1/search/history")
      .set(authHeader(accessToken));

    expect(clearResponse.statusCode).toBe(200);
    expect(clearResponse.body.data.cleared).toBe(true);
  });

  it("filters blocked and private users from search and suggestions while preserving followed private access", async () => {
    const client = getClient();
    const { user: viewer, accessToken: viewerToken } = await createAuthenticatedUser({
      identity: "search-viewer@example.com",
      username: "search_viewer",
    });
    const { user: blockedUser } = await createAuthenticatedUser({
      identity: "search-blocked@example.com",
      username: "search_blocked_target",
      fullName: "Blocked Search Target",
    });
    const { user: privateUser } = await createAuthenticatedUser({
      identity: "search-private@example.com",
      username: "search_private_target",
      fullName: "Private Search Target",
      isPrivateAccount: true,
      school: "Private School",
    });

    await client
      .post(`/api/v1/chats/users/${blockedUser._id}/block`)
      .set(authHeader(viewerToken));

    const publicSearchResponse = await client.get("/api/v1/search/users").query({ q: "search_" });
    expect(publicSearchResponse.statusCode).toBe(200);
    expect(publicSearchResponse.body.data.some((item) => item.username === privateUser.username)).toBe(false);

    const viewerSearchResponse = await client
      .get("/api/v1/search/users")
      .set(authHeader(viewerToken))
      .query({ q: "search_" });
    expect(viewerSearchResponse.statusCode).toBe(200);
    expect(viewerSearchResponse.body.data.some((item) => item.username === blockedUser.username)).toBe(false);
    expect(viewerSearchResponse.body.data.some((item) => item.username === privateUser.username)).toBe(false);

    await createFollow({ followerId: viewer._id, followingId: privateUser._id });

    const followedPrivateSearchResponse = await client
      .get("/api/v1/search/users")
      .set(authHeader(viewerToken))
      .query({ q: "search_" });
    expect(followedPrivateSearchResponse.statusCode).toBe(200);
    expect(followedPrivateSearchResponse.body.data.some((item) => item.username === privateUser.username)).toBe(true);

    const suggestionsResponse = await client
      .get("/api/v1/search/suggestions")
      .set(authHeader(viewerToken))
      .query({ q: "search_" });
    expect(suggestionsResponse.statusCode).toBe(200);
    expect(suggestionsResponse.body.data.users.some((item) => item.username === blockedUser.username)).toBe(false);
  });

  it("does not leak sensitive fields through public user discovery routes", async () => {
    const client = getClient();
    await createUser({
      identity: "search-fields@example.com",
      username: "search_fields_user",
      fullName: "Search Fields User",
    });

    const response = await client.get("/api/v1/search/users").query({ q: "search_fields" });
    expect(response.statusCode).toBe(200);
    expect(response.body.data[0].passwordHash).toBeUndefined();
    expect(response.body.data[0].accountId).toBeUndefined();
    expect(response.body.data[0].googleId).toBeUndefined();
    expect(response.body.data[0].tokenInvalidBefore).toBeUndefined();
  });
});
