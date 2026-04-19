package com.stugram.app.core.nativemedia

import java.nio.ByteBuffer

internal object NativeMediaBridge {
    init {
        System.loadLibrary("native_media")
    }

    external fun createWaveformProcessor(barCount: Int, smoothing: Float): Long
    external fun releaseWaveformProcessor(handle: Long)
    external fun resetWaveformProcessor(handle: Long)
    external fun pushAmplitudeSample(handle: Long, amplitude: Int): FloatArray

    external fun initCallCore(): Long
    external fun releaseCallCore(handle: Long)
    external fun processAudioFrame(
        handle: Long,
        pcmBuffer: ByteBuffer,
        sampleRateHz: Int,
        channelCount: Int
    ): FloatArray
}
