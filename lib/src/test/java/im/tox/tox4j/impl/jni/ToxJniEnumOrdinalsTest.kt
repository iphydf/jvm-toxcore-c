package im.tox.tox4j.impl.jni

import im.tox.tox4j.av.enums.ToxavCallControl
import im.tox.tox4j.core.enums.ToxConferenceType
import im.tox.tox4j.core.enums.ToxConnection
import im.tox.tox4j.core.enums.ToxFileControl
import im.tox.tox4j.core.enums.ToxFileKind
import im.tox.tox4j.core.enums.ToxGroupExitType
import im.tox.tox4j.core.enums.ToxGroupJoinFail
import im.tox.tox4j.core.enums.ToxGroupModEvent
import im.tox.tox4j.core.enums.ToxGroupPrivacyState
import im.tox.tox4j.core.enums.ToxGroupRole
import im.tox.tox4j.core.enums.ToxGroupTopicLock
import im.tox.tox4j.core.enums.ToxGroupVoiceState
import im.tox.tox4j.core.enums.ToxLogLevel
import im.tox.tox4j.core.enums.ToxMessageType
import im.tox.tox4j.core.enums.ToxProxyType
import im.tox.tox4j.core.enums.ToxSavedataType
import im.tox.tox4j.core.enums.ToxUserStatus
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Pins the declaration order of every Kotlin enum that crosses the JNI
 * boundary. The C++ `Enum::valueOf<T>` helpers in
 * `cpp/{ToxCore,ToxAv}/generated/enums.cpp` map ordinals to C constants
 * via a `case 0: return ...; case 1: return ...` switch. Reordering a
 * Kotlin enum would silently translate to a different `Tox_*` constant
 * at runtime; this test makes that reordering a compile-time-style test
 * failure instead.
 *
 * Each test mirrors the corresponding `Enum::valueOf<>` switch verbatim.
 * If the C++ side ever changes, update the expected list here so the two
 * stay in lockstep.
 */
class ToxJniEnumOrdinalsTest {
    private fun <E : Enum<E>> assertOrdinalOrder(
        values: Array<E>,
        expected: List<String>,
    ) = assertContentEquals(expected, values.map { it.name })

    // ---------------------------------------------------------------
    // core enums — cpp/ToxCore/generated/enums.cpp
    // ---------------------------------------------------------------

    @Test
    fun toxConferenceType_order() = assertOrdinalOrder(ToxConferenceType.values(), listOf("TEXT", "AV"))

    @Test
    fun toxConnection_order() = assertOrdinalOrder(ToxConnection.values(), listOf("NONE", "TCP", "UDP"))

    @Test
    fun toxFileControl_order() = assertOrdinalOrder(ToxFileControl.values(), listOf("RESUME", "PAUSE", "CANCEL"))

    @Test
    fun toxFileKind_constants() {
        // ToxFileKind is an `object` with `const val`s rather than an
        // enum (it's an open set in the C API — clients can use their
        // own kind values). The values are generated from the C enum's
        // real initializers, so this is a tripwire against c-toxcore
        // renumbering, which would be an ABI break.
        assertEquals(0, ToxFileKind.DATA)
        assertEquals(1, ToxFileKind.AVATAR)
        assertEquals(2, ToxFileKind.STICKER)
        assertEquals(3, ToxFileKind.SHA1)
        assertEquals(4, ToxFileKind.SHA256)
    }

    @Test
    fun toxGroupExitType_order() =
        assertOrdinalOrder(
            ToxGroupExitType.values(),
            listOf(
                "QUIT",
                "TIMEOUT",
                "DISCONNECTED",
                "SELF_DISCONNECTED",
                "KICK",
                "SYNC_ERROR",
            ),
        )

    @Test
    fun toxGroupJoinFail_order() =
        assertOrdinalOrder(
            ToxGroupJoinFail.values(),
            listOf("PEER_LIMIT", "INVALID_PASSWORD", "UNKNOWN"),
        )

    @Test
    fun toxGroupModEvent_order() =
        assertOrdinalOrder(
            ToxGroupModEvent.values(),
            listOf("KICK", "OBSERVER", "USER", "MODERATOR"),
        )

    @Test
    fun toxGroupPrivacyState_order() = assertOrdinalOrder(ToxGroupPrivacyState.values(), listOf("PUBLIC", "PRIVATE"))

    @Test
    fun toxGroupRole_order() =
        assertOrdinalOrder(
            ToxGroupRole.values(),
            listOf("FOUNDER", "MODERATOR", "USER", "OBSERVER"),
        )

    @Test
    fun toxGroupTopicLock_order() = assertOrdinalOrder(ToxGroupTopicLock.values(), listOf("ENABLED", "DISABLED"))

    @Test
    fun toxGroupVoiceState_order() =
        assertOrdinalOrder(
            ToxGroupVoiceState.values(),
            listOf("ALL", "MODERATOR", "FOUNDER"),
        )

    @Test
    fun toxLogLevel_order() =
        assertOrdinalOrder(
            ToxLogLevel.values(),
            listOf("TRACE", "DEBUG", "INFO", "WARNING", "ERROR"),
        )

    @Test
    fun toxMessageType_order() = assertOrdinalOrder(ToxMessageType.values(), listOf("NORMAL", "ACTION"))

    @Test
    fun toxProxyType_order() = assertOrdinalOrder(ToxProxyType.values(), listOf("NONE", "HTTP", "SOCKS5"))

    @Test
    fun toxSavedataType_order() =
        assertOrdinalOrder(
            ToxSavedataType.values(),
            listOf("NONE", "TOX_SAVE", "SECRET_KEY"),
        )

    @Test
    fun toxUserStatus_order() = assertOrdinalOrder(ToxUserStatus.values(), listOf("NONE", "AWAY", "BUSY"))

    // ---------------------------------------------------------------
    // av enums — cpp/ToxAv/generated/enums.cpp
    // ---------------------------------------------------------------

    @Test
    fun toxavCallControl_order() =
        assertOrdinalOrder(
            ToxavCallControl.values(),
            listOf(
                "RESUME",
                "PAUSE",
                "CANCEL",
                "MUTE_AUDIO",
                "UNMUTE_AUDIO",
                "HIDE_VIDEO",
                "SHOW_VIDEO",
            ),
        )

    // ToxavFriendCallState is no longer a Kotlin enum: call state
    // crosses JNI as a raw uint32 bitmask and the constants object
    // carries the real C bit values (generated from the header).
    // Its values are pinned in ToxAvEnumsTest.
}
