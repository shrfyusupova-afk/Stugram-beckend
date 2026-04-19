const catchAsync = require("../utils/catchAsync");
const { sendResponse } = require("../utils/apiResponse");
const recommendationRefreshService = require("../services/recommendationRefreshService");

const getRefreshQueueHealth = catchAsync(async (_req, res) => {
  const result = await recommendationRefreshService.getRecommendationRefreshQueueHealth();

  sendResponse(res, {
    message: "Recommendation refresh queue health fetched successfully",
    data: result,
  });
});

const getDeadLetterDetail = catchAsync(async (req, res) => {
  const result = await recommendationRefreshService.getDeadLetterDetail(req.params.deadLetterId);

  sendResponse(res, {
    message: "Recommendation dead-letter detail fetched successfully",
    data: result,
  });
});

const replayDeadLetter = catchAsync(async (req, res) => {
  const result = await recommendationRefreshService.replayDeadLetterById({
    deadLetterId: req.params.deadLetterId,
    actorId: req.user.id,
  });

  sendResponse(res, {
    message: "Recommendation dead-letter replay processed",
    data: result,
  });
});

const replayDeadLetters = catchAsync(async (req, res) => {
  const result = await recommendationRefreshService.replayDeadLetters({
    surface: req.body?.surface || null,
    jobName: req.body?.jobName || null,
    limit: req.body?.limit || 20,
    actorId: req.user.id,
  });

  sendResponse(res, {
    message: "Recommendation dead-letter bulk replay processed",
    data: result,
  });
});

const getReplayAuditHistory = catchAsync(async (req, res) => {
  const result = await recommendationRefreshService.getReplayAuditHistory(req.query);

  sendResponse(res, {
    message: "Recommendation replay audit history fetched successfully",
    data: result.items,
    meta: result.meta,
  });
});

const getRefreshQueuePrometheusMetrics = catchAsync(async (_req, res) => {
  const metrics = await recommendationRefreshService.getRecommendationRefreshPrometheusMetrics();
  res.set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
  res.status(200).send(metrics);
});

module.exports = {
  getDeadLetterDetail,
  getRefreshQueueHealth,
  getRefreshQueuePrometheusMetrics,
  getReplayAuditHistory,
  replayDeadLetter,
  replayDeadLetters,
};
