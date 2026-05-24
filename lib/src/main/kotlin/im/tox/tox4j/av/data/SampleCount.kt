package im.tox.tox4j.av.data

import kotlin.jvm.JvmInline

@JvmInline
value class SampleCount(
    val value: Int,
) {
    init {
        // C takes a size_t, where a negative would become huge.
        require(value >= 0) { "SampleCount must be non-negative, got $value" }
    }

    constructor(
        audioLength: AudioLength,
        samplingRate: SamplingRate,
    ) : this((samplingRate.value / 1000 * audioLength.toMillis()).toInt())

    companion object {
        /** Clamps negatives to zero, for values received rather than sent. */
        fun fromInt(value: Int): SampleCount = SampleCount(maxOf(0, value))
    }
}
