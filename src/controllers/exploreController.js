const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const exploreService = require("../services/exploreService");

const getTrendingExplore = catchAsync(async (req, res) => {
  const result = await exploreService.getTrendingExplore(req.query);
  sendResponse(res, {
    message: "Trending explore fetched successfully",
    data: result,
  });
});

const getCreatorsDiscovery = catchAsync(async (req, res) => {
  const result = await exploreService.getCreatorsDiscovery(req.user.id, req.query);
  sendResponse(res, {
    message: "Creators discovery fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

module.exports = {
  getTrendingExplore,
  getCreatorsDiscovery,
};
