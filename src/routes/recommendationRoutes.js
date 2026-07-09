const express = require("express");

const recommendationController = require("../controllers/recommendationController");
const { requireAuth } = require("../middlewares/auth");
const { requireRecommendationEnabled } = require("../middlewares/recommendationSecurity");
const validate = require("../middlewares/validate");
const {
  feedQuerySchema,
  impressionSchema,
  watchProgressSchema,
  notInterestedSchema,
  onboardingTopicsSchema,
} = require("../validators/recommendationValidators");

const router = express.Router();

router.get("/feed/summary", requireAuth, requireRecommendationEnabled, recommendationController.getFeedSummary);
router.get("/feed/me", requireAuth, requireRecommendationEnabled, validate(feedQuerySchema), recommendationController.getMyFeed);
router.get("/reels/me", requireAuth, requireRecommendationEnabled, validate(feedQuerySchema), recommendationController.getMyReels);

router.post("/interactions/impression", requireAuth, requireRecommendationEnabled, validate(impressionSchema), recommendationController.trackImpression);
router.post("/interactions/watch-progress", requireAuth, requireRecommendationEnabled, validate(watchProgressSchema), recommendationController.trackWatchProgress);
router.post("/interactions/not-interested", requireAuth, requireRecommendationEnabled, validate(notInterestedSchema), recommendationController.markNotInterested);
router.post("/interactions/onboarding-topics", requireAuth, requireRecommendationEnabled, validate(onboardingTopicsSchema), recommendationController.seedOnboardingTopics);

module.exports = router;
