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
    duplicateGroup: {
      _id: { conversation: "$conversation", sender: "$sender", clientId: "$clientId" },
      count: { $sum: 1 },
      messageIds: { $push: "$_id" },
    },
  },
  {
    label: "Group message idempotency",
    collectionName: "groupmessages",
    expectedName: "uniq_groupConversation_sender_clientId",
    key: { groupConversation: 1, sender: 1, clientId: 1 },
    unique: true,
    partialFilterExpression: { clientId: { $type: "string" } },
    duplicateGroup: {
      _id: { groupConversation: "$groupConversation", sender: "$sender", clientId: "$clientId" },
      count: { $sum: 1 },
      messageIds: { $push: "$_id" },
    },
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
    throw new Error("MONGODB_URI or MONGO_URI is required to migrate chat indexes");
  }

  await mongoose.connect(uri, {
    serverSelectionTimeoutMS: Number(process.env.MONGO_SERVER_SELECTION_TIMEOUT_MS || 15000),
    connectTimeoutMS: Number(process.env.MONGO_CONNECT_TIMEOUT_MS || 15000),
    socketTimeoutMS: Number(process.env.MONGO_SOCKET_TIMEOUT_MS || 45000),
    autoIndex: false,
  });
};

const findDuplicateClientIds = async (collection, required) =>
  collection
    .aggregate([
      { $match: { clientId: { $type: "string" } } },
      { $group: required.duplicateGroup },
      { $match: { count: { $gt: 1 } } },
      { $limit: 20 },
    ])
    .toArray();

const ensureIndex = async (db, required) => {
  const collection = db.collection(required.collectionName);
  const indexes = await collection.indexes();
  const matching = indexes.find((index) => isRequiredIndex(index, required));

  if (matching) {
    console.log(`SKIP ${required.label}: already present (${required.collectionName}.${matching.name})`);
    return { created: false };
  }

  const sameName = indexes.find((index) => index.name === required.expectedName);
  if (sameName) {
    throw new Error(
      `${required.collectionName}.${required.expectedName} already exists but has different key/options. ` +
        "Refusing to drop or modify indexes automatically."
    );
  }

  const duplicates = await findDuplicateClientIds(collection, required);
  if (duplicates.length > 0) {
    const sample = duplicates.map((item) => ({
      key: item._id,
      count: item.count,
      messageIds: item.messageIds.slice(0, 5),
    }));
    const error = new Error(
      `${required.label}: cannot create unique index because duplicate clientId groups already exist. ` +
        "No documents were modified. Resolve duplicates manually, then rerun migration."
    );
    error.details = sample;
    throw error;
  }

  console.log(`CREATE ${required.label}: ${required.collectionName}.${required.expectedName}`);
  await collection.createIndex(required.key, {
    name: required.expectedName,
    unique: required.unique,
    partialFilterExpression: required.partialFilterExpression,
    background: true,
  });
  console.log(`CREATED ${required.label}: ${required.collectionName}.${required.expectedName}`);
  return { created: true };
};

const main = async () => {
  console.log("STUGRAM chat idempotency index migration");
  console.log("Mode: create missing indexes only; no indexes will be dropped and no documents will be modified.");

  await connect();
  const db = mongoose.connection.db;
  console.log(`Connected database: ${db.databaseName}`);

  let createdCount = 0;
  for (const required of REQUIRED_INDEXES) {
    const result = await ensureIndex(db, required);
    if (result.created) createdCount += 1;
  }

  console.log(`Chat idempotency index migration completed. Indexes created: ${createdCount}.`);
};

main()
  .catch((error) => {
    console.error("Chat idempotency index migration failed:");
    console.error(error?.stack || error?.message || error);
    if (error?.details) {
      console.error("Duplicate sample:");
      console.error(JSON.stringify(error.details, null, 2));
    }
    process.exitCode = 1;
  })
  .finally(async () => {
    if (mongoose.connection.readyState !== 0) {
      await mongoose.connection.close(false).catch(() => null);
    }
  });
