#include <jni.h>
#include <vector>
#include <array>
#include <cstddef>
#include <cstdint>
#include <cstring>

namespace native_media::audio {
    class WaveformProcessor;
    WaveformProcessor* createWaveformProcessor(int barCount, float smoothing);
    void destroyWaveformProcessor(WaveformProcessor* processor);
    void resetWaveformProcessor(WaveformProcessor* processor);
    std::vector<float> pushAmplitudeSample(WaveformProcessor* processor, int amplitude);
}

namespace native_media::call {
    class CallCore;
    CallCore* createCallCore();
    void destroyCallCore(CallCore* core);
    std::array<float, 2> processAudioFrame(CallCore* core, const int16_t* samples, size_t sampleCount, int sampleRateHz, int channelCount);
    void resetCallCore(CallCore* core);
}

namespace {

template <typename T>
T* handleToPtr(jlong handle) {
    return reinterpret_cast<T*>(handle);
}

template <typename T>
jlong ptrToHandle(T* ptr) {
    return reinterpret_cast<jlong>(ptr);
}

jfloatArray toFloatArray(JNIEnv* env, const std::vector<float>& values) {
    jfloatArray array = env->NewFloatArray(static_cast<jsize>(values.size()));
    if (array == nullptr) {
        return nullptr;
    }
    if (!values.empty()) {
        env->SetFloatArrayRegion(array, 0, static_cast<jsize>(values.size()), values.data());
    }
    return array;
}

jfloatArray toFloatArray(JNIEnv* env, const std::array<float, 2>& values) {
    jfloatArray array = env->NewFloatArray(2);
    if (array == nullptr) {
        return nullptr;
    }
    env->SetFloatArrayRegion(array, 0, 2, values.data());
    return array;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_stugram_app_core_nativemedia_NativeMediaBridge_createWaveformProcessor(
        JNIEnv*,
        jobject,
        jint barCount,
        jfloat smoothing) {
    return ptrToHandle(native_media::audio::createWaveformProcessor(static_cast<int>(barCount), static_cast<float>(smoothing)));
}

extern "C" JNIEXPORT void JNICALL
Java_com_stugram_app_core_nativemedia_NativeMediaBridge_releaseWaveformProcessor(
        JNIEnv*,
        jobject,
        jlong handle) {
    native_media::audio::destroyWaveformProcessor(handleToPtr<native_media::audio::WaveformProcessor>(handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_stugram_app_core_nativemedia_NativeMediaBridge_resetWaveformProcessor(
        JNIEnv*,
        jobject,
        jlong handle) {
    native_media::audio::resetWaveformProcessor(handleToPtr<native_media::audio::WaveformProcessor>(handle));
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_stugram_app_core_nativemedia_NativeMediaBridge_pushAmplitudeSample(
        JNIEnv* env,
        jobject,
        jlong handle,
        jint amplitude) {
    auto values = native_media::audio::pushAmplitudeSample(handleToPtr<native_media::audio::WaveformProcessor>(handle), static_cast<int>(amplitude));
    return toFloatArray(env, values);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_stugram_app_core_nativemedia_NativeMediaBridge_initCallCore(
        JNIEnv*,
        jobject) {
    return ptrToHandle(native_media::call::createCallCore());
}

extern "C" JNIEXPORT void JNICALL
Java_com_stugram_app_core_nativemedia_NativeMediaBridge_releaseCallCore(
        JNIEnv*,
        jobject,
        jlong handle) {
    native_media::call::destroyCallCore(handleToPtr<native_media::call::CallCore>(handle));
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_stugram_app_core_nativemedia_NativeMediaBridge_processAudioFrame(
        JNIEnv* env,
        jobject,
        jlong handle,
        jobject pcmBuffer,
        jint sampleRateHz,
        jint channelCount) {
    if (pcmBuffer == nullptr) {
        return toFloatArray(env, std::array<float, 2>{0.0f, 0.0f});
    }

    auto* buffer = static_cast<int16_t*>(env->GetDirectBufferAddress(pcmBuffer));
    const jlong capacity = env->GetDirectBufferCapacity(pcmBuffer);
    if (buffer == nullptr || capacity <= 1) {
        return toFloatArray(env, std::array<float, 2>{0.0f, 0.0f});
    }

    const size_t sampleCount = static_cast<size_t>(capacity / static_cast<jlong>(sizeof(int16_t)));
    auto values = native_media::call::processAudioFrame(
            handleToPtr<native_media::call::CallCore>(handle),
            buffer,
            sampleCount,
            static_cast<int>(sampleRateHz),
            static_cast<int>(channelCount));
    return toFloatArray(env, values);
}
