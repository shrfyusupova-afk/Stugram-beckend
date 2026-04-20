#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <cmath>

namespace native_media::call {

class CallCore {
public:
    void reset() {
        frameCount_ = 0;
        rmsState_ = 0.0f;
        peakState_ = 0.0f;
    }

    std::array<float, 2> processAudioFrame(const int16_t* samples, size_t sampleCount, int /*sampleRateHz*/, int /*channelCount*/) {
        if (samples == nullptr || sampleCount == 0) {
            return {0.0f, 0.0f};
        }

        float peak = 0.0f;
        double sumSquares = 0.0;
        for (size_t i = 0; i < sampleCount; ++i) {
            const float normalized = static_cast<float>(samples[i]) / 32768.0f;
            const float absSample = std::abs(normalized);
            peak = std::max(peak, absSample);
            sumSquares += static_cast<double>(normalized) * static_cast<double>(normalized);
        }

        const float rms = static_cast<float>(std::sqrt(sumSquares / static_cast<double>(sampleCount)));
        rmsState_ = (rmsState_ * 0.78f) + (rms * 0.22f);
        peakState_ = std::max(peakState_ * 0.82f, peak);
        ++frameCount_;
        return {rmsState_, peakState_};
    }

private:
    std::int64_t frameCount_ = 0;
    float rmsState_ = 0.0f;
    float peakState_ = 0.0f;
};

CallCore* createCallCore() {
    return new CallCore();
}

void destroyCallCore(CallCore* core) {
    delete core;
}

std::array<float, 2> processAudioFrame(CallCore* core, const int16_t* samples, size_t sampleCount, int sampleRateHz, int channelCount) {
    if (core == nullptr) {
        return {0.0f, 0.0f};
    }
    return core->processAudioFrame(samples, sampleCount, sampleRateHz, channelCount);
}

void resetCallCore(CallCore* core) {
    if (core != nullptr) {
        core->reset();
    }
}

} // namespace native_media::call
