const mongoose = require("mongoose");
const dns = require("dns");

const { env } = require("./env");
const logger = require("../utils/logger");
const {
  classifyMongoConnectionFailure,
  classifyMongoConnectionFailureWithDiagnostics,
  getErrorSummary,
  parseMongoUri,
  runAtlasConnectivityDiagnostics,
  sanitizeMongoUri,
} = require("../utils/atlasDiagnostics");

let isConnectionEventsBound = false;
let isQueryInstrumentationBound = false;
let memoryMongoServer = null;
let mongoMode = "disconnected";
let lastAtlasFailure = null;
let lastAtlasReport = null;

const mongooseReadyStates = {
  0: "disconnected",
  1: "connected",
  2: "connecting",
  3: "disconnecting",
};

const bindConnectionEvents = () => {
  if (isConnectionEventsBound) return;

  mongoose.connection.on("connected", () => {
    logger.info("MongoDB connected");
  });

  mongoose.connection.on("error", (error) => {
    logger.error("MongoDB connection error", {
      message: error.message,
    });
  });

  mongoose.connection.on("disconnected", () => {
    logger.warn("MongoDB disconnected");
  });

  isConnectionEventsBound = true;
};

const buildMongoOptions = () => {
  const options = {
    autoIndex: env.nodeEnv !== "production",
    serverSelectionTimeoutMS: env.mongoServerSelectionTimeoutMs,
    connectTimeoutMS: env.mongoConnectTimeoutMs,
    socketTimeoutMS: env.mongoSocketTimeoutMs,
    maxPoolSize: env.nodeEnv === "production" ? 20 : 10,
  };

  if (env.mongoForceIpv4) {
    options.family = 4;
  }

  return options;
};

const buildAtlasContext = () => {
  const options = buildMongoOptions();
  const uriReport = parseMongoUri(env.mongoUri);

  return {
    target: sanitizeMongoUri(env.mongoUri),
    mode: "atlas",
    clusterHost: uriReport.host || null,
    dbName: uriReport.dbName || null,
    isSrv: uriReport.isSrv || false,
    hasUsername: uriReport.hasUsername || false,
    hasPassword: uriReport.hasPassword || false,
    nodeVersion: process.version,
    opensslVersion: process.versions.openssl || null,
    dnsServers: env.mongoDnsServers.length ? env.mongoDnsServers : dns.getServers(),
    serverSelectionTimeoutMs: options.serverSelectionTimeoutMS,
    connectTimeoutMs: options.connectTimeoutMS,
    socketTimeoutMs: options.socketTimeoutMS,
    forceIpv4: Boolean(options.family === 4),
    memoryFallbackAllowed: env.allowMemoryDbFallback,
  };
};

const sanitizeQueryPayload = (value) => {
  if (value == null) return value;

  try {
    return JSON.parse(
      JSON.stringify(value, (_key, currentValue) => {
        if (typeof currentValue === "string" && currentValue.length > 200) {
          return `${currentValue.slice(0, 200)}...`;
        }

        if (Array.isArray(currentValue) && currentValue.length > 20) {
          return [...currentValue.slice(0, 20), `...(${currentValue.length - 20} more)`];
        }

        return currentValue;
      })
    );
  } catch (_error) {
    return "[unserializable]";
  }
};

const bindQueryInstrumentation = () => {
  if (isQueryInstrumentationBound) return;

  const originalQueryExec = mongoose.Query.prototype.exec;
  mongoose.Query.prototype.exec = async function observedQueryExec(...args) {
    const startedAt = process.hrtime.bigint();

    try {
      return await originalQueryExec.apply(this, args);
    } finally {
      const durationMs = Number(process.hrtime.bigint() - startedAt) / 1_000_000;
      const payload = {
        model: this.model?.modelName || null,
        operation: this.op || null,
        durationMs: Number(durationMs.toFixed(2)),
        filter: sanitizeQueryPayload(this.getFilter?.() || this._conditions || {}),
      };

      if (durationMs >= env.mongoSlowQueryMs) {
        logger.warn("Slow Mongo query detected", payload);
      } else if (env.mongoQueryDebug) {
        logger.info("Mongo query completed", payload);
      }
    }
  };

  const originalAggregateExec = mongoose.Aggregate.prototype.exec;
  mongoose.Aggregate.prototype.exec = async function observedAggregateExec(...args) {
    const startedAt = process.hrtime.bigint();

    try {
      return await originalAggregateExec.apply(this, args);
    } finally {
      const durationMs = Number(process.hrtime.bigint() - startedAt) / 1_000_000;
      const payload = {
        model: this._model?.modelName || null,
        operation: "aggregate",
        durationMs: Number(durationMs.toFixed(2)),
        pipeline: sanitizeQueryPayload(this._pipeline || []),
      };

      if (durationMs >= env.mongoSlowQueryMs) {
        logger.warn("Slow Mongo query detected", payload);
      } else if (env.mongoQueryDebug) {
        logger.info("Mongo query completed", payload);
      }
    }
  };

  isQueryInstrumentationBound = true;
};

