const express = require("express");

const supportController = require("../controllers/supportController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const { uploadSupportScreenshot } = require("../middlewares/upload");
const {
  createSupportTicketSchema,
  supportTicketsListQuerySchema,
  supportTicketIdParamSchema,
} = require("../validators/supportValidators");

const router = express.Router();

router.post("/problems", requireAuth, uploadSupportScreenshot, validate(createSupportTicketSchema), supportController.createSupportTicket);
router.get("/problems/me", requireAuth, validate(supportTicketsListQuerySchema), supportController.getMySupportTickets);
router.get("/problems/:ticketId", requireAuth, validate(supportTicketIdParamSchema), supportController.getSupportTicketDetail);

module.exports = router;
