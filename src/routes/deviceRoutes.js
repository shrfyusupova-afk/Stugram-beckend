const express = require("express");

const deviceController = require("../controllers/deviceController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const { registerPushTokenSchema, deletePushTokenSchema } = require("../validators/deviceValidators");

const router = express.Router();

router.post("/push-token", requireAuth, validate(registerPushTokenSchema), deviceController.registerPushToken);
router.delete("/push-token", requireAuth, validate(deletePushTokenSchema), deviceController.deletePushToken);

module.exports = router;
