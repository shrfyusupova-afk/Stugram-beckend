const express = require("express");

const adminController = require("../controllers/adminController");
const recommendationQueueController = require("../controllers/recommendationQueueController");
const supportController = require("../controllers/supportController");
const { requireAuth } = require("../middlewares/auth");
const { requireRole } = require("../middlewares/requireRole");
const validate = require("../middlewares/validate");
const {
  listReportsSchema,
  reportIdParamSchema,
  reviewReportSchema,
  moderateUserSchema,
  replayDeadLetterSchema,
  replayDeadLettersSchema,
  replayAuditHistorySchema,
  testPushSchema,
} = require("../validators/adminValidators");
const {
  adminSupportTicketsListQuerySchema,
  supportTicketIdParamSchema,
  adminSupportTicketStatusSchema,
  adminSupportTicketAssignSchema,
  adminSupportTicketNoteSchema,
} = require("../validators/supportValidators");

const router = express.Router();
const requireModeratorOrAdmin = requireRole("admin", "moderator");

router.use(requireAuth);

router.get("/reports", requireModeratorOrAdmin, validate(listReportsSchema), adminController.listReports);
router.get("/support/tickets", requireModeratorOrAdmin, validate(adminSupportTicketsListQuerySchema), supportController.getAdminSupportTickets);
router.get("/support/tickets/:ticketId", requireModeratorOrAdmin, validate(supportTicketIdParamSchema), supportController.getAdminSupportTicketDetail);
router.patch(
  "/support/tickets/:ticketId/status",
  requireModeratorOrAdmin,
  validate(adminSupportTicketStatusSchema),
  supportController.updateAdminSupportTicketStatus
);
router.patch(
  "/support/tickets/:ticketId/assign",
  requireRole("admin"),
  validate(adminSupportTicketAssignSchema),
  supportController.assignAdminSupportTicket
);
router.post(
  "/support/tickets/:ticketId/notes",
  requireModeratorOrAdmin,
  validate(adminSupportTicketNoteSchema),
  supportController.addAdminSupportTicketNote
);
router.get("/queues/recommendations/health", requireModeratorOrAdmin, recommendationQueueController.getRefreshQueueHealth);
router.get("/queues/recommendations/metrics", requireRole("admin"), recommendationQueueController.getRefreshQueuePrometheusMetrics);
router.get(
  "/queues/recommendations/dead-letters/:deadLetterId",
  requireRole("admin"),
  validate(replayDeadLetterSchema),
  recommendationQueueController.getDeadLetterDetail
);
router.post(
  "/queues/recommendations/dead-letters/:deadLetterId/replay",
  requireRole("admin"),
  validate(replayDeadLetterSchema),
  recommendationQueueController.replayDeadLetter
);
router.post(
  "/queues/recommendations/dead-letters/replay",
  requireRole("admin"),
  validate(replayDeadLettersSchema),
  recommendationQueueController.replayDeadLetters
);
router.get(
  "/queues/recommendations/replay-audits",
  requireRole("admin"),
  validate(replayAuditHistorySchema),
  recommendationQueueController.getReplayAuditHistory
);
router.get("/reports/:reportId", requireModeratorOrAdmin, validate(reportIdParamSchema), adminController.getReport);
router.patch("/reports/:reportId", requireModeratorOrAdmin, validate(reviewReportSchema), adminController.reviewReport);
router.post("/users/:userId/suspend", requireModeratorOrAdmin, validate(moderateUserSchema), adminController.suspendUser);
router.post("/users/:userId/ban", requireRole("admin"), validate(moderateUserSchema), adminController.banUser);
router.post("/test/push", requireRole("admin"), validate(testPushSchema), adminController.testPush);

module.exports = router;
