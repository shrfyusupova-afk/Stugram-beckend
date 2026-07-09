const crypto = require("crypto");

const REQUEST_ID_HEADER = "x-request-id";
const MAX_REQUEST_ID_LENGTH = 128;
const REQUEST_ID_PATTERN = /^[A-Za-z0-9_.:-]+$/;

const generateRequestId = () => {
  const now = Date.now().toString(36);
  const randomPart = typeof crypto.randomUUID === "function"
    ? crypto.randomUUID().replace(/-/g, "").slice(0, 12)
    : crypto.randomBytes(6).toString("hex");
  return `req_${now}_${randomPart}`;
};

const normalizeIncomingRequestId = (value) => {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  if (!trimmed) return null;
  if (trimmed.length > MAX_REQUEST_ID_LENGTH) return null;
  if (!REQUEST_ID_PATTERN.test(trimmed)) return null;
  return trimmed;
};

const assignRequestId = (req, res, next) => {
  const incoming = normalizeIncomingRequestId(req.headers[REQUEST_ID_HEADER]);
  const requestId = incoming || generateRequestId();

  req.requestId = requestId;
  req.requestStartedAt = process.hrtime.bigint();
  res.locals.requestId = requestId;
  res.setHeader(REQUEST_ID_HEADER, requestId);

  next();
};

module.exports = {
  REQUEST_ID_HEADER,
  generateRequestId,
  normalizeIncomingRequestId,
  assignRequestId,
};
