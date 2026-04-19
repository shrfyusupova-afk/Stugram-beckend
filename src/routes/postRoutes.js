const express = require("express");

const postController = require("../controllers/postController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const { uploadPostMedia } = require("../middlewares/upload");
const {
  createPostSchema,
  updatePostSchema,
  postIdParamSchema,
  userPostsSchema,
  feedQuerySchema,
  savedPostsQuerySchema,
} = require("../validators/postValidators");

const router = express.Router();

router.post("/", requireAuth, uploadPostMedia, validate(createPostSchema), postController.createPost);
router.post("/:postId/save", requireAuth, validate(postIdParamSchema), postController.savePost);
router.patch("/:postId", requireAuth, validate(updatePostSchema), postController.updatePost);
router.delete("/:postId/save", requireAuth, validate(postIdParamSchema), postController.unsavePost);
router.delete("/:postId", requireAuth, validate(postIdParamSchema), postController.deletePost);
router.get("/feed/me", requireAuth, validate(feedQuerySchema), postController.getFeed);
router.get("/saved/me", requireAuth, validate(savedPostsQuerySchema), postController.getSavedPosts);
router.get("/user/:username", validate(userPostsSchema), postController.getUserPosts);
router.get("/:postId", validate(postIdParamSchema), postController.getPost);

module.exports = router;
