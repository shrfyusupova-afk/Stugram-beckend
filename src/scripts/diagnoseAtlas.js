require("dotenv").config();

const mongoose = require("mongoose");
const dns = require("dns");

const { env } = require("../config/env");
const {
  classifyMongoConnectionFailure,
  classifyMongoConnectionFailureWithDiagnostics,
  getErrorSummary,
  parseMongoUri,
  runAtlasConnectivityDiagnostics,
} = require("../utils/atlasDiagnostics");

const buildDiagnosticMongoOptions = () => {
  const options = {
    autoIndex: false,
    serverSelectionTimeoutMS: env.mongoServerSelectionTimeoutMs,
    connectTimeoutMS: env.mongoConnectTimeoutMs,
    socketTimeoutMS: env.mongoSocketTimeoutMs,
    maxPoolSize: 1,
  };

  if (env.mongoForceIpv4) {
    options.family = 4;
  }

  return options;
};

const main = async () => {
  if (env.mongoDnsServers.length) {
    dns.setServers(env.mongoDnsServers);
  }

  const uri = parseMongoUri(env.mongoUri);
  const report = {
    uri,
    options: {
      serverSelectionTimeoutMS: env.mongoServerSelectionTimeoutMs,
      connectTimeoutMS: env.mongoConnectTimeoutMs,
      socketTimeoutMS: env.mongoSocketTimeoutMs,
      forceIpv4: env.mongoForceIpv4,
      dnsServers: env.mongoDnsServers.length ? env.mongoDnsServers : dns.getServers(),
    },
    diagnostics: await runAtlasConnectivityDiagnostics(env.mongoUri, {
      tlsTimeoutMs: Math.min(env.mongoConnectTimeoutMs, 7000),
      maxTlsHosts: 3,
    }),
    mongoose: {
      uriAttempted: false,
      connected: false,
      pingSucceeded: false,
      error: null,
      classification: null,
    },
  };

  try {
    await mongoose.connect(env.mongoUri, buildDiagnosticMongoOptions());
    report.mongoose.uriAttempted = true;
    report.mongoose.connected = true;
    await mongoose.connection.db.admin().ping();
    report.mongoose.pingSucceeded = true;
  } catch (error) {
    report.mongoose.error = getErrorSummary(error);
    report.mongoose.classification = classifyMongoConnectionFailureWithDiagnostics(error, report.diagnostics);
    report.mongoose.uriAttempted = true;
  } finally {
    if (mongoose.connection.readyState !== 0) {
      await mongoose.connection.close(false).catch(() => null);
    }
  }

  report.summary = {
    likelyCause: report.mongoose.classification || classifyMongoConnectionFailure(report.mongoose.error),
    atlasConnected: report.mongoose.connected,
    atlasPingSucceeded: report.mongoose.pingSucceeded,
    dnsSrvResolved: report.diagnostics.dns.srvResolved,
    txtResolved: report.diagnostics.dns.txtResolved,
    tlsAttempted: report.diagnostics.tls.attempted,
  };

  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
  process.exit(report.mongoose.connected && report.mongoose.pingSucceeded ? 0 : 2);
};

main().catch((error) => {
  process.stderr.write(
    `${JSON.stringify(
      {
        success: false,
        error: getErrorSummary(error),
      },
      null,
      2
    )}\n`
  );
  process.exit(1);
});
