const express = require("express");

const requestController = require("../controllers/requestController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const { requestIdParamSchema, listRequestsSchema } = require("../validators/requestValidators");

const router = express.Router();

router.get("/", requireAuth, validate(listRequestsSchema), requestController.listRequests);
router.post("/:requestId/add-to-chat", requireAuth, validate(requestIdParamSchema), requestController.addToChat);
router.post("/:requestId/block", requireAuth, validate(requestIdParamSchema), requestController.blockRequestUser);
router.delete("/:requestId", requireAuth, validate(requestIdParamSchema), requestController.deleteRequest);

module.exports = router;
