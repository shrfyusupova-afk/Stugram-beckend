const reportManagementService = require("../../admin/services/reportManagementService");

const createReport = async (reporterId, payload, meta = {}) => reportManagementService.createReport(reporterId, payload, meta);

module.exports = {
  createReport,
};
