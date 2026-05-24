package im.tox.tox4j.av.enums

import kotlin.test.Test
import kotlin.test.assertEquals

/** The ToxAV side of `ToxEnumsTest`. */
class ToxAvEnumsTest {
    private inline fun <reified T : Enum<T>> assertValues(vararg expected: T) {
        val actual = enumValues<T>().toList()
        assertEquals(expected.toList(), actual)
    }

    @Test
    fun toxavCallControl() =
        assertValues(
            ToxavCallControl.RESUME,
            ToxavCallControl.PAUSE,
            ToxavCallControl.CANCEL,
            ToxavCallControl.MUTE_AUDIO,
            ToxavCallControl.UNMUTE_AUDIO,
            ToxavCallControl.HIDE_VIDEO,
            ToxavCallControl.SHOW_VIDEO,
        )

    // Bit flags with the C values, so a change here breaks the C ABI.
    @Test
    fun toxavFriendCallState() {
        assertEquals(0, ToxavFriendCallState.NONE)
        assertEquals(1, ToxavFriendCallState.ERROR)
        assertEquals(2, ToxavFriendCallState.FINISHED)
        assertEquals(4, ToxavFriendCallState.SENDING_A)
        assertEquals(8, ToxavFriendCallState.SENDING_V)
        assertEquals(16, ToxavFriendCallState.ACCEPTING_A)
        assertEquals(32, ToxavFriendCallState.ACCEPTING_V)
    }
}