const ensureCriticalIndexes = async () => {
  // Production-safe: create the minimum indexes needed for auth + settings hot paths.
  // This is idempotent and does not log sensitive values.
  if (!mongoose.connection?.db) return;

  const hasSameKey = (a, b) => {
    const aKeys = Object.keys(a || {});
    const bKeys = Object.keys(b || {});
    if (aKeys.length !== bKeys.length) return false;
    return aKeys.every((key) => a[key] === b[key]);
  };

  const boolOrFalse = (value) => (value === true ? true : false);

  const ensureIndex = async ({ collectionName, key, options, warnLabel }) => {
    const collection = mongoose.connection.db.collection(collectionName);
    try {
      const existing = await collection
        .listIndexes()
        .toArray()
        .catch(() => []);

      const match = existing.find((idx) => hasSameKey(idx.key, key));
      if (match) {
        const expectedUnique = boolOrFalse(options.unique);
        const expectedSparse = boolOrFalse(options.sparse);
        const actualUnique = boolOrFalse(match.unique);
        const actualSparse = boolOrFalse(match.sparse);

        if (expectedUnique === actualUnique && expectedSparse === actualSparse) {
          return;
        }

        logger.warn(warnLabel, {
          message: "Index exists but options differ",
          existingName: match.name,
          existingUnique: actualUnique,
          existingSparse: actualSparse,
          expectedUnique,
          expectedSparse,
        });
        return;
      }

      await collection.createIndex(key, options);
    } catch (error) {
      // If index already exists (different name), treat it as healthy.
      const msg = String(error?.message || "");
      if (msg.includes("Index already exists with a different name")) {
        return;
      }
      logger.warn(warnLabel, { message: error.message });
    }
  };

  try {
    await ensureIndex({
      collectionName: "users",
      key: { identity: 1 },
      options: { unique: true, sparse: true },
      warnLabel: "Failed to ensure users.identity index",
    });

    await ensureIndex({
      collectionName: "settings",
      key: { user: 1 },
      options: { unique: true },
      warnLabel: "Failed to ensure settings.user index",
    });

    await ensureIndex({
      collectionName: "accounts",
      key: { identity: 1 },
      options: { unique: true },
      warnLabel: "Failed to ensure accounts.identity index",
    });
  } catch (error) {
    logger.warn("Failed to ensure critical indexes", { message: error.message });
  }
};

