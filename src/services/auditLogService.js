const AuditLog = require("../models/AuditLog");
const logger = require("../utils/logger");

const createAuditLog = async (payload) => {
  try {
    return await AuditLog.create(payload);
  } catch (error) {
    logger.error("Failed to create audit log", error.message);
    return null;
  }
};

module.exports = { createAuditLog };
