const express = require("express");

const { requireAuth } = require("../../../middlewares/auth");
const validate = require("../../../middlewares/validate");
const reportController = require("../controllers/reportController");
const { createReportSchema } = require("../validators/reportValidators");

const router = express.Router();

router.post("/", requireAuth, validate(createReportSchema), reportController.createReport);

module.exports = router;
