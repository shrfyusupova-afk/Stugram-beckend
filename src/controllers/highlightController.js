const { sendResponse } = require("../utils/apiResponse");
const catchAsync = require("../utils/catchAsync");
const highlightService = require("../services/highlightService");

const getMyHighlights = catchAsync(async (req, res) => {
  const result = await highlightService.getMyHighlights(req.user.id);
  sendResponse(res, { message: "Your highlights fetched successfully", data: result });
});

const getHighlightsByUsername = catchAsync(async (req, res) => {
  const result = await highlightService.getHighlightsByUsername(req.user?.id, req.params.username);
  sendResponse(res, { message: "Highlights fetched successfully", data: result });
});

const createHighlight = catchAsync(async (req, res) => {
  const result = await highlightService.createHighlight(req.user.id, req.body);
  sendResponse(res, {
    statusCode: 201,
    message: "Highlight created successfully",
    data: result,
  });
});

const updateHighlight = catchAsync(async (req, res) => {
  const result = await highlightService.updateHighlight(req.user.id, req.params.id, req.body);
  sendResponse(res, { message: "Highlight updated successfully", data: result });
});

const deleteHighlight = catchAsync(async (req, res) => {
  const result = await highlightService.deleteHighlight(req.user.id, req.params.id);
  sendResponse(res, { message: "Highlight deleted successfully", data: result });
});

const addStoryToHighlight = catchAsync(async (req, res) => {
  const result = await highlightService.addStoryToHighlight(req.user.id, req.params.id, req.body);
  sendResponse(res, { message: "Story added to highlight successfully", data: result });
});

const removeStoryFromHighlight = catchAsync(async (req, res) => {
  const result = await highlightService.removeStoryFromHighlight(req.user.id, req.params.id, req.params.storyId);
  sendResponse(res, { message: "Story removed from highlight successfully", data: result });
});

module.exports = {
  getMyHighlights,
  getHighlightsByUsername,
  createHighlight,
  updateHighlight,
  deleteHighlight,
  addStoryToHighlight,
  removeStoryFromHighlight,
};
