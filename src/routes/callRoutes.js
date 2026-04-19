const express = require("express");

const callController = require("../controllers/callController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const { callHistoryQuerySchema, callIdParamSchema } = require("../validators/callValidators");

const router = express.Router();

router.get("/history", requireAuth, validate(callHistoryQuerySchema), callController.getCallHistory);
router.get("/:callId", requireAuth, validate(callIdParamSchema), callController.getCallDetail);

module.exports = router;
