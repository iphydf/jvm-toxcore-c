package im.tox.tox4j.av.data

import kotlin.jvm.JvmInline

@JvmInline
value class Width(
    val value: Int,
) {
    init {
        // c-toxcore types this as uint16_t, so a value outside
        // [0, MAX] would silently truncate as it crosses JNI. Reject up front.
        require(value in 0..MAX) { "Width must fit in uint16_t (0..$MAX), got $value" }
    }

    companion object {
        const val MAX: Int = 0xFFFF

        /**
         * Coerce a raw int into a valid [Width], clamping to
         * `[0, MAX]`. Use this on the receive path (proto / IPC /
         * untrusted input) where an out-of-range value should silently
         * degrade rather than throw — direct construction via
         * `Width(value)` is the right choice on the send path where
         * bad input is a programming error.
         */
        fun fromInt(value: Int): Width = Width(value.coerceIn(0, MAX))
    }
}
