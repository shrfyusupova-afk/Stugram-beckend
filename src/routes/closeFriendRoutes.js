const express = require("express");

const closeFriendController = require("../controllers/closeFriendController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const { closeFriendUserIdParamSchema, closeFriendsListQuerySchema } = require("../validators/closeFriendValidators");

const router = express.Router();

router.get("/me", requireAuth, validate(closeFriendsListQuerySchema), closeFriendController.getCloseFriends);
router.post("/:userId", requireAuth, validate(closeFriendUserIdParamSchema), closeFriendController.addCloseFriend);
router.delete("/:userId", requireAuth, validate(closeFriendUserIdParamSchema), closeFriendController.removeCloseFriend);

module.exports = router;
