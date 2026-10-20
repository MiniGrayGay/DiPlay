package com.shilapi.xcertplay.compat

import android.media.MediaCodec
import android.os.Build
import java.nio.ByteBuffer

/**
 * Pre-API 21 [MediaCodec] used [MediaCodec.getInputBuffers]/[MediaCodec.getOutputBuffers] arrays.
 * API 21 introduced the per-index [MediaCodec.getInputBuffer]/[MediaCodec.getOutputBuffer] calls.
 * This helper picks the right path at runtime so callers stay simple.
 */
internal object MediaCodecCompat {

    fun getInputBuffer(codec: MediaCodec, index: Int): ByteBuffer? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            codec.getInputBuffer(index)
        } else {
            @Suppress("DEPRECATION")
            codec.inputBuffers.getOrNull(index)
        }

    fun getOutputBuffer(codec: MediaCodec, index: Int): ByteBuffer? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            codec.getOutputBuffer(index)
        } else {
            @Suppress("DEPRECATION")
            codec.outputBuffers.getOrNull(index)
        }
}
