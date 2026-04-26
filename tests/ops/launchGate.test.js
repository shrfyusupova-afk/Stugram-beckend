const { setupIntegrationTestSuite } = require("../helpers/integration");
const {
  authHeader,
  createAuthenticatedUser,
  createGroupConversation,
} = require("../helpers/factories");
const { env } = require("../../src/config/env");
const { summarizeLatencies, summarizeResponses } = require("../../src/scripts/load/shared");
const { buildReadinessReport } = require("../../src/scripts/verifyLaunchGate");

const { getClient } = setupIntegrationTestSuite();

describe("launch gate and rollout safety", () => {
  const originalFlags = {
    chatGroupSendEnabled: env.chatGroupSendEnabled,
    chatReplaySyncEnabled: env.chatReplaySyncEnabled,
  };

  afterEach(() => {
    env.chatGroupSendEnabled = originalFlags.chatGroupSendEnabled;
    env.chatReplaySyncEnabled = originalFlags.chatReplaySyncEnabled;
  });

  it("enforces chat kill switches for replay and group sending", async () => {
    const client = getClient();
    const { user: owner, accessToken: ownerToken } = await createAuthenticatedUser({
      identity: "launch-gate-owner@example.com",
      username: "launch_gate_owner",
    });
    const { user: member } = await createAuthenticatedUser({
      identity: "launch-gate-member@example.com",
      username: "launch_gate_member",
    });

    const group = await createGroupConversation({
      ownerId: owner._id,
      memberIds: [member._id],
      name: "Launch Gate Group",
    });
    const groupId = group._id.toString();

    env.chatGroupSendEnabled = false;
    const disabledGroupSendResponse = await client
      .post(`/api/v1/group-chats/${groupId}/messages`)
      .set(authHeader(ownerToken))
      .send({ text: "disabled" });
    expect(disabledGroupSendResponse.statusCode).toBe(503);

    env.chatReplaySyncEnabled = false;
    const disabledReplayResponse = await client
      .get("/api/v1/group-chats/events")
      .set(authHeader(ownerToken))
      .query({ groupId, after: 0, limit: 20 });
    expect(disabledReplayResponse.statusCode).toBe(503);
  });

  it("summarizes load results into machine-readable latency output", () => {
    const latencySummary = summarizeLatencies([100, 200, 300, 400, 500]);
    expect(latencySummary.count).toBe(5);
    expect(latencySummary.p50).toBe(300);
    expect(latencySummary.p95).toBe(500);

    const responseSummary = summarizeResponses([
      { ok: true, status: 200, durationMs: 120 },
      { ok: false, status: 429, durationMs: 240 },
      { ok: false, status: 503, durationMs: 360 },
    ]);
    expect(responseSummary.totalRequests).toBe(3);
    expect(responseSummary.rateLimitedCount).toBe(1);
    expect(responseSummary.serverErrorCount).toBe(1);
  });

  it("builds a readiness report with blockers and kill-switch states", () => {
    const report = buildReadinessReport({
      health: { json: { data: { runtimeMode: { closedAlphaNoWorker: false } } } },
      metrics: { json: { data: { metrics: { chat_rate_limit_hit_total: 1 } } } },
      steps: [
        { name: "privacy-suite", ok: true },
        { name: "load-smoke", ok: false },
      ],
    });

    expect(report.pass).toBe(false);
    expect(report.currentBlockers).toEqual(["load-smoke"]);
    expect(report.killSwitches).toEqual(
      expect.objectContaining({
        groupSendEnabled: expect.any(Boolean),
        replaySyncEnabled: expect.any(Boolean),
      })
    );
  });
});
