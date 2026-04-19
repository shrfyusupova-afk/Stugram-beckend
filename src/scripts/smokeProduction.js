require("dotenv").config();

const DEFAULT_BASE_URL = "https://stugram-beckend.onrender.com";
const REQUEST_TIMEOUT_MS = Number(process.env.SMOKE_REQUEST_TIMEOUT_MS || 45000);

const baseUrl = String(process.argv[2] || process.env.STUGRAM_BACKEND_URL || DEFAULT_BASE_URL).replace(/\/+$/, "");
const expectedMode = String(process.env.STUGRAM_SMOKE_MODE || "closed-alpha").trim().toLowerCase();

const isClosedAlphaQueueDisabled = (data = {}) =>
  data.recommendationMode === "db-direct" &&
  data.workerRequired === false &&
  data.queueHealth?.queue?.enabled === false &&
  data.queueHealth?.queue?.mode === "disabled-for-closed-alpha";

const isRedisAcceptable = (data = {}) => {
  if (data.redisRequired === true) {
    return data.redisConnected === true && data.redisMode === "connected";
  }

  return (
    data.redisConnected === true ||
    data.redisMode === "connected" ||
    data.cacheMode === "disabled" ||
    data.cacheMode === "redis-optional-unavailable"
  );
};

const assertHealth = (body) => {
  const data = body?.data || {};
  const coreReady =
    body?.success === true &&
    data.mongoConnected === true &&
    data.mongoMode === "atlas" &&
    data.cloudinaryConfigured === true &&
    data.pushEnabled === true &&
    isRedisAcceptable(data);

  if (!coreReady) return false;

  if (expectedMode === "full-production") {
    return (
      data.redisConnected === true &&
      data.redisMode === "connected" &&
      data.queueHealth?.queue?.enabled === true &&
      data.queueHealth?.queue?.ready === true &&
      data.cacheMode === "enabled"
    );
  }

  return isClosedAlphaQueueDisabled(data) || (
    data.redisConnected === true &&
    data.redisMode === "connected" &&
    data.queueHealth?.queue?.enabled === true &&
    data.queueHealth?.queue?.ready === true
  );
};

const checks = [
  {
    path: "/livez",
    name: "livez",
    assert: (body) => body?.success === true,
  },
  {
    path: "/health",
    name: "health",
    assert: assertHealth,
  },
  {
    path: "/readyz",
    name: "readyz",
    assert: (body) => body?.success === true,
  },
  {
    path: "/health/push",
    name: "push",
    assert: (body) => body?.success === true && body?.data?.pushEnabled === true,
  },
];

const runCheck = async (check) => {
  const url = `${baseUrl}${check.path}`;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  let response;
  try {
    response = await fetch(url, {
      headers: {
        Accept: "application/json",
      },
      signal: controller.signal,
    });
  } finally {
    clearTimeout(timeout);
  }
  const text = await response.text();
  let body = null;

  try {
    body = text ? JSON.parse(text) : null;
  } catch (_error) {
    throw new Error(`${check.name} returned non-JSON response: ${text.slice(0, 200)}`);
  }

  if (!response.ok) {
    throw new Error(`${check.name} returned HTTP ${response.status}: ${JSON.stringify(body)}`);
  }

  if (!check.assert(body)) {
    throw new Error(`${check.name} failed readiness assertion: ${JSON.stringify(body?.data || body)}`);
  }

  return {
    name: check.name,
    status: response.status,
    ok: true,
  };
};

const main = async () => {
  console.log(`Running production smoke checks against ${baseUrl} in ${expectedMode} mode`);
  const results = [];

  for (const check of checks) {
    results.push(await runCheck(check));
  }

  console.log(JSON.stringify({ ok: true, baseUrl, results }, null, 2));
};

main().catch((error) => {
  console.error(
    JSON.stringify(
      {
        ok: false,
        baseUrl,
        name: error.name,
        message: error.message,
        stack: error.stack,
      },
      null,
      2
    )
  );
  process.exit(1);
});
