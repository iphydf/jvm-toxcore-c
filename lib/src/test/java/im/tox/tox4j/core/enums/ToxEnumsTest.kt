package im.tox.tox4j.core.enums

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the order of every Kotlin enum the JNI bridge marshals. The generated
 * `Enum::ordinal<Tox_*>` / `Enum::valueOf<Tox_*>` switch statements on the C++
 * side map specific Kotlin ordinals to specific C `TOX_*` constants. If a
 * Kotlin enum is reordered or a value is dropped, the mapping breaks
 * silently — bytes that mean "X" on one side end up meaning "Y" on the other.
 * Asserting the value list per enum turns any drift into a Kotlin-level
 * test failure.
 *
 * `ToxFileKind` is intentionally absent: it is a plain `object` with `Int`
 * constants, not a JVM enum, so it is checked via assertions on the constant
 * values themselves.
 */
class ToxEnumsTest {
    private inline fun <reified T : Enum<T>> assertValues(vararg expected: T) {
        val actual = enumValues<T>().toList()
        assertEquals(expected.toList(), actual)
    }

    @Test
    fun toxConferenceType() =
        assertValues(
            ToxConferenceType.TEXT,
            ToxConferenceType.AV,
        )

    @Test
    fun toxConnection() =
        assertValues(
            ToxConnection.NONE,
            ToxConnection.TCP,
            ToxConnection.UDP,
        )

    @Test
    fun toxFileControl() =
        assertValues(
            ToxFileControl.RESUME,
            ToxFileControl.PAUSE,
            ToxFileControl.CANCEL,
        )

    @Test
    fun toxGroupExitType() =
        assertValues(
            ToxGroupExitType.QUIT,
            ToxGroupExitType.TIMEOUT,
            ToxGroupExitType.DISCONNECTED,
            ToxGroupExitType.SELF_DISCONNECTED,
            ToxGroupExitType.KICK,
            ToxGroupExitType.SYNC_ERROR,
        )

    @Test
    fun toxGroupJoinFail() =
        assertValues(
            ToxGroupJoinFail.PEER_LIMIT,
            ToxGroupJoinFail.INVALID_PASSWORD,
            ToxGroupJoinFail.UNKNOWN,
        )

    @Test
    fun toxGroupModEvent() =
        assertValues(
            ToxGroupModEvent.KICK,
            ToxGroupModEvent.OBSERVER,
            ToxGroupModEvent.USER,
            ToxGroupModEvent.MODERATOR,
        )

    @Test
    fun toxGroupPrivacyState() =
        assertValues(
            ToxGroupPrivacyState.PUBLIC,
            ToxGroupPrivacyState.PRIVATE,
        )

    @Test
    fun toxGroupRole() =
        assertValues(
            ToxGroupRole.FOUNDER,
            ToxGroupRole.MODERATOR,
            ToxGroupRole.USER,
            ToxGroupRole.OBSERVER,
        )

    @Test
    fun toxGroupTopicLock() =
        assertValues(
            ToxGroupTopicLock.ENABLED,
            ToxGroupTopicLock.DISABLED,
        )

    @Test
    fun toxGroupVoiceState() =
        assertValues(
            ToxGroupVoiceState.ALL,
            ToxGroupVoiceState.MODERATOR,
            ToxGroupVoiceState.FOUNDER,
        )

    @Test
    fun toxMessageType() =
        assertValues(
            ToxMessageType.NORMAL,
            ToxMessageType.ACTION,
        )

    @Test
    fun toxProxyType() =
        assertValues(
            ToxProxyType.NONE,
            ToxProxyType.HTTP,
            ToxProxyType.SOCKS5,
        )

    @Test
    fun toxSavedataType() =
        assertValues(
            ToxSavedataType.NONE,
            ToxSavedataType.TOX_SAVE,
            ToxSavedataType.SECRET_KEY,
        )

    @Test
    fun toxUserStatus() =
        assertValues(
            ToxUserStatus.NONE,
            ToxUserStatus.AWAY,
            ToxUserStatus.BUSY,
        )

    @Test
    fun toxFileKindData() = assertEquals(0, ToxFileKind.DATA)

    @Test
    fun toxFileKindAvatar() = assertEquals(1, ToxFileKind.AVATAR)
}