const connectDatabase = async () => {
  bindConnectionEvents();
  bindQueryInstrumentation();

  if (mongoose.connection.readyState === 1) {
    return mongoose.connection;
  }

  mongoose.set("strictQuery", true);
  if (env.mongoDnsServers.length) {
    dns.setServers(env.mongoDnsServers);
  }

  try {
    const uriReport = parseMongoUri(env.mongoUri);
    lastAtlasReport = {
      uri: uriReport,
      dns: null,
      tls: null,
      connection: {
        uriAttempted: false,
        connected: false,
        pingSucceeded: false,
        error: null,
        classification: null,
      },
      serverSelectionSucceeded: false,
      authenticationSucceeded: false,
      selectedMode: "disconnected",
    };

    logger.info("Connecting to MongoDB primary datastore", buildAtlasContext());

    if (!uriReport.valid) {
      throw new Error(`Invalid MongoDB URI: ${uriReport.error?.message || "parse failed"}`);
    }

    await mongoose.connect(env.mongoUri, buildMongoOptions());
    await mongoose.connection.db.admin().ping();
    mongoMode = "atlas";
    lastAtlasFailure = null;
    lastAtlasReport = {
      ...lastAtlasReport,
      serverSelectionSucceeded: true,
      authenticationSucceeded: true,
      connection: {
        uriAttempted: true,
        connected: true,
        pingSucceeded: true,
        error: null,
        classification: null,
      },
      selectedMode: "atlas",
    };
    logger.info("MongoDB primary datastore selected", {
      mode: mongoMode,
      clusterHost: uriReport.host || null,
      dbName: uriReport.dbName || null,
      state: mongooseReadyStates[mongoose.connection.readyState] || "unknown",
    });
    // Fire-and-forget: avoid blocking startup while still healing missing indexes
    // when production was launched with autoIndex disabled.
    void ensureCriticalIndexes();
    return mongoose.connection;
  } catch (error) {
    let diagnostics = null;
    let classification = classifyMongoConnectionFailure(error);
    let pingSucceeded = false;

    if (env.mongoStartupDiagnostics) {
      try {
        diagnostics = await runAtlasConnectivityDiagnostics(env.mongoUri, {
          tlsTimeoutMs: Math.min(env.mongoConnectTimeoutMs, 7000),
          maxTlsHosts: 2,
        });
        classification = classifyMongoConnectionFailureWithDiagnostics(error, diagnostics);
        pingSucceeded = Boolean(diagnostics?.connection?.pingSucceeded);
        lastAtlasReport = {
          ...lastAtlasReport,
          uri: diagnostics.uri,
          dns: diagnostics.dns,
          tls: diagnostics.tls,
          node: diagnostics.node,
          connection: diagnostics.connection,
          failure: {
            ...getErrorSummary(error),
            classification,
          },
        };
        logger.error("MongoDB Atlas connectivity diagnostics", {
          clusterHost: diagnostics.uri.host || null,
          dbName: diagnostics.uri.dbName || null,
          dnsServersUsed: diagnostics.dns.serversUsed,
          dnsSrvAttempted: diagnostics.dns.srvLookupAttempted,
          dnsSrvResolved: diagnostics.dns.srvResolved,
          dnsTxtResolved: diagnostics.dns.txtResolved,
          resolvedHosts: diagnostics.dns.resolvedHosts,
          tlsAttempted: diagnostics.tls.attempted,
          tlsResults: diagnostics.tls.results,
          connectAttempted: diagnostics.connection.uriAttempted,
          connectSucceeded: diagnostics.connection.connected,
          pingSucceeded,
          failureClassification: classification,
        });
      } catch (diagnosticError) {
        logger.warn("MongoDB Atlas connectivity diagnostics failed", {
          ...getErrorSummary(diagnosticError),
        });
      }
    }
    lastAtlasFailure = {
      ...getErrorSummary(error),
      classification,
    };
    mongoMode = "disconnected";
    if (lastAtlasReport) {
      lastAtlasReport = {
        ...lastAtlasReport,
        serverSelectionSucceeded: false,
        authenticationSucceeded: classification !== "bad_credentials_or_database_access" ? false : false,
        connection: {
          uriAttempted: true,
          connected: false,
          pingSucceeded: false,
          error: lastAtlasFailure,
          classification,
        },
        selectedMode: "disconnected",
        failure: lastAtlasFailure,
      };
    }

    logger.error("MongoDB primary datastore connection failed", {
      ...lastAtlasFailure,
      target: sanitizeMongoUri(env.mongoUri),
      likelyCause: classification,
      memoryFallbackAllowed: env.allowMemoryDbFallback,
      nodeEnv: env.nodeEnv,
      pingSucceeded,
    });

    if (env.nodeEnv !== "production" && env.allowMemoryDbFallback) {
      try {
        const { MongoMemoryServer } = require("mongodb-memory-server");

        if (!memoryMongoServer) {
          memoryMongoServer = await MongoMemoryServer.create({
            instance: {
              dbName: "stugram_dev",
              ip: "127.0.0.1",
              // Use an auto-assigned port so the dev fallback does not collide
              // with any existing local MongoDB or stale test process.
            },
          });
        }

        const fallbackUri = memoryMongoServer.getUri();
        logger.warn("MongoDB memory fallback explicitly enabled for development", {
          mode: "memory-fallback",
          uri: sanitizeMongoUri(fallbackUri),
          firstAtlasFailure: lastAtlasFailure,
        });

        await mongoose.connect(fallbackUri, buildMongoOptions());
        mongoMode = "memory-fallback";
        if (lastAtlasReport) {
          lastAtlasReport.selectedMode = "memory-fallback";
        }
        void ensureCriticalIndexes();
        return mongoose.connection;
      } catch (fallbackError) {
        mongoMode = "disconnected";
        logger.error("In-memory MongoDB fallback failed", {
          ...getErrorSummary(fallbackError),
        });
      }
    } else if (env.nodeEnv !== "production") {
      logger.error("MongoDB memory fallback blocked", {
        reason: "ALLOW_MEMORY_DB_FALLBACK is not true",
        action: "Set ALLOW_MEMORY_DB_FALLBACK=true only for local development, or fix Atlas connectivity.",
      });
    }

    throw error;
  }
};

const getDatabaseStatus = () => ({
  readyState: mongoose.connection.readyState,
  state: mongooseReadyStates[mongoose.connection.readyState] || "unknown",
  connected: mongoose.connection.readyState === 1,
  mode: mongoose.connection.readyState === 1 ? mongoMode : "disconnected",
  mongoMode: mongoose.connection.readyState === 1 ? mongoMode : "disconnected",
  fallbackAllowed: env.allowMemoryDbFallback,
  lastAtlasFailure,
  atlasReport: lastAtlasReport,
});

const isDatabaseReady = () => mongoose.connection.readyState === 1;

const isPrimaryDatabaseReady = () => isDatabaseReady() && mongoMode === "atlas";

const isMemoryFallbackReady = () =>
  env.nodeEnv !== "production" && env.allowMemoryDbFallback && isDatabaseReady() && mongoMode === "memory-fallback";

const closeDatabaseConnection = async () => {
  if (mongoose.connection.readyState === 0) {
    if (memoryMongoServer) {
      await memoryMongoServer.stop();
      memoryMongoServer = null;
    }
    mongoMode = "disconnected";
    return;
  }

  await mongoose.connection.close(false);

  if (memoryMongoServer) {
    await memoryMongoServer.stop();
    memoryMongoServer = null;
  }
  mongoMode = "disconnected";
};

module.exports = {
  connectDatabase,
  buildMongoOptions,
  getDatabaseStatus,
  isDatabaseReady,
  isPrimaryDatabaseReady,
  isMemoryFallbackReady,
  closeDatabaseConnection,
  ensureCriticalIndexes,
};
