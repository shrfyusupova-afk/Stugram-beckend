const express = require("express");

const settingsController = require("../controllers/settingsController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const {
  settingsUpdateSchema,
  notificationSettingsSchema,
  hiddenWordsSettingsSchema,
} = require("../validators/settingsValidators");

const router = express.Router();

router.get("/me", requireAuth, settingsController.getSettings);
router.patch("/me", requireAuth, validate(settingsUpdateSchema), settingsController.updateSettings);
router.get("/me/notifications", requireAuth, settingsController.getNotificationSettings);
router.patch(
  "/me/notifications",
  requireAuth,
  validate(notificationSettingsSchema),
  settingsController.updateNotificationSettings
);
router.get("/me/hidden-words", requireAuth, settingsController.getHiddenWordsSettings);
router.patch("/me/hidden-words", requireAuth, validate(hiddenWordsSettingsSchema), settingsController.updateHiddenWordsSettings);

module.exports = router;
