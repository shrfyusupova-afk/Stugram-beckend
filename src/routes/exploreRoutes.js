const express = require("express");

const exploreController = require("../controllers/exploreController");
const { requireAuth } = require("../middlewares/auth");
const validate = require("../middlewares/validate");
const {
  trendingExploreQuerySchema,
  creatorsDiscoveryQuerySchema,
} = require("../validators/exploreValidators");

const router = express.Router();

router.get("/trending", validate(trendingExploreQuerySchema), exploreController.getTrendingExplore);
router.get("/creators", requireAuth, validate(creatorsDiscoveryQuerySchema), exploreController.getCreatorsDiscovery);

module.exports = router;
