const { setupIntegrationTestSuite } = require("../helpers/integration");
const { authHeader, createAuthenticatedUser, createPost, createUser } = require("../helpers/factories");

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
});
