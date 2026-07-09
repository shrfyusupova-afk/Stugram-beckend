const { isRecommendationEnabled } = require("../config/featureFlags");
const { requireFeatureEnabled } = require("./featureGate");

const requireRecommendationEnabled = requireFeatureEnabled({
  isEnabled: isRecommendationEnabled,
  feature: "recommendations",
  errorCode: "FEATURE_RECOMMENDATIONS_DISABLED",
});

module.exports = {
  requireRecommendationEnabled,
};
