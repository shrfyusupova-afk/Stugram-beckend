const metricsStore = new Map();

const normalizeValue = (value) => {
  if (value === null || value === undefined || value === "") return "unknown";
  return String(value).replace(/[^a-zA-Z0-9:_-]/g, "_");
};

const serializeLabels = (labels = {}) =>
  Object.entries(labels)
    .filter(([, value]) => value !== null && value !== undefined)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => `${key}=${normalizeValue(value)}`)
    .join(",");

const buildMetricKey = (name, labels = {}) => `${name}|${serializeLabels(labels)}`;

const parseMetricKey = (metricKey) => {
  const [name, serializedLabels = ""] = metricKey.split("|");
  const labels = serializedLabels
    ? Object.fromEntries(
        serializedLabels.split(",").filter(Boolean).map((item) => {
          const [key, value] = item.split("=");
          return [key, value];
        })
      )
    : {};
  return { name, labels };
};

const ensureMetric = (name, labels = {}) => {
  const key = buildMetricKey(name, labels);
  if (!metricsStore.has(key)) {
    metricsStore.set(key, 0);
  }
  return key;
};

const incrementCounter = (name, labels = {}, amount = 1) => {
  const key = ensureMetric(name, labels);
  metricsStore.set(key, Number(metricsStore.get(key) || 0) + amount);
};

const setGauge = (name, labels = {}, value = 0) => {
  const key = ensureMetric(name, labels);
  metricsStore.set(key, Number(value) || 0);
};

const getMetricsSnapshot = () =>
  Array.from(metricsStore.entries()).map(([metricKey, value]) => ({
    ...parseMetricKey(metricKey),
    value,
  }));

const formatMetricLine = (name, value, labels = {}) => {
  const normalizedName = normalizeValue(name).replace(/:/g, "_");
  const labelEntries = Object.entries(labels).filter(([, labelValue]) => labelValue !== undefined && labelValue !== null);
  if (!labelEntries.length) {
    return `${normalizedName} ${value}`;
  }
  const renderedLabels = labelEntries
    .map(([key, labelValue]) => `${normalizeValue(key)}="${normalizeValue(labelValue)}"`)
    .join(",");
  return `${normalizedName}{${renderedLabels}} ${value}`;
};

const renderPrometheusMetrics = () =>
  getMetricsSnapshot()
    .map((metric) => formatMetricLine(metric.name, metric.value, metric.labels))
    .join("\n");

const resetChatMetrics = () => {
  metricsStore.clear();
};

module.exports = {
  incrementCounter,
  setGauge,
  getMetricsSnapshot,
  renderPrometheusMetrics,
  resetChatMetrics,
};
