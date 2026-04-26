const express = require("express");

const highlightController = require("../controllers/highlightController");
const { requireAuth, optionalAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const {
  usernameParamSchema,
  highlightIdParamSchema,
  highlightCreateSchema,
  highlightUpdateSchema,
  highlightAddStorySchema,
  highlightStoryParamSchema,
} = require("../validators/highlightValidators");

const router = express.Router();

router.get("/me", requireAuth, highlightController.getMyHighlights);
router.get("/:username", optionalAuth, validate(usernameParamSchema), highlightController.getHighlightsByUsername);
router.post("/", requireAuth, validate(highlightCreateSchema), highlightController.createHighlight);
router.patch("/:id", requireAuth, validate(highlightIdParamSchema), validate(highlightUpdateSchema), highlightController.updateHighlight);
router.delete("/:id", requireAuth, validate(highlightIdParamSchema), highlightController.deleteHighlight);
router.post("/:id/stories", requireAuth, validate(highlightIdParamSchema), validate(highlightAddStorySchema), highlightController.addStoryToHighlight);
router.delete("/:id/stories/:storyId", requireAuth, validate(highlightStoryParamSchema), highlightController.removeStoryFromHighlight);

module.exports = router;
