require("dotenv").config();

const { createJsonRequest, runConcurrent, summarizeResponses } = require("./shared");

const DEFAULT_BASE_URL = "https://stugram-beckend.onrender.com";

const baseUrl = String(process.argv[2] || process.env.STUGRAM_BACKEND_URL || DEFAULT_BASE_URL).replace(/\/+$/, "");
const directToken = process.env.STUGRAM_LOAD_DIRECT_TOKEN || null;
const groupToken = process.env.STUGRAM_LOAD_GROUP_TOKEN || directToken;
const conversationId = process.env.STUGRAM_LOAD_CONVERSATION_ID || null;
const groupId = process.env.STUGRAM_LOAD_GROUP_ID || null;

const requireEnv = (value, name) => {
  if (!value) {
    throw new Error(`${name} is required for chat load testing`);
  }
  return value;
};

const makeDirectMessageBody = (index) => ({
  clientId: `load-direct-${Date.now()}-${index}`,
  text: `load smoke ${index}`,
  messageType: "text",
});

const makeGroupMessageBody = (index) => ({
  clientId: `load-group-${Date.now()}-${index}`,
  text: `load group ${index}`,
  messageType: "text",
});

const run = async () => {
  requireEnv(directToken, "STUGRAM_LOAD_DIRECT_TOKEN");
  requireEnv(conversationId, "STUGRAM_LOAD_CONVERSATION_ID");
  requireEnv(groupToken, "STUGRAM_LOAD_GROUP_TOKEN");
  requireEnv(groupId, "STUGRAM_LOAD_GROUP_ID");

  const scenarios = [
    {
      name: "direct_chat_send",
      totalRequests: Number(process.env.STUGRAM_LOAD_DIRECT_REQUESTS || 40),
      concurrency: Number(process.env.STUGRAM_LOAD_DIRECT_CONCURRENCY || 5),
      task: (index) =>
        createJsonRequest({
          url: `${baseUrl}/api/v1/chats/conversations/${conversationId}/messages`,
          method: "POST",
          token: directToken,
          body: makeDirectMessageBody(index),
        }),
    },
    {
      name: "group_chat_send",
      totalRequests: Number(process.env.STUGRAM_LOAD_GROUP_REQUESTS || 40),
      concurrency: Number(process.env.STUGRAM_LOAD_GROUP_CONCURRENCY || 5),
      task: (index) =>
        createJsonRequest({
          url: `${baseUrl}/api/v1/group-chats/${groupId}/messages`,
          method: "POST",
          token: groupToken,
          body: makeGroupMessageBody(index),
        }),
    },
    {
      name: "direct_replay",
      totalRequests: Number(process.env.STUGRAM_LOAD_REPLAY_REQUESTS || 20),
      concurrency: Number(process.env.STUGRAM_LOAD_REPLAY_CONCURRENCY || 4),
      task: () =>
        createJsonRequest({
          url: `${baseUrl}/api/v1/chats/events?conversationId=${conversationId}&after=0&limit=50`,
          token: directToken,
        }),
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

  console.log(JSON.stringify({ ok: true, baseUrl, type: "chat", results }, null, 2));
};

run().catch((error) => {
  console.error(JSON.stringify({ ok: false, baseUrl, type: "chat", message: error.message, stack: error.stack }, null, 2));
  process.exit(1);
});
