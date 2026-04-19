const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const activityService = require("../services/activityService");

const getMyActivity = catchAsync(async (req, res) => {
  const result = await activityService.getMyActivity(req.user.id, req.query);
  sendResponse(res, {
    message: "Activity fetched successfully",
    data: {
      recentActivity: result.items,
      summary: result.summary,
    },
    meta: result.meta,
  });
});

module.exports = {
  getMyActivity,
};
