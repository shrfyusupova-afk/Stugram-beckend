package com.stugram.app.core.nativemedia

import java.io.Closeable
import java.nio.ByteBuffer

class CallCoreBridge : Closeable {
    private val handle: Long = NativeMediaBridge.initCallCore()

    fun processAudioFrame(
        pcmBuffer: ByteBuffer,
        sampleRateHz: Int,
        channelCount: Int
    ): FloatArray {
        if (handle == 0L) return floatArrayOf(0f, 0f)
        return NativeMediaBridge.processAudioFrame(handle, pcmBuffer, sampleRateHz, channelCount)
    }

    override fun close() {
        if (handle != 0L) {
            NativeMediaBridge.releaseCallCore(handle)
        }
    }
}
