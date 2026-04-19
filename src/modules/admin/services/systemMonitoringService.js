const os = require("os");

const recommendationRefreshService = require("../../../services/recommendationRefreshService");

const getSystemHealth = async () => {
  const recommendationHealth = await recommendationRefreshService.getRecommendationRefreshQueueHealth();

  return {
    serverUptimeSeconds: Math.round(process.uptime()),
    memoryUsage: process.memoryUsage(),
    cpuUsage: process.cpuUsage(),
    loadAverage: os.loadavg(),
    queueHealthSummary: recommendationHealth.queue.counts,
    recommendationHealthSummary: {
      worker: recommendationHealth.worker,
      deadLetterCount: recommendationHealth.metrics.global.deadLetterCount,
      averageProcessingLatencyMs: recommendationHealth.metrics.global.averageProcessingLatencyMs,
    },
  };
};

module.exports = {
  getSystemHealth,
};
