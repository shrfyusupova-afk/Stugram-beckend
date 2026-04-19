const express = require("express");

const profileController = require("../controllers/profileController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const { uploadAvatar, uploadBanner } = require("../middlewares/upload");
const {
  usernameParamSchema,
  usernameAvailabilityQuerySchema,
  updateProfileSchema,
  profileFeedSchema,
  createProfileSchema,
} = require("../validators/profileValidators");
const { feedQuerySchema } = require("../validators/recommendationValidators");

const router = express.Router();

router.get("/check-username", validate(usernameAvailabilityQuerySchema), profileController.checkUsernameAvailability);
router.get("/suggestions", requireAuth, validate(feedQuerySchema), profileController.getProfileSuggestions);
router.get("/:username/summary", validate(usernameParamSchema), profileController.getProfileSummary);
router.get("/:username/reels", validate(profileFeedSchema), profileController.getProfileReels);
router.get("/:username/tagged", validate(profileFeedSchema), profileController.getProfileTaggedPosts);
router.get("/me/all", requireAuth, profileController.getMyProfiles);
router.get("/me", requireAuth, profileController.getCurrentProfile);
router.post("/", requireAuth, validate(createProfileSchema), profileController.createProfile);
router.get("/:username", validate(usernameParamSchema), profileController.getProfile);
router.patch("/me", requireAuth, validate(updateProfileSchema), profileController.updateProfile);
router.post("/me/avatar", requireAuth, uploadAvatar, profileController.uploadAvatar);
router.post("/me/banner", requireAuth, uploadBanner, profileController.uploadBanner);

module.exports = router;
