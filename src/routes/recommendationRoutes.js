const express = require("express");

const recommendationController = require("../controllers/recommendationController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const {
  feedQuerySchema,
  impressionSchema,
  watchProgressSchema,
  notInterestedSchema,
  onboardingTopicsSchema,
} = require("../validators/recommendationValidators");

const router = express.Router();

router.get("/feed/summary", requireAuth, recommendationController.getFeedSummary);
router.get("/feed/me", requireAuth, validate(feedQuerySchema), recommendationController.getMyFeed);
router.get("/reels/me", requireAuth, validate(feedQuerySchema), recommendationController.getMyReels);

router.post("/interactions/impression", requireAuth, validate(impressionSchema), recommendationController.trackImpression);
router.post("/interactions/watch-progress", requireAuth, validate(watchProgressSchema), recommendationController.trackWatchProgress);
router.post("/interactions/not-interested", requireAuth, validate(notInterestedSchema), recommendationController.markNotInterested);
router.post("/interactions/onboarding-topics", requireAuth, validate(onboardingTopicsSchema), recommendationController.seedOnboardingTopics);

module.exports = router;
