require("dotenv").config();

const { createJsonRequest, runConcurrent, summarizeResponses } = require("./shared");

const DEFAULT_BASE_URL = "https://stugram-beckend.onrender.com";

const baseUrl = String(process.argv[2] || process.env.STUGRAM_BACKEND_URL || DEFAULT_BASE_URL).replace(/\/+$/, "");
const token = process.env.STUGRAM_LOAD_ACCESS_TOKEN || null;
const monitoringKey = process.env.INTERNAL_METRICS_KEY || null;

const run = async () => {
  const scenarios = [
    {
      name: "health",
      totalRequests: 10,
      concurrency: 2,
      task: () => createJsonRequest({ url: `${baseUrl}/health` }),
    },
    {
      name: "search_users",
      totalRequests: 20,
      concurrency: 4,
      task: () => createJsonRequest({ url: `${baseUrl}/api/v1/search/users?q=ali`, token }),
    },
    {
      name: "profile_summary",
      totalRequests: 10,
      concurrency: 2,
      task: () => createJsonRequest({ url: `${baseUrl}/health/chat-observability`, headers: monitoringKey ? { "x-internal-monitoring-key": monitoringKey } : {} }),
    },
  ];

  const results = {};
  for (const scenario of scenarios) {
    const responses = await runConcurrent({
      totalRequests: scenario.totalRequests,
      concurrency: scenario.concurrency,
      task: scenario.task,
    });
    results[scenario.name] = summarizeResponses(responses);
  }

  console.log(JSON.stringify({ ok: true, baseUrl, type: "smoke", results }, null, 2));
};

run().catch((error) => {
  console.error(JSON.stringify({ ok: false, baseUrl, type: "smoke", message: error.message, stack: error.stack }, null, 2));
  process.exit(1);
});
