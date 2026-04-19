const SENSITIVE_KEY_PATTERN = /(token|secret|password|authorization|cookie|set-cookie|mongo|redis|api[-_]?key|refresh|access)/i;

const sanitizeValue = (value, seen = new WeakSet()) => {
  if (value === null || value === undefined) return value;
  if (typeof value === "string") {
    if (value.length > 2048) {
      return `${value.slice(0, 256)}...[truncated]`;
    }
    return value;
  }

  if (typeof value !== "object") return value;
  if (seen.has(value)) return "[Circular]";
  seen.add(value);

  if (Array.isArray(value)) {
    return value.map((item) => sanitizeValue(item, seen));
  }

  return Object.fromEntries(
    Object.entries(value).map(([key, nestedValue]) => [
      key,
      SENSITIVE_KEY_PATTERN.test(key) ? "[REDACTED]" : sanitizeValue(nestedValue, seen),
    ])
  );
};

const serializeMeta = (meta) => {
  if (!meta) return "";
  if (typeof meta === "string") return meta;

  try {
    return JSON.stringify(sanitizeValue(meta));
  } catch (_error) {
    return String(meta);
  }
};

const logger = {
  info(message, meta) {
    console.log(`[INFO] ${message}`, serializeMeta(meta));
  },
  warn(message, meta) {
    console.warn(`[WARN] ${message}`, serializeMeta(meta));
  },
  error(message, meta) {
    console.error(`[ERROR] ${message}`, serializeMeta(meta));
  },
};

module.exports = logger;
