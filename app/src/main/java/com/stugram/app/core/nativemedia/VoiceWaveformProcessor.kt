package com.stugram.app.core.nativemedia

import java.io.Closeable

class VoiceWaveformProcessor(
    private val barCount: Int = 18,
    private val smoothing: Float = 0.82f
) : Closeable {
    private val handle: Long = NativeMediaBridge.createWaveformProcessor(barCount, smoothing)

    val levelsCount: Int
        get() = barCount

    fun reset() {
        if (handle != 0L) {
            NativeMediaBridge.resetWaveformProcessor(handle)
        }
    }

    fun pushAmplitudeSample(amplitude: Int): FloatArray {
        if (handle == 0L) return FloatArray(barCount) { 0.12f }
        return NativeMediaBridge.pushAmplitudeSample(handle, amplitude)
    }

    override fun close() {
        if (handle != 0L) {
            NativeMediaBridge.releaseWaveformProcessor(handle)
        }
    }
}
