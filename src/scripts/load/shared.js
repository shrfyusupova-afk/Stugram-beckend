const percentile = (sortedValues, ratio) => {
  if (!sortedValues.length) return 0;
  const index = Math.min(sortedValues.length - 1, Math.max(0, Math.ceil(sortedValues.length * ratio) - 1));
  return sortedValues[index];
};

const summarizeLatencies = (durations = []) => {
  if (!durations.length) {
    return { count: 0, min: 0, max: 0, p50: 0, p95: 0, p99: 0, average: 0 };
  }

  const sorted = [...durations].sort((a, b) => a - b);
  const total = sorted.reduce((sum, value) => sum + value, 0);
  return {
    count: sorted.length,
    min: sorted[0],
    max: sorted[sorted.length - 1],
    p50: percentile(sorted, 0.5),
    p95: percentile(sorted, 0.95),
    p99: percentile(sorted, 0.99),
    average: Math.round(total / sorted.length),
  };
};

const createJsonRequest = async ({ url, method = "GET", token = null, body = null, headers = {}, fetchImpl = fetch }) => {
  const startedAt = Date.now();
  const response = await fetchImpl(url, {
    method,
    headers: {
      Accept: "application/json",
      ...(body ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const durationMs = Date.now() - startedAt;
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch (_error) {
    json = null;
  }

  return {
    ok: response.ok,
    status: response.status,
    durationMs,
    json,
    text,
  };
};

const runConcurrent = async ({ totalRequests, concurrency, task }) => {
  const results = [];
  let nextIndex = 0;

  const worker = async () => {
    while (nextIndex < totalRequests) {
      const currentIndex = nextIndex;
      nextIndex += 1;
      results[currentIndex] = await task(currentIndex);
    }
  };

  await Promise.all(Array.from({ length: Math.max(1, concurrency) }, () => worker()));
  return results;
};

const summarizeResponses = (responses = []) => {
  const statusCounts = {};
  responses.forEach((response) => {
    statusCounts[response.status] = (statusCounts[response.status] || 0) + 1;
  });

  return {
    totalRequests: responses.length,
    successCount: responses.filter((item) => item.ok).length,
    statusCounts,
    rateLimitedCount: responses.filter((item) => item.status === 429).length,
    serverErrorCount: responses.filter((item) => item.status >= 500).length,
    latencyMs: summarizeLatencies(responses.map((item) => item.durationMs)),
  };
};

module.exports = {
  summarizeLatencies,
  createJsonRequest,
  runConcurrent,
  summarizeResponses,
};
