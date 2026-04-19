const express = require("express");

const storyController = require("../controllers/storyController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const { uploadStoryMedia } = require("../middlewares/upload");
const {
  createStorySchema,
  storyIdParamSchema,
  storyViewersSchema,
  storyInsightsSchema,
  storyCommentsSchema,
  storyCommentCreateSchema,
  storyCommentDeleteSchema,
  storyReplySchema,
  userStoriesSchema,
  storiesFeedSchema,
} = require("../validators/storyValidators");

const router = express.Router();

router.post("/", requireAuth, uploadStoryMedia, validate(createStorySchema), storyController.createStory);
router.get("/feed/me", requireAuth, validate(storiesFeedSchema), storyController.getStoriesFeed);
router.get("/user/:username", validate(userStoriesSchema), storyController.getStoriesOfUser);
router.post("/:storyId/view", requireAuth, validate(storyIdParamSchema), storyController.markStoryAsViewed);
router.patch("/:storyId/view", requireAuth, validate(storyIdParamSchema), storyController.markStoryAsViewed);
router.get("/:storyId/viewers", requireAuth, validate(storyViewersSchema), storyController.getStoryViewers);
router.get("/:storyId/insights", requireAuth, validate(storyInsightsSchema), storyController.getStoryInsights);
router.post("/:storyId/like", requireAuth, validate(storyIdParamSchema), storyController.likeStory);
router.delete("/:storyId/like", requireAuth, validate(storyIdParamSchema), storyController.unlikeStory);
router.get("/:storyId/likes", requireAuth, validate(storyViewersSchema), storyController.getStoryLikes);
router.post("/:storyId/comments", requireAuth, validate(storyCommentCreateSchema), storyController.addStoryComment);
router.get("/:storyId/comments", requireAuth, validate(storyCommentsSchema), storyController.getStoryComments);
router.get("/:storyId/replies", requireAuth, validate(storyCommentsSchema), storyController.getStoryReplies);
router.post("/:storyId/reply", requireAuth, validate(storyReplySchema), storyController.replyToStory);
router.delete(
  "/:storyId/comments/:commentId",
  requireAuth,
  validate(storyCommentDeleteSchema),
  storyController.deleteStoryComment
);
router.delete("/:storyId", requireAuth, validate(storyIdParamSchema), storyController.deleteStory);

module.exports = router;
