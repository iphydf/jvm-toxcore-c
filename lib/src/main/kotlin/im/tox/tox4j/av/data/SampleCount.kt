package im.tox.tox4j.av.data

import kotlin.jvm.JvmInline

@JvmInline
value class SampleCount(
    val value: Int,
) {
    init {
        // The C side types this as size_t (toxav_audio_send_frame).
        // The JNI surface widens to Long but the Kotlin wrapper holds
        // an Int; reject negatives outright (no documented use case)
        // so a `SampleCount(-1)` doesn't sneak through as a huge
        // unsigned size_t on the C side.
        require(value >= 0) { "SampleCount must be non-negative, got $value" }
    }

    constructor(
        audioLength: AudioLength,
        samplingRate: SamplingRate,
    ) : this((samplingRate.value / 1000 * audioLength.toMillis()).toInt())

    companion object {
        /**
         * Coerce a raw int into a valid `SampleCount`, clamping
         * negatives to zero. Use on the receive path (proto / IPC
         * / untrusted input) where an out-of-range value should
         * silently degrade rather than throw.
         */
        fun fromInt(value: Int): SampleCount = SampleCount(maxOf(0, value))
    }
}
