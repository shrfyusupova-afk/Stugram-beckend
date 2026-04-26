const crypto = require("crypto");

const IdempotencyRecord = require("../models/IdempotencyRecord");
const logger = require("../utils/logger");

const HEADER_NAME = "idempotency-key";
const RETENTION_MS = 24 * 60 * 60 * 1000;

const hashValue = (value) => crypto.createHash("sha256").update(value).digest("hex");

const normalizeKey = (value) => {
  if (typeof value !== "string") return "";
  return value.trim().slice(0, 160);
};

const getRoutePath = (req) => (req.originalUrl || req.path || "").split("?")[0];

const idempotency = async (req, res, next) => {
  const rawKey = normalizeKey(req.get(HEADER_NAME));
  if (!rawKey || !req.user?.id) return next();

  const method = req.method.toUpperCase();
  const path = getRoutePath(req);
  const keyHash = hashValue([req.user.id.toString(), method, path, rawKey].join(":"));

  let ownsRecord = false;
  let record = null;

  try {
    record = await IdempotencyRecord.create({
      keyHash,
      user: req.user.id,
      method,
      path,
      status: "processing",
      expiresAt: new Date(Date.now() + RETENTION_MS),
    });
    ownsRecord = true;
  } catch (error) {
    if (error?.code !== 11000) return next(error);
    record = await IdempotencyRecord.findOne({ keyHash }).lean();
  }

  if (!ownsRecord && record?.status === "completed" && record.responseBody) {
    res.setHeader("X-Idempotent-Replay", "true");
    return res.status(record.responseStatus || 200).json(record.responseBody);
  }

  if (!ownsRecord) {
    res.setHeader("Retry-After", "2");
    return res.status(409).json({
      success: false,
      message: "Request is still processing. Please retry shortly.",
      data: null,
      meta: null,
    });
  }

  const originalJson = res.json.bind(res);
  res.json = (body) => {
    const statusCode = res.statusCode || 200;
    if (statusCode < 500) {
      const replayBody = JSON.parse(JSON.stringify(body));
      return IdempotencyRecord.findOneAndUpdate(
        { keyHash },
        {
          status: "completed",
          responseStatus: statusCode,
          responseBody: replayBody,
          expiresAt: new Date(Date.now() + RETENTION_MS),
        }
      )
        .catch((error) => {
          logger.warn("idempotency_record_persist_failed", {
            requestId: req.requestId || null,
            userId: req.user?.id || null,
            method,
            path,
            errorName: error.name,
            message: error.message,
          });
        })
        .then(() => originalJson(body));
    }
    return originalJson(body);
  };

  res.on("finish", () => {
    if (res.statusCode >= 500) {
      IdempotencyRecord.deleteOne({ keyHash }).catch(() => null);
    }
  });

  return next();
};

module.exports = idempotency;
