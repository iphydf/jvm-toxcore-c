package im.tox.tox4j.core

import im.tox.tox4j.core.data.Port
import im.tox.tox4j.core.data.ToxConferenceMessage
import im.tox.tox4j.core.data.ToxConferenceNumber
import im.tox.tox4j.core.data.ToxDhtId
import im.tox.tox4j.core.data.ToxFileId
import im.tox.tox4j.core.data.ToxFileNumber
import im.tox.tox4j.core.data.ToxFilename
import im.tox.tox4j.core.data.ToxFriendMessage
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.core.data.ToxGroupName
import im.tox.tox4j.core.data.ToxGroupNumber
import im.tox.tox4j.core.data.ToxGroupTopic
import im.tox.tox4j.core.data.ToxName
import im.tox.tox4j.core.data.ToxStatusMessage
import im.tox.tox4j.core.enums.ToxFileControl
import im.tox.tox4j.core.enums.ToxFileKind
import im.tox.tox4j.core.enums.ToxGroupPrivacyState
import im.tox.tox4j.core.enums.ToxMessageType
import im.tox.tox4j.core.exceptions.ToxBootstrapException
import im.tox.tox4j.core.exceptions.ToxConferenceSendMessageException
import im.tox.tox4j.core.exceptions.ToxFileControlException
import im.tox.tox4j.core.exceptions.ToxFileSendException
import im.tox.tox4j.core.exceptions.ToxFriendSendMessageException
import im.tox.tox4j.core.exceptions.ToxGroupSelfQueryException
import im.tox.tox4j.core.exceptions.ToxGroupSetPrivacyStateException
import im.tox.tox4j.core.exceptions.ToxGroupStateQueryException
import im.tox.tox4j.core.exceptions.ToxGroupTopicSetException
import im.tox.tox4j.core.exceptions.ToxSetInfoException
import im.tox.tox4j.core.exceptions.ToxSetTypingException
import im.tox.tox4j.core.options.ToxOptions
import im.tox.tox4j.impl.jni.ToxCoreImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Error-path coverage for `ToxCore` methods that the API documents
 * specific failure modes for. Each test triggers exactly one error path
 * and asserts both the exception class and (where the C ABI defines a
 * specific error code) the matching `Code` value.
 *
 * Written TDD-style: every assertion here describes a documented C-API
 * behavior. If any fails or crashes, the JNI bridge is the suspect.
 */
@OptIn(kotlin.ExperimentalStdlibApi::class)
class ToxCoreErrorPathTest {
    private val options = ToxOptions(localDiscoveryEnabled = false)

    // ---- bootstrap ------------------------------------------------------

