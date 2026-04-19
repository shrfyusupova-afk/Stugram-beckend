const express = require("express");

const notificationController = require("../controllers/notificationController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const { notificationIdSchema, notificationQuerySchema } = require("../validators/notificationValidators");

const router = express.Router();

router.get("/summary", requireAuth, notificationController.getSummary);
router.get("/unread-count", requireAuth, notificationController.getUnreadCount);
router.get("/", requireAuth, validate(notificationQuerySchema), notificationController.getNotifications);
router.patch("/:notificationId/read", requireAuth, validate(notificationIdSchema), notificationController.markNotificationAsRead);
router.patch("/read-all", requireAuth, notificationController.markAllAsRead);

module.exports = router;
