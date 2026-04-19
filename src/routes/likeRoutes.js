const express = require("express");

const likeController = require("../controllers/likeController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const { likePostSchema, likedPostsHistorySchema } = require("../validators/likeValidators");

const router = express.Router();

router.get("/posts/me", requireAuth, validate(likedPostsHistorySchema), likeController.getLikedPostsHistory);
router.post("/posts/:postId", requireAuth, validate(likePostSchema), likeController.likePost);
router.delete("/posts/:postId", requireAuth, validate(likePostSchema), likeController.unlikePost);

module.exports = router;
