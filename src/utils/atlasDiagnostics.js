const dns = require("dns").promises;
const tls = require("tls");

const DEFAULT_MONGODB_PORT = 27017;

const sanitizeMongoUri = (uri = "") =>
  String(uri)
    .replace(/\/\/([^:/@]+):([^@]+)@/, "//***:***@")
    .replace(/\/\/([^@/]+)@/, "//***@");

const parseMongoUri = (uri = "") => {
  try {
    const parsed = new URL(uri);
    const dbName = parsed.pathname?.replace(/^\//, "") || null;
    const query = Object.fromEntries(parsed.searchParams.entries());

    return {
      valid: true,
      scheme: parsed.protocol.replace(":", ""),
      isSrv: parsed.protocol === "mongodb+srv:",
      host: parsed.hostname,
      port: parsed.port ? Number(parsed.port) : null,
      dbName,
      hasUsername: Boolean(parsed.username),
      hasPassword: Boolean(parsed.password),
      query,
      sanitizedUri: sanitizeMongoUri(uri),
      rawUsernameLength: parsed.username ? decodeURIComponent(parsed.username).length : 0,
      rawPasswordLength: parsed.password ? decodeURIComponent(parsed.password).length : 0,
    };
  } catch (error) {
    return {
      valid: false,
      sanitizedUri: sanitizeMongoUri(uri),
      error: {
        name: error?.name || "Error",
        message: error?.message || "Invalid MongoDB URI",
      },
    };
  }
};

const summarizeDnsRecords = (records = []) =>
  records.map((record) => ({
    name: record.name,
    port: record.port,
    priority: record.priority,
    weight: record.weight,
  }));

const getErrorSummary = (error) => ({
  name: error?.name || "Error",
  message: error?.message || "Unknown error",
  code: error?.code || null,
  codeName: error?.codeName || null,
  syscall: error?.syscall || null,
  hostname: error?.hostname || null,
  reason: error?.reason?.type || error?.reason?.message || null,
});

const testTlsHandshake = (host, port = DEFAULT_MONGODB_PORT, timeoutMs = 5000) =>
  new Promise((resolve) => {
    const startedAt = Date.now();
    const socket = tls.connect({
      host,
      port,
      servername: host,
      timeout: timeoutMs,
    });

    const finish = (result) => {
      socket.destroy();
      resolve({
        host,
        port,
        durationMs: Date.now() - startedAt,
        ...result,
      });
    };

    socket.once("secureConnect", () => {
      finish({
        ok: true,
        authorized: socket.authorized,
        authorizationError: socket.authorizationError || null,
        protocol: socket.getProtocol?.() || null,
        cipher: socket.getCipher?.()?.name || null,
      });
    });

    socket.once("timeout", () => {
      finish({
        ok: false,
        error: {
          name: "TimeoutError",
          message: `TLS handshake timed out after ${timeoutMs}ms`,
        },
      });
    });

    socket.once("error", (error) => {
      finish({
        ok: false,
        error: getErrorSummary(error),
      });
    });
  });

const classifyMongoConnectionFailure = (error) => {
  const message = String(error?.message || "");
  const reason = String(error?.reason?.type || error?.reason?.message || "");
  const combined = `${message} ${reason}`.toLowerCase();

  if (combined.includes("authentication failed") || combined.includes("bad auth") || combined.includes("auth failed")) {
    return "bad_credentials_or_database_access";
  }

  if (combined.includes("querysrv") || combined.includes("enotfound") || combined.includes("srv") || combined.includes("dns")) {
    return "dns_srv_resolution";
  }

  if (combined.includes("tls") || combined.includes("ssl") || combined.includes("handshake") || combined.includes("alert internal error")) {
    return "tls_handshake";
  }

  if (combined.includes("ip") && combined.includes("whitelist")) {
    return "ip_whitelist_or_network_access";
  }

  if (combined.includes("replicasetnoprimary") || combined.includes("server selection") || combined.includes("timed out")) {
    return "cluster_unavailable_or_network_blocked";
  }

  if (combined.includes("econnrefused") || combined.includes("etimedout") || combined.includes("ehostunreach")) {
    return "network_unreachable";
  }

  return "unknown";
};

const classifyMongoConnectionFailureWithDiagnostics = (error, diagnostics = null) => {
  const diagnosticsDns = diagnostics?.dns || {};
  const diagnosticsTls = diagnostics?.tls || {};
  const tlsResults = Array.isArray(diagnosticsTls.results) ? diagnosticsTls.results : [];
  const tlsMessages = tlsResults
    .map((result) => `${result?.error?.message || ""} ${result?.error?.code || ""}`.toLowerCase())
    .join(" ");
  const tlsFailed = tlsResults.length > 0 && tlsResults.every((result) => !result.ok);
  const dnsError = diagnosticsDns.error || diagnosticsDns.txtError || null;
  const dnsFailed = diagnosticsDns.srvLookupAttempted && diagnosticsDns.srvResolved === false;

  const message = String(error?.message || "");
  const reason = String(error?.reason?.type || error?.reason?.message || "");
  const combined = `${message} ${reason} ${tlsMessages}`.toLowerCase();

  if (combined.includes("authentication failed") || combined.includes("bad auth") || combined.includes("auth failed")) {
    return "bad_credentials_or_database_access";
  }

  if (dnsFailed || (dnsError && combined.includes("srv"))) {
    return "dns_srv_resolution";
  }

  if (
    combined.includes("tls") ||
    combined.includes("ssl") ||
    combined.includes("handshake") ||
    combined.includes("alert internal error") ||
    (tlsFailed && combined.includes("ssl"))
  ) {
    return "tls_handshake";
  }

  if (combined.includes("ip") && combined.includes("whitelist")) {
    return "ip_whitelist_or_network_access";
  }

  if (
    combined.includes("econnrefused") ||
    combined.includes("etimedout") ||
    combined.includes("ehostunreach") ||
    combined.includes("network is unreachable")
  ) {
    return "network_unreachable";
  }

  if (combined.includes("replicasetnoprimary") || combined.includes("server selection") || combined.includes("timed out")) {
    return "cluster_unavailable_or_network_blocked";
  }

  if (tlsFailed && tlsResults.some((result) => String(result?.error?.code || "").includes("ERR_SSL"))) {
    return "tls_handshake";
  }

  if (dnsError) {
    return "dns_srv_resolution";
  }

  return classifyMongoConnectionFailure(error);
};

const runAtlasConnectivityDiagnostics = async (uri, options = {}) => {
  const parsed = parseMongoUri(uri);
  const report = {
    uri: parsed,
    node: {
      version: process.version,
      openssl: process.versions.openssl || null,
    },
    connection: {
      uriAttempted: false,
      connected: false,
      pingSucceeded: false,
      error: null,
      classification: null,
    },
    dns: {
      srvLookupAttempted: false,
      srvResolved: false,
      txtResolved: false,
      resolvedHosts: [],
      records: [],
      txtRecords: [],
      error: null,
      txtError: null,
      serversUsed: dns.getServers(),
    },
    tls: {
      attempted: false,
      results: [],
    },
  };

  if (!parsed.valid || !parsed.host) {
    return report;
  }

  if (parsed.isSrv) {
    const srvName = `_mongodb._tcp.${parsed.host}`;
    report.dns.srvLookupAttempted = true;

    try {
      const records = await dns.resolveSrv(srvName);
      report.dns.srvResolved = records.length > 0;
      report.dns.records = summarizeDnsRecords(records);
      report.dns.resolvedHosts = records.map((record) => ({
        host: record.name,
        port: record.port,
      }));
    } catch (error) {
      report.dns.error = getErrorSummary(error);
    }

    try {
      const txtRecords = await dns.resolveTxt(parsed.host);
      report.dns.txtResolved = true;
      report.dns.txtRecords = txtRecords.map((item) => item.join(""));
    } catch (error) {
      report.dns.txtError = getErrorSummary(error);
    }
  }

  const tlsHosts = parsed.isSrv
    ? report.dns.records.slice(0, options.maxTlsHosts || 2).map((record) => ({ host: record.name, port: record.port }))
    : [{ host: parsed.host, port: parsed.port || DEFAULT_MONGODB_PORT }];

  if (tlsHosts.length) {
    report.tls.attempted = true;
    report.tls.results = await Promise.all(
      tlsHosts.map((target) => testTlsHandshake(target.host, target.port, options.tlsTimeoutMs || 5000))
    );
  }

  return report;
};

module.exports = {
  classifyMongoConnectionFailure,
  classifyMongoConnectionFailureWithDiagnostics,
  getErrorSummary,
  parseMongoUri,
  runAtlasConnectivityDiagnostics,
  sanitizeMongoUri,
};
