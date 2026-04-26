require("dotenv").config();

const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");
const { env } = require("../config/env");

const DEFAULT_BASE_URL = "https://stugram-beckend.onrender.com";
const baseUrl = String(process.argv[2] || process.env.STUGRAM_BACKEND_URL || DEFAULT_BASE_URL).replace(/\/+$/, "");
const skipCommands = process.env.LAUNCH_GATE_SKIP_COMMANDS === "true";
const readinessPath = path.resolve(__dirname, "../../LAUNCH_READINESS.json");

const launchThresholds = {
  closedBeta: {
    sendP95Ms: 1500,
    replayP95Ms: 1200,
    terminalFailureRate: 0.01,
    replayFailureRate: 0.01,
    pendingOldestAgeMs: 10 * 60 * 1000,
  },
  widerBeta: {
    sendP95Ms: 1000,
    replayP95Ms: 900,
    terminalFailureRate: 0.005,
    replayFailureRate: 0.005,
    pendingOldestAgeMs: 5 * 60 * 1000,
  },
};

const runStep = (name, command, args = []) => {
  if (skipCommands) {
    return { name, ok: true, skipped: true };
  }

  const result = spawnSync(command, args, {
    cwd: path.resolve(__dirname, "../.."),
    encoding: "utf8",
    env: process.env,
  });

  return {
    name,
    ok: result.status === 0,
    status: result.status,
    stdout: result.stdout,
    stderr: result.stderr,
  };
};

const fetchJson = async (url, headers = {}) => {
  const response = await fetch(url, { headers: { Accept: "application/json", ...headers } });
  const json = await response.json();
  return { ok: response.ok, status: response.status, json };
};

const buildReadinessReport = ({ health, metrics, steps }) => {
  const metricSnapshot = metrics?.json?.data?.metrics || {};
  const blockers = steps.filter((step) => !step.ok).map((step) => step.name);

  return {
    generatedAt: new Date().toISOString(),
    baseUrl,
    testedSafeScale: "closed-beta-low-hundreds",
    currentBlockers: blockers,
    acceptedRisksClosedBeta: [
      "Heavy 1000-user traffic still requires manual pre-launch load execution with production-like tokens.",
      "Realtime kill switch disables chat emits but does not redesign client UX.",
    ],
    unacceptableRisksPublicLaunch: [
      "Any privacy/access integration failure",
      "Replay sync disabled without fallback plan",
      "Terminal send failures above launch thresholds",
    ],
    killSwitches: {
      groupSendEnabled: env.chatGroupSendEnabled,
      mediaSendEnabled: env.chatMediaSendEnabled,
      replaySyncEnabled: env.chatReplaySyncEnabled,
      realtimeEnabled: env.chatRealtimeEnabled,
      rateLimitStrictMode: env.chatRateLimitStrictMode,
    },
    metricsToWatch: {
      first24Hours: [
        "chat_send_failed_terminal_total",
        "chat_replay_sync_failure_total",
        "chat_rate_limit_hit_total",
        "chat_pending_oldest_age_ms",
      ],
      first7Days: [
        "chat_duplicate_resolved_total",
        "chat_5xx_total",
        "chat_socket_emit_total",
        "chat_media_upload_failure_total",
      ],
    },
    thresholds: launchThresholds,
    health: health?.json?.data || null,
    metrics: metricSnapshot,
    launchSteps: steps.map((step) => ({
      name: step.name,
      ok: step.ok,
      skipped: Boolean(step.skipped),
      status: step.status ?? null,
    })),
    pass: blockers.length === 0,
  };
};

const main = async () => {
  const monitoringHeaders = process.env.INTERNAL_METRICS_KEY
    ? { "x-internal-monitoring-key": process.env.INTERNAL_METRICS_KEY }
    : {};

  const [health, metrics] = await Promise.all([
    fetchJson(`${baseUrl}/health`),
    fetchJson(`${baseUrl}/health/chat-observability`, monitoringHeaders),
  ]);

  const steps = [
    runStep("verify-chat-indexes", "node", ["scripts/verifyChatIndexes.js"]),
    runStep("smoke-production", "node", ["src/scripts/smokeProduction.js", baseUrl]),
    runStep("privacy-and-chat-tests", "npm", ["test", "--", "--runTestsByPath", "tests/profile/profile.integration.test.js", "tests/search/search.integration.test.js", "tests/chat/chat.integration.test.js", "tests/realtime/socket.integration.test.js"]),
    runStep("load-smoke", "node", ["src/scripts/load/loadSmoke.js", baseUrl]),
  ];

  const report = buildReadinessReport({ health, metrics, steps });
  fs.writeFileSync(readinessPath, `${JSON.stringify(report, null, 2)}\n`);

  console.log(JSON.stringify(report, null, 2));
  if (!report.pass) {
    process.exit(1);
  }
};

if (require.main === module) {
  main().catch((error) => {
    console.error(JSON.stringify({ ok: false, message: error.message, stack: error.stack }, null, 2));
    process.exit(1);
  });
}

module.exports = {
  buildReadinessReport,
  launchThresholds,
};
