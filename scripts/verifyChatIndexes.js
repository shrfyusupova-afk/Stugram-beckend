#!/usr/bin/env node

const mongoose = require("mongoose");
const dotenv = require("dotenv");

dotenv.config();

const REQUIRED_INDEXES = [
  {
    label: "Direct message idempotency",
    collectionName: "messages",
    expectedName: "uniq_conversation_sender_clientId",
    key: { conversation: 1, sender: 1, clientId: 1 },
    unique: true,
    partialFilterExpression: { clientId: { $type: "string" } },
  },
  {
    label: "Group message idempotency",
    collectionName: "groupmessages",
    expectedName: "uniq_groupConversation_sender_clientId",
    key: { groupConversation: 1, sender: 1, clientId: 1 },
    unique: true,
    partialFilterExpression: { clientId: { $type: "string" } },
  },
];

const stableStringify = (value) => {
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(",")}]`;
  if (value && typeof value === "object") {
    return `{${Object.keys(value)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`)
      .join(",")}}`;
  }
  return JSON.stringify(value);
};

const sameObject = (a, b) => stableStringify(a || {}) === stableStringify(b || {});

const normalizePartial = (partial) => {
  const clientId = partial?.clientId || {};
  if (clientId.$type === "string") {
    return { clientId: { $type: "string" } };
  }
  return partial || {};
};

const isRequiredIndex = (index, required) =>
  sameObject(index.key, required.key) &&
  index.unique === required.unique &&
  sameObject(normalizePartial(index.partialFilterExpression), required.partialFilterExpression);

const getMongoUri = () => process.env.MONGODB_URI || process.env.MONGO_URI;

const connect = async () => {
  const uri = getMongoUri();
  if (!uri) {
    throw new Error("MONGODB_URI or MONGO_URI is required to verify chat indexes");
  }

  await mongoose.connect(uri, {
    serverSelectionTimeoutMS: Number(process.env.MONGO_SERVER_SELECTION_TIMEOUT_MS || 15000),
    connectTimeoutMS: Number(process.env.MONGO_CONNECT_TIMEOUT_MS || 15000),
    socketTimeoutMS: Number(process.env.MONGO_SOCKET_TIMEOUT_MS || 45000),
    autoIndex: false,
  });
};

const verifyCollectionIndex = async (db, required) => {
  const indexes = await db.collection(required.collectionName).indexes();
  const matching = indexes.find((index) => isRequiredIndex(index, required));
  const sameName = indexes.find((index) => index.name === required.expectedName);

  if (matching) {
    const nameNote = matching.name === required.expectedName
      ? "name OK"
      : `name differs (${matching.name}); key/options OK`;
    return {
      ok: true,
      message: `${required.label}: OK (${required.collectionName}.${matching.name}, ${nameNote})`,
    };
  }

  const details = {
    expectedName: required.expectedName,
    expectedKey: required.key,
    expectedUnique: required.unique,
    expectedPartialFilterExpression: required.partialFilterExpression,
    existingSameName: sameName
      ? {
          name: sameName.name,
          key: sameName.key,
          unique: Boolean(sameName.unique),
          partialFilterExpression: sameName.partialFilterExpression || null,
        }
      : null,
  };

  return {
    ok: false,
    message: `${required.label}: MISSING_OR_MISCONFIGURED (${required.collectionName}.${required.expectedName})`,
    details,
  };
};

const main = async () => {
  console.log("STUGRAM chat idempotency index verification");
  console.log("Mode: verify only; no indexes or documents will be modified.");

  await connect();
  const db = mongoose.connection.db;
  console.log(`Connected database: ${db.databaseName}`);

  const results = [];
  for (const required of REQUIRED_INDEXES) {
    results.push(await verifyCollectionIndex(db, required));
  }

  let failures = 0;
  for (const result of results) {
    if (result.ok) {
      console.log(`PASS ${result.message}`);
    } else {
      failures += 1;
      console.error(`FAIL ${result.message}`);
      console.error(JSON.stringify(result.details, null, 2));
    }
  }

  if (failures > 0) {
    console.error(`Chat idempotency index verification failed: ${failures} required index(es) missing or misconfigured.`);
    process.exitCode = 2;
  } else {
    console.log("Chat idempotency index verification passed: all required indexes are present.");
  }
};

main()
  .catch((error) => {
    console.error("Chat idempotency index verification failed with an unexpected error:");
    console.error(error?.stack || error?.message || error);
    process.exitCode = 1;
  })
  .finally(async () => {
    if (mongoose.connection.readyState !== 0) {
      await mongoose.connection.close(false).catch(() => null);
    }
  });
