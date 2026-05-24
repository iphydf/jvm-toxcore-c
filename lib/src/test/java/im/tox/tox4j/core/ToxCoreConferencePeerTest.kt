package im.tox.tox4j.core

import im.tox.tox4j.core.data.ToxConferenceNumber
import im.tox.tox4j.core.data.ToxConferenceOfflinePeerNumber
import im.tox.tox4j.core.data.ToxConferencePeerNumber
import im.tox.tox4j.core.exceptions.ToxConferencePeerQueryException
import im.tox.tox4j.core.exceptions.ToxConferenceSetMaxOfflineException
import im.tox.tox4j.core.options.ToxOptions
import im.tox.tox4j.impl.jni.ToxCoreImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Smoke coverage for the conference peer / offline-peer query family.
 * A fresh conference has the local user as the only peer; bogus
 * conference numbers and bogus peer numbers each surface as the
 * matching `Code` on `ToxConferencePeerQueryException` /
 * `ToxConferenceSetMaxOfflineException`. The valid-call paths exercise
 * the JNI byte-array / int round-trip; the error paths pin the code
 * mapping so a future renumbering of `Tox_Err_Conference_Peer_Query`
 * won't silently slip through.
 */
class ToxCoreConferencePeerTest {
    private val options = ToxOptions(localDiscoveryEnabled = false)

    @Test
    fun peerCount_freshConference_isOne() {
        ToxCoreImpl(options).use { tox ->
            val conf = tox.conferenceNew()
            assertEquals(1, tox.conferencePeerCount(conf))
        }
    }

    @Test
    fun peerCount_unknownConference_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxConferencePeerQueryException> {
                    tox.conferencePeerCount(ToxConferenceNumber(999))
                }
            assertEquals(ToxConferencePeerQueryException.Code.CONFERENCE_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun offlinePeerCount_freshConference_isZero() {
        ToxCoreImpl(options).use { tox ->
            val conf = tox.conferenceNew()
            assertEquals(0, tox.conferenceOfflinePeerCount(conf))
        }
    }

    @Test
    fun offlinePeerCount_unknownConference_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxConferencePeerQueryException> {
                    tox.conferenceOfflinePeerCount(ToxConferenceNumber(999))
                }
            assertEquals(ToxConferencePeerQueryException.Code.CONFERENCE_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun setMaxOffline_unknownConference_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxConferenceSetMaxOfflineException> {
                    tox.conferenceSetMaxOffline(ToxConferenceNumber(999), 10)
                }
            assertEquals(ToxConferenceSetMaxOfflineException.Code.CONFERENCE_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun setMaxOffline_validConference_succeeds() {
        ToxCoreImpl(options).use { tox ->
            val conf = tox.conferenceNew()
            tox.conferenceSetMaxOffline(conf, 50)
        }
    }

    @Test
    fun peerGetName_selfPeer_returnsEmptyBeforeSet() {
        ToxCoreImpl(options).use { tox ->
            val conf = tox.conferenceNew()
            // The local user is peer 0; name is empty until set via
            // setName, and a fresh ToxCoreImpl hasn't called it. The
            // c-toxcore default friendlist entry has a zero-byte name.
            val name = tox.conferencePeerGetName(conf, ToxConferencePeerNumber(0))
            assertEquals(0, name.value.size)
        }
    }

    @Test
    fun peerGetName_unknownPeer_throws() {
        ToxCoreImpl(options).use { tox ->
            val conf = tox.conferenceNew()
            val ex =
                assertFailsWith<ToxConferencePeerQueryException> {
                    tox.conferencePeerGetName(conf, ToxConferencePeerNumber(999))
                }
            assertEquals(ToxConferencePeerQueryException.Code.PEER_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun peerGetPublicKey_selfPeer_returnsKey() {
        ToxCoreImpl(options).use { tox ->
            val conf = tox.conferenceNew()
            val key = tox.conferencePeerGetPublicKey(conf, ToxConferencePeerNumber(0))
            // Public key has a fixed size; if the call returned, the
            // wrapper's init validation already enforced it.
            assertEquals(ToxCoreConstants.PUBLIC_KEY_SIZE, key.value.size)
        }
    }

    @Test
    fun peerGetPublicKey_unknownPeer_throws() {
        ToxCoreImpl(options).use { tox ->
            val conf = tox.conferenceNew()
            val ex =
                assertFailsWith<ToxConferencePeerQueryException> {
                    tox.conferencePeerGetPublicKey(conf, ToxConferencePeerNumber(999))
                }
            assertEquals(ToxConferencePeerQueryException.Code.PEER_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun peerNumberIsOurs_selfPeer_isTrue() {
        ToxCoreImpl(options).use { tox ->
            val conf = tox.conferenceNew()
            assertTrue(tox.conferencePeerNumberIsOurs(conf, ToxConferencePeerNumber(0)))
        }
    }

    @Test
    fun peerNumberIsOurs_unknownConference_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxConferencePeerQueryException> {
                    tox.conferencePeerNumberIsOurs(ToxConferenceNumber(999), ToxConferencePeerNumber(0))
                }
            assertEquals(ToxConferencePeerQueryException.Code.CONFERENCE_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun offlinePeerGetName_unknownPeer_throws() {
        ToxCoreImpl(options).use { tox ->
            val conf = tox.conferenceNew()
            val ex =
                assertFailsWith<ToxConferencePeerQueryException> {
                    tox.conferenceOfflinePeerGetName(conf, ToxConferenceOfflinePeerNumber(0))
                }
            // Fresh conference has zero offline peers; index 0 is out of range.
            assertEquals(ToxConferencePeerQueryException.Code.PEER_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun offlinePeerGetPublicKey_unknownConference_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxConferencePeerQueryException> {
                    tox.conferenceOfflinePeerGetPublicKey(
                        ToxConferenceNumber(999),
                        ToxConferenceOfflinePeerNumber(0),
                    )
                }
            assertEquals(ToxConferencePeerQueryException.Code.CONFERENCE_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun offlinePeerGetLastActive_unknownPeer_throws() {
        ToxCoreImpl(options).use { tox ->
            val conf = tox.conferenceNew()
            val ex =
                assertFailsWith<ToxConferencePeerQueryException> {
                    tox.conferenceOfflinePeerGetLastActive(conf, ToxConferenceOfflinePeerNumber(0))
                }
            assertEquals(ToxConferencePeerQueryException.Code.PEER_NOT_FOUND, ex.code)
        }
    }
}
