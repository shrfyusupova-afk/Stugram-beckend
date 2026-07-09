const { setupIntegrationTestSuite } = require("../helpers/integration");
const { createAuthenticatedUser, authHeader } = require("../helpers/factories");
const { env } = require("../../src/config/env");

const { getClient } = setupIntegrationTestSuite();

describe("Request correlation middleware", () => {
  afterEach(() => {
    env.chatReplaySyncEnabled = true;
  });

  it("generates requestId when x-request-id is missing", async () => {
    const client = getClient();
    const response = await client.get("/");

    const headerRequestId = response.headers["x-request-id"];
    expect(typeof headerRequestId).toBe("string");
    expect(headerRequestId.length).toBeGreaterThan(8);
    expect(response.body.meta.requestId).toBe(headerRequestId);
    expect(typeof response.body.meta.timestamp).toBe("string");
  });

  it("preserves valid client-provided x-request-id", async () => {
    const client = getClient();
    const clientRequestId = "android_req_123.abc-xyz";
    const response = await client.get("/").set("x-request-id", clientRequestId);

    expect(response.headers["x-request-id"]).toBe(clientRequestId);
    expect(response.body.meta.requestId).toBe(clientRequestId);
  });

  it("replaces invalid or too-long x-request-id", async () => {
    const client = getClient();
    const invalid = "A".repeat(180);
    const response = await client.get("/").set("x-request-id", invalid);

    expect(response.headers["x-request-id"]).not.toBe(invalid);
    expect(response.body.meta.requestId).toBe(response.headers["x-request-id"]);
    expect(response.body.meta.requestId.length).toBeLessThanOrEqual(128);
  });

  it("feature-gated response includes requestId in header and meta", async () => {
    const client = getClient();
    const { accessToken } = await createAuthenticatedUser({
      identity: "reqid-feature@example.com",
      username: "reqid_feature",
    });

    env.chatReplaySyncEnabled = false;
    const response = await client
      .get("/api/v1/chats/events")
      .set(authHeader(accessToken));

    expect(response.statusCode).toBe(503);
    expect(response.headers["x-request-id"]).toBeTruthy();
    expect(response.body.meta.requestId).toBe(response.headers["x-request-id"]);
    expect(response.body.error.code).toBe("FEATURE_CHAT_REPLAY_DISABLED");
  });

  it("error handler response includes requestId", async () => {
    const client = getClient();
    const response = await client.get("/api/v1/unknown-route");

    expect(response.statusCode).toBe(404);
    expect(response.headers["x-request-id"]).toBeTruthy();
    expect(response.body.meta.requestId).toBe(response.headers["x-request-id"]);
    expect(response.body.success).toBe(false);
  });

  it("success envelope includes meta.requestId", async () => {
    const client = getClient();
    const { accessToken } = await createAuthenticatedUser({
      identity: "reqid-success@example.com",
      username: "reqid_success",
    });

    const response = await client
      .get("/api/v1/chats/summary")
      .set(authHeader(accessToken));

    expect(response.statusCode).toBe(200);
    expect(response.body.meta.requestId).toBe(response.headers["x-request-id"]);
    expect(typeof response.body.meta.timestamp).toBe("string");
  });
});
