const express = require("express");

const activityController = require("../controllers/activityController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const { activityQuerySchema } = require("../validators/activityValidators");

const router = express.Router();

router.get("/me", requireAuth, validate(activityQuerySchema), activityController.getMyActivity);

module.exports = router;
