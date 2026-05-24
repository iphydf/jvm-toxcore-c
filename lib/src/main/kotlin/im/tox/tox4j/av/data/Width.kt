package im.tox.tox4j.av.data

import kotlin.jvm.JvmInline

@JvmInline
value class Width(
    val value: Int,
) {
    init {
        // Anything else would be truncated to uint16_t.
        require(value in 0..MAX) { "Width must fit in uint16_t (0..$MAX), got $value" }
    }

    companion object {
        const val MAX: Int = 0xFFFF

        /** Clamps to `[0, MAX]`, for values from outside the program. */
        fun fromInt(value: Int): Width = Width(value.coerceIn(0, MAX))
    }
}
