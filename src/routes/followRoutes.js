const express = require("express");

const followController = require("../controllers/followController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const { targetUserSchema, followRequestDecisionSchema, followListSchema } = require("../validators/followValidators");
const { paginationQuerySchema } = require("../validators/commonValidators");

const router = express.Router();

router.post("/:userId", requireAuth, validate(targetUserSchema), followController.followUser);
router.delete("/:userId", requireAuth, validate(targetUserSchema), followController.unfollowUser);
router.delete("/followers/:userId", requireAuth, validate(targetUserSchema), followController.removeFollower);
router.get("/requests/me", requireAuth, validate({ query: paginationQuerySchema }), followController.getFollowRequests);
router.post("/requests/:requestId/accept", requireAuth, validate(followRequestDecisionSchema), followController.acceptFollowRequest);
router.post("/requests/:requestId/reject", requireAuth, validate(followRequestDecisionSchema), followController.rejectFollowRequest);
router.get("/:username/followers", validate(followListSchema), followController.getFollowers);
router.get("/:username/following", validate(followListSchema), followController.getFollowing);

module.exports = router;
