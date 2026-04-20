#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

namespace native_media::audio {

class WaveformProcessor {
public:
    WaveformProcessor(int barCount, float smoothing)
        : barCount_(std::max(8, barCount)),
          smoothing_(std::clamp(smoothing, 0.05f, 0.95f)),
          history_(static_cast<size_t>(std::max(64, barCount_ * 8)), 0.0f),
          levels_(static_cast<size_t>(barCount_), 0.12f) {}

    void reset() {
        std::fill(history_.begin(), history_.end(), 0.0f);
        std::fill(levels_.begin(), levels_.end(), 0.12f);
        historyHead_ = 0;
        historySize_ = 0;
        lastEnvelope_ = 0.12f;
    }

    std::vector<float> pushAmplitudeSample(int amplitude) {
        const float normalized = normalize(amplitude);
        lastEnvelope_ = (lastEnvelope_ * smoothing_) + (normalized * (1.0f - smoothing_));
        pushHistory(lastEnvelope_);
        rebuildLevels();
        return levels_;
    }

private:
    int barCount_;
    float smoothing_;
    std::vector<float> history_;
    std::vector<float> levels_;
    size_t historyHead_ = 0;
    size_t historySize_ = 0;
    float lastEnvelope_ = 0.12f;

    static float normalize(int amplitude) {
        const float clamped = std::max(0, std::min(amplitude, 32767));
        const float scaled = std::log1p(clamped) / std::log1p(32767.0f);
        return std::clamp(scaled, 0.0f, 1.0f);
    }

    void pushHistory(float value) {
        history_[historyHead_] = value;
        historyHead_ = (historyHead_ + 1) % history_.size();
        historySize_ = std::min(historySize_ + 1, history_.size());
    }

    float sampleAtAge(size_t age) const {
        if (historySize_ == 0) {
            return 0.12f;
        }
        const size_t oldestIndex = (historyHead_ + history_.size() - historySize_) % history_.size();
        return history_[(oldestIndex + age) % history_.size()];
    }

    void rebuildLevels() {
        if (historySize_ == 0) {
            return;
        }

        const size_t visibleHistory = std::min(historySize_, static_cast<size_t>(barCount_ * 4));
        const float segment = static_cast<float>(visibleHistory) / static_cast<float>(barCount_);

        for (int i = 0; i < barCount_; ++i) {
            const size_t startAge = static_cast<size_t>(std::floor(segment * i));
            const size_t endAge = std::max(startAge + 1, static_cast<size_t>(std::ceil(segment * (i + 1))));
            const size_t clampedEnd = std::min(endAge, historySize_);
            float sum = 0.0f;
            size_t count = 0;
            for (size_t age = startAge; age < clampedEnd; ++age) {
                sum += sampleAtAge(historySize_ - visibleHistory + age);
                ++count;
            }
            const float averaged = count > 0 ? (sum / static_cast<float>(count)) : sampleAtAge(historySize_ - 1);
            const float neighborBlend = 0.88f;
            levels_[static_cast<size_t>(i)] =
                    std::clamp((levels_[static_cast<size_t>(i)] * neighborBlend) + (averaged * (1.0f - neighborBlend)), 0.08f, 1.0f);
        }

        for (int i = 1; i < barCount_ - 1; ++i) {
            const float smoothed = (levels_[static_cast<size_t>(i - 1)] * 0.18f) +
                                   (levels_[static_cast<size_t>(i)] * 0.64f) +
                                   (levels_[static_cast<size_t>(i + 1)] * 0.18f);
            levels_[static_cast<size_t>(i)] = smoothed;
        }
    }
};

WaveformProcessor* createWaveformProcessor(int barCount, float smoothing) {
    return new WaveformProcessor(barCount, smoothing);
}

void destroyWaveformProcessor(WaveformProcessor* processor) {
    delete processor;
}

void resetWaveformProcessor(WaveformProcessor* processor) {
    if (processor != nullptr) {
        processor->reset();
    }
}

std::vector<float> pushAmplitudeSample(WaveformProcessor* processor, int amplitude) {
    if (processor == nullptr) {
        return std::vector<float>(18, 0.12f);
    }
    return processor->pushAmplitudeSample(amplitude);
}

} // namespace native_media::audio
