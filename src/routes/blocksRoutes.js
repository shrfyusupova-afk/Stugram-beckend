const express = require("express");

const blockController = require("../controllers/blockController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const { blockedAccountsListSchema, blockedUserParamSchema } = require("../validators/blockValidators");

const router = express.Router();

router.get("/me", requireAuth, validate(blockedAccountsListSchema), blockController.getBlockedAccounts);
router.post("/:userId", requireAuth, validate(blockedUserParamSchema), blockController.blockUser);
router.delete("/:userId", requireAuth, validate(blockedUserParamSchema), blockController.unblockUser);

module.exports = router;