    @Test
    fun bootstrap_badPort_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxBootstrapException> {
                    tox.bootstrap(
                        "localhost",
                        Port(0.toUShort()),
                        ToxDhtId(ByteArray(ToxCoreConstants.PUBLIC_KEY_SIZE)),
                    )
                }
            assertEquals(ToxBootstrapException.Code.BAD_PORT, ex.code)
        }
    }

    @Test
    fun bootstrap_emptyHost_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxBootstrapException> {
                    tox.bootstrap(
                        "",
                        Port(33445.toUShort()),
                        ToxDhtId(ByteArray(ToxCoreConstants.PUBLIC_KEY_SIZE)),
                    )
                }
            assertEquals(ToxBootstrapException.Code.BAD_HOST, ex.code)
        }
    }

    // ---- setName / setStatusMessage too-long ----------------------------

    @Test
    fun setName_tooLong_throws() {
        ToxCoreImpl(options).use { tox ->
            val tooLong = ByteArray(ToxCoreConstants.MAX_NAME_LENGTH + 1) { 'a'.code.toByte() }
            val ex =
                assertFailsWith<ToxSetInfoException> {
                    tox.setName(ToxName(tooLong))
                }
            assertEquals(ToxSetInfoException.Code.TOO_LONG, ex.code)
        }
    }

    @Test
    fun setStatusMessage_tooLong_throws() {
        ToxCoreImpl(options).use { tox ->
            val tooLong = ByteArray(ToxCoreConstants.MAX_STATUS_MESSAGE_LENGTH + 1) { 'a'.code.toByte() }
            val ex =
                assertFailsWith<ToxSetInfoException> {
                    tox.setStatusMessage(ToxStatusMessage(tooLong))
                }
            assertEquals(ToxSetInfoException.Code.TOO_LONG, ex.code)
        }
    }

    // ---- friendSendMessage ---------------------------------------------

    @Test
    fun friendSendMessage_unknownFriend_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxFriendSendMessageException> {
                    tox.friendSendMessage(
                        ToxFriendNumber(999),
                        ToxMessageType.NORMAL,
                        ToxFriendMessage("hi".encodeToByteArray()),
                    )
                }
            assertEquals(ToxFriendSendMessageException.Code.FRIEND_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun friendSendMessage_emptyMessage_throws() {
        // Create a friend so we get past FRIEND_NOT_FOUND and exercise the
        // empty-message path. The friend isn't connected, but the empty
        // check happens before the connection check.
        ToxCoreImpl(options).use { peer ->
            ToxCoreImpl(options).use { tox ->
                val friend = tox.friendAddNorequest(peer.publicKey)
                val ex =
                    assertFailsWith<ToxFriendSendMessageException> {
                        tox.friendSendMessage(
                            friend,
                            ToxMessageType.NORMAL,
                            ToxFriendMessage(ByteArray(0)),
                        )
                    }
                assertEquals(ToxFriendSendMessageException.Code.EMPTY, ex.code)
            }
        }
    }

    @Test
    fun friendSendMessage_tooLong_throws() {
        ToxCoreImpl(options).use { peer ->
            ToxCoreImpl(options).use { tox ->
                val friend = tox.friendAddNorequest(peer.publicKey)
                val tooLong = ByteArray(ToxCoreConstants.MAX_MESSAGE_LENGTH + 1)
                val ex =
                    assertFailsWith<ToxFriendSendMessageException> {
                        tox.friendSendMessage(friend, ToxMessageType.NORMAL, ToxFriendMessage(tooLong))
                    }
                assertEquals(ToxFriendSendMessageException.Code.TOO_LONG, ex.code)
            }
        }
    }

    @Test
    fun friendSendMessage_notConnected_throws() {
        // Friend exists but isn't connected (we never iterate to bootstrap).
        ToxCoreImpl(options).use { peer ->
            ToxCoreImpl(options).use { tox ->
                val friend = tox.friendAddNorequest(peer.publicKey)
                val ex =
                    assertFailsWith<ToxFriendSendMessageException> {
                        tox.friendSendMessage(friend, ToxMessageType.NORMAL, ToxFriendMessage("hi".encodeToByteArray()))
                    }
                assertEquals(ToxFriendSendMessageException.Code.FRIEND_NOT_CONNECTED, ex.code)
            }
        }
    }

    // ---- fileSend ------------------------------------------------------

    @Test
    fun fileSend_unknownFriend_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxFileSendException> {
                    tox.fileSend(
                        ToxFriendNumber(999),
                        ToxFileKind.DATA,
                        100L,
                        ToxFileId(ByteArray(ToxCoreConstants.FILE_ID_LENGTH)),
                        ToxFilename("a.bin".encodeToByteArray()),
                    )
                }
            assertEquals(ToxFileSendException.Code.FRIEND_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun fileSend_filenameTooLong_throws() {
        ToxCoreImpl(options).use { peer ->
            ToxCoreImpl(options).use { tox ->
                val friend = tox.friendAddNorequest(peer.publicKey)
                val name = ByteArray(ToxCoreConstants.MAX_FILENAME_LENGTH + 1) { 'a'.code.toByte() }
                val ex =
                    assertFailsWith<ToxFileSendException> {
                        tox.fileSend(
                            friend,
                            ToxFileKind.DATA,
                            100L,
                            ToxFileId(ByteArray(ToxCoreConstants.FILE_ID_LENGTH)),
                            ToxFilename(name),
                        )
                    }
                assertEquals(ToxFileSendException.Code.NAME_TOO_LONG, ex.code)
            }
        }
    }

    // ---- fileControl ----------------------------------------------------

    @Test
    fun fileControl_unknownFriend_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxFileControlException> {
                    tox.fileControl(ToxFriendNumber(999), ToxFileNumber(0), ToxFileControl.RESUME)
                }
            assertEquals(ToxFileControlException.Code.FRIEND_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun fileControl_unknownFile_throws() {
        ToxCoreImpl(options).use { peer ->
            ToxCoreImpl(options).use { tox ->
                val friend = tox.friendAddNorequest(peer.publicKey)
                val ex =
                    assertFailsWith<ToxFileControlException> {
                        tox.fileControl(friend, ToxFileNumber(999), ToxFileControl.RESUME)
                    }
                // Friend not connected check happens first by the C ABI.
                assertEquals(ToxFileControlException.Code.FRIEND_NOT_CONNECTED, ex.code)
            }
        }
    }

    // ---- setTyping ------------------------------------------------------

    @Test
    fun setTyping_unknownFriend_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxSetTypingException> {
                    tox.setTyping(ToxFriendNumber(999), true)
                }
            assertEquals(ToxSetTypingException.Code.FRIEND_NOT_FOUND, ex.code)
        }
    }

    // ---- conferenceSendMessage -----------------------------------------

    @Test
    fun conferenceSendMessage_unknownConference_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxConferenceSendMessageException> {
                    tox.conferenceSendMessage(
                        ToxConferenceNumber(999),
                        ToxMessageType.NORMAL,
                        ToxConferenceMessage("hi".encodeToByteArray()),
                    )
                }
            assertEquals(ToxConferenceSendMessageException.Code.CONFERENCE_NOT_FOUND, ex.code)
        }
    }

    // ---- NGC group queries / setters on bad group number ----------------

    @Test
    fun groupSelfGetName_unknownGroup_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxGroupSelfQueryException> {
                    tox.groupSelfGetName(ToxGroupNumber(999))
                }
            assertEquals(ToxGroupSelfQueryException.Code.GROUP_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun groupGetName_unknownGroup_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxGroupStateQueryException> {
                    tox.groupGetName(ToxGroupNumber(999))
                }
            assertEquals(ToxGroupStateQueryException.Code.GROUP_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun groupSetPrivacyState_unknownGroup_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxGroupSetPrivacyStateException> {
                    tox.groupSetPrivacyState(ToxGroupNumber(999), ToxGroupPrivacyState.PUBLIC)
                }
            assertEquals(ToxGroupSetPrivacyStateException.Code.GROUP_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun groupSetTopic_unknownGroup_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxGroupTopicSetException> {
                    tox.groupSetTopic(ToxGroupNumber(999), ToxGroupTopic("x".encodeToByteArray()))
                }
            assertEquals(ToxGroupTopicSetException.Code.GROUP_NOT_FOUND, ex.code)
        }
    }

    // Sanity: a freshly-created group is reachable; the prior unknown-group
    // tests don't poison the instance.
    @Test
    fun groupSelfGetName_validGroup_succeeds() {
        ToxCoreImpl(options).use { tox ->
            val g =
                tox.groupNew(
                    ToxGroupPrivacyState.PRIVATE,
                    ToxGroupName("OkGroup".encodeToByteArray()),
                    ToxGroupName("Founder".encodeToByteArray()),
                )
            tox.groupSelfGetName(g) // must not throw or crash
        }
    }
}
