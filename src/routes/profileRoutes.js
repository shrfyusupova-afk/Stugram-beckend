const express = require("express");

const profileController = require("../controllers/profileController");
const { requireAuth, optionalAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const { uploadAvatar, uploadBanner } = require("../middlewares/upload");
const {
  usernameParamSchema,
  usernameAvailabilityQuerySchema,
  updateProfileSchema,
  profileFeedSchema,
  highlightIdParamSchema,
  createHighlightSchema,
  updateHighlightSchema,
  createProfileSchema,
} = require("../validators/profileValidators");
const { feedQuerySchema } = require("../validators/recommendationValidators");

const router = express.Router();

router.get("/check-username", validate(usernameAvailabilityQuerySchema), profileController.checkUsernameAvailability);
router.get("/suggestions", requireAuth, validate(feedQuerySchema), profileController.getProfileSuggestions);
router.get("/:username/summary", optionalAuth, validate(usernameParamSchema), profileController.getProfileSummary);
router.get("/:username/reels", optionalAuth, validate(profileFeedSchema), profileController.getProfileReels);
router.get("/:username/tagged", optionalAuth, validate(profileFeedSchema), profileController.getProfileTaggedPosts);
router.get("/:username/highlights", optionalAuth, validate(usernameParamSchema), profileController.getProfileHighlights);
router.get("/me/all", requireAuth, profileController.getMyProfiles);
router.get("/me", requireAuth, profileController.getCurrentProfile);
router.post("/", requireAuth, validate(createProfileSchema), profileController.createProfile);
router.post("/me/highlights", requireAuth, validate(createHighlightSchema), profileController.createProfileHighlight);
router.patch("/me/highlights/:highlightId", requireAuth, validate(highlightIdParamSchema), validate(updateHighlightSchema), profileController.renameProfileHighlight);
router.delete("/me/highlights/:highlightId", requireAuth, validate(highlightIdParamSchema), profileController.deleteProfileHighlight);
router.get("/:username", optionalAuth, validate(usernameParamSchema), profileController.getProfile);
router.patch("/me", requireAuth, validate(updateProfileSchema), profileController.updateProfile);
router.post("/me/avatar", requireAuth, uploadAvatar, profileController.uploadAvatar);
router.post("/me/banner", requireAuth, uploadBanner, profileController.uploadBanner);

module.exports = router;
