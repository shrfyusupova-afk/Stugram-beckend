const express = require("express");

const { requireAuth } = require("../../../middlewares/auth");
const { requireRole } = require("../../../middlewares/requireRole");
const validate = require("../../../middlewares/validate");
const adminPanelController = require("../controllers/adminPanelController");
const {
  adminUsersQuerySchema,
  adminUserIdParamSchema,
  adminPostsQuerySchema,
  adminPostIdParamSchema,
  adminHidePostSchema,
  adminReportsQuerySchema,
  adminReportResolveSchema,
  adminAuditLogsQuerySchema,
} = require("../validators/adminPanelValidators");

const router = express.Router();

router.use(requireAuth, requireRole("admin"));

router.get("/dashboard", adminPanelController.getDashboard);
router.get("/users", validate(adminUsersQuerySchema), adminPanelController.listUsers);
router.patch("/users/:id/ban", validate(adminUserIdParamSchema), adminPanelController.banUser);
router.patch("/users/:id/unban", validate(adminUserIdParamSchema), adminPanelController.unbanUser);
router.delete("/users/:id", validate(adminUserIdParamSchema), adminPanelController.deleteUser);

router.get("/posts", validate(adminPostsQuerySchema), adminPanelController.listPosts);
router.patch("/posts/:id/hide", validate(adminHidePostSchema), adminPanelController.hidePost);
router.delete("/posts/:id", validate(adminPostIdParamSchema), adminPanelController.deletePost);

router.get("/reports", validate(adminReportsQuerySchema), adminPanelController.listReports);
router.patch("/reports/:id/resolve", validate(adminReportResolveSchema), adminPanelController.resolveReport);

router.get("/audit-logs", validate(adminAuditLogsQuerySchema), adminPanelController.listAuditLogs);

router.get("/system/health", adminPanelController.getSystemHealth);

module.exports = router;
