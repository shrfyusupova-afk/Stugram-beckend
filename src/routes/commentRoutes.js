const express = require("express");

const commentController = require("../controllers/commentController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const idempotency = require("../middlewares/idempotency");
const { addCommentSchema, commentsByPostSchema, commentIdParamSchema } = require("../validators/commentValidators");

const router = express.Router();

router.post("/posts/:postId", requireAuth, validate(addCommentSchema), idempotency, commentController.addComment);
router.get("/posts/:postId", validate(commentsByPostSchema), commentController.getCommentsByPost);
router.delete("/:commentId", requireAuth, validate(commentIdParamSchema), commentController.deleteComment);

module.exports = router;
