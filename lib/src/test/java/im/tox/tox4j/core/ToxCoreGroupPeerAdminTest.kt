package im.tox.tox4j.core

import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.core.data.ToxGroupChatId
import im.tox.tox4j.core.data.ToxGroupCustomPacket
import im.tox.tox4j.core.data.ToxGroupCustomPrivatePacket
import im.tox.tox4j.core.data.ToxGroupInviteData
import im.tox.tox4j.core.data.ToxGroupMessage
import im.tox.tox4j.core.data.ToxGroupName
import im.tox.tox4j.core.data.ToxGroupNumber
import im.tox.tox4j.core.data.ToxGroupPassword
import im.tox.tox4j.core.data.ToxGroupPeerNumber
import im.tox.tox4j.core.data.ToxGroupPrivateMessage
import im.tox.tox4j.core.enums.ToxGroupPrivacyState
import im.tox.tox4j.core.enums.ToxGroupRole
import im.tox.tox4j.core.enums.ToxMessageType
import im.tox.tox4j.core.exceptions.ToxGroupDisconnectException
import im.tox.tox4j.core.exceptions.ToxGroupInviteAcceptException
import im.tox.tox4j.core.exceptions.ToxGroupInviteFriendException
import im.tox.tox4j.core.exceptions.ToxGroupIsConnectedException
import im.tox.tox4j.core.exceptions.ToxGroupJoinException
import im.tox.tox4j.core.exceptions.ToxGroupKickPeerException
import im.tox.tox4j.core.exceptions.ToxGroupPeerQueryException
import im.tox.tox4j.core.exceptions.ToxGroupSelfQueryException
import im.tox.tox4j.core.exceptions.ToxGroupSendCustomPacketException
import im.tox.tox4j.core.exceptions.ToxGroupSendCustomPrivatePacketException
import im.tox.tox4j.core.exceptions.ToxGroupSendMessageException
import im.tox.tox4j.core.exceptions.ToxGroupSendPrivateMessageException
import im.tox.tox4j.core.exceptions.ToxGroupSetIgnoreException
import im.tox.tox4j.core.exceptions.ToxGroupSetPeerLimitException
import im.tox.tox4j.core.exceptions.ToxGroupSetRoleException
import im.tox.tox4j.core.options.ToxOptions
import im.tox.tox4j.impl.jni.ToxCoreImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Smoke coverage for the NGC group peer / admin family. A
 * single-instance group has only the local user; bogus group numbers,
 * bogus peer numbers, and self-targeting administrative actions each
 * surface as the matching `Code` on the corresponding exception. The
 * non-error paths exercise the JNI boundary; the error paths pin the
 * c-toxcore `Tox_Err_Group_*` mapping.
 *
 * Methods exercised by call-and-return tests (no peer interaction
 * needed):
 *   groupJoin, groupIsConnected, groupDisconnect, groupSelfGetPeerId,
 *   groupPeerGetName, groupPeerGetPublicKey, groupPeerGetStatus,
 *   groupPeerGetRole, groupPeerGetConnectionStatus, groupSetPeerLimit,
 *   groupSetIgnore, groupSetRole, groupKickPeer, groupSendMessage,
 *   groupSendPrivateMessage, groupSendCustomPacket,
 *   groupSendCustomPrivatePacket, groupInviteFriend, groupInviteAccept.
 */
class ToxCoreGroupPeerAdminTest {
    private val options = ToxOptions(localDiscoveryEnabled = false)

    private inline fun withGroup(block: (ToxCoreImpl, ToxGroupNumber) -> Unit) {
        ToxCoreImpl(options).use { tox ->
            val group =
                tox.groupNew(
                    ToxGroupPrivacyState.PRIVATE,
                    ToxGroupName("SmokeTest".encodeToByteArray()),
                    ToxGroupName("Founder".encodeToByteArray()),
                )
            block(tox, group)
        }
    }

    // ---- groupJoin / groupIsConnected / groupDisconnect -----------------

    @Test
    fun groupJoin_invalidChatId_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxGroupJoinException> {
                    tox.groupJoin(
                        ToxGroupChatId(ByteArray(ToxCoreConstants.GROUP_CHAT_ID_SIZE)),
                        ToxGroupName("Joiner".encodeToByteArray()),
                        ToxGroupPassword(ByteArray(0)),
                    )
                }
            // A zeroed chat ID has no matching group; CORE is c-toxcore's
            // umbrella error for the join handshake.
            assertTrue(ex.code in setOf(ToxGroupJoinException.Code.CORE, ToxGroupJoinException.Code.INIT))
        }
    }

    @Test
    fun groupIsConnected_freshGroup_isTrue() {
        withGroup { tox, group ->
            // groupNew transitions the group to a connected-to-self state
            // immediately; the boolean reports the local instance's
            // connection intent rather than peer-list status.
            assertTrue(tox.groupIsConnected(group))
        }
    }

    @Test
    fun groupIsConnected_unknownGroup_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxGroupIsConnectedException> {
                    tox.groupIsConnected(ToxGroupNumber(999))
                }
            assertEquals(ToxGroupIsConnectedException.Code.GROUP_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun groupDisconnect_unknownGroup_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxGroupDisconnectException> {
                    tox.groupDisconnect(ToxGroupNumber(999))
                }
            assertEquals(ToxGroupDisconnectException.Code.GROUP_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun groupDisconnect_validGroup_succeeds() {
        withGroup { tox, group ->
            tox.groupDisconnect(group)
        }
    }

    // ---- groupSelfGetPeerId / groupPeerGet* -----------------------------

    @Test
    fun groupSelfGetPeerId_freshGroup_returnsValid() {
        withGroup { tox, group ->
            // Self always has a valid peer id; the specific value is
            // c-toxcore's choice, just verify the call succeeds.
            tox.groupSelfGetPeerId(group)
        }
    }

    @Test
    fun groupSelfGetPeerId_unknownGroup_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxGroupSelfQueryException> {
                    tox.groupSelfGetPeerId(ToxGroupNumber(999))
                }
            assertEquals(ToxGroupSelfQueryException.Code.GROUP_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun groupPeerGetName_self_returnsFounderName() {
        withGroup { tox, group ->
            val selfId = tox.groupSelfGetPeerId(group)
            assertEquals("Founder", tox.groupPeerGetName(group, selfId).value.decodeToString())
        }
    }

    @Test
    fun groupPeerGetName_unknownPeer_throws() {
        withGroup { tox, group ->
            val ex =
                assertFailsWith<ToxGroupPeerQueryException> {
                    tox.groupPeerGetName(group, ToxGroupPeerNumber(999))
                }
            assertEquals(ToxGroupPeerQueryException.Code.PEER_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun groupPeerGetPublicKey_unknownPeer_throws() {
        withGroup { tox, group ->
            val ex =
                assertFailsWith<ToxGroupPeerQueryException> {
                    tox.groupPeerGetPublicKey(group, ToxGroupPeerNumber(999))
                }
            assertEquals(ToxGroupPeerQueryException.Code.PEER_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun groupPeerGetStatus_unknownPeer_throws() {
        withGroup { tox, group ->
            val ex =
                assertFailsWith<ToxGroupPeerQueryException> {
                    tox.groupPeerGetStatus(group, ToxGroupPeerNumber(999))
                }
            assertEquals(ToxGroupPeerQueryException.Code.PEER_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun groupPeerGetRole_unknownPeer_throws() {
        withGroup { tox, group ->
            val ex =
                assertFailsWith<ToxGroupPeerQueryException> {
                    tox.groupPeerGetRole(group, ToxGroupPeerNumber(999))
                }
            assertEquals(ToxGroupPeerQueryException.Code.PEER_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun groupPeerGetConnectionStatus_unknownPeer_throws() {
        withGroup { tox, group ->
            val ex =
                assertFailsWith<ToxGroupPeerQueryException> {
                    tox.groupPeerGetConnectionStatus(group, ToxGroupPeerNumber(999))
                }
            assertEquals(ToxGroupPeerQueryException.Code.PEER_NOT_FOUND, ex.code)
        }
    }

    // ---- groupSet* / groupKickPeer (founder-only ops, succeed on self) --

    @Test
    fun groupSetPeerLimit_validGroup_succeeds() {
        withGroup { tox, group ->
            // Founder can set the peer limit on their own group.
            tox.groupSetPeerLimit(group, 64)
        }
    }

    @Test
    fun groupSetPeerLimit_unknownGroup_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxGroupSetPeerLimitException> {
                    tox.groupSetPeerLimit(ToxGroupNumber(999), 64)
                }
            assertEquals(ToxGroupSetPeerLimitException.Code.GROUP_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun groupSetIgnore_selfPeer_throwsSelf() {
        withGroup { tox, group ->
            val selfId = tox.groupSelfGetPeerId(group)
            val ex =
                assertFailsWith<ToxGroupSetIgnoreException> {
                    tox.groupSetIgnore(group, selfId, true)
                }
            assertEquals(ToxGroupSetIgnoreException.Code.SELF, ex.code)
        }
    }

    @Test
    fun groupSetIgnore_unknownGroup_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxGroupSetIgnoreException> {
                    tox.groupSetIgnore(ToxGroupNumber(999), ToxGroupPeerNumber(0), true)
                }
            assertEquals(ToxGroupSetIgnoreException.Code.GROUP_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun groupSetRole_selfPeer_throwsSelf() {
        withGroup { tox, group ->
            val selfId = tox.groupSelfGetPeerId(group)
            val ex =
                assertFailsWith<ToxGroupSetRoleException> {
                    tox.groupSetRole(group, selfId, ToxGroupRole.USER)
                }
            assertEquals(ToxGroupSetRoleException.Code.SELF, ex.code)
        }
    }

    @Test
    fun groupSetRole_unknownGroup_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxGroupSetRoleException> {
                    tox.groupSetRole(ToxGroupNumber(999), ToxGroupPeerNumber(0), ToxGroupRole.USER)
                }
            assertEquals(ToxGroupSetRoleException.Code.GROUP_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun groupKickPeer_selfPeer_throwsSelf() {
        withGroup { tox, group ->
            val selfId = tox.groupSelfGetPeerId(group)
            val ex =
                assertFailsWith<ToxGroupKickPeerException> {
                    tox.groupKickPeer(group, selfId)
                }
            assertEquals(ToxGroupKickPeerException.Code.SELF, ex.code)
        }
    }

    @Test
    fun groupKickPeer_unknownGroup_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxGroupKickPeerException> {
                    tox.groupKickPeer(ToxGroupNumber(999), ToxGroupPeerNumber(0))
                }
            assertEquals(ToxGroupKickPeerException.Code.GROUP_NOT_FOUND, ex.code)
        }
    }

    // ---- groupSendMessage family ---------------------------------------

    @Test
    fun groupSendMessage_unknownGroup_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxGroupSendMessageException> {
                    tox.groupSendMessage(
                        ToxGroupNumber(999),
                        ToxMessageType.NORMAL,
                        ToxGroupMessage("hi".encodeToByteArray()),
                    )
                }
            assertEquals(ToxGroupSendMessageException.Code.GROUP_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun groupSendMessage_emptyMessage_throws() {
        withGroup { tox, group ->
            val ex =
                assertFailsWith<ToxGroupSendMessageException> {
                    tox.groupSendMessage(
                        group,
                        ToxMessageType.NORMAL,
                        ToxGroupMessage(ByteArray(0)),
                    )
                }
            assertEquals(ToxGroupSendMessageException.Code.EMPTY, ex.code)
        }
    }

    @Test
    fun groupSendPrivateMessage_unknownPeer_throws() {
        withGroup { tox, group ->
            val ex =
                assertFailsWith<ToxGroupSendPrivateMessageException> {
                    tox.groupSendPrivateMessage(
                        group,
                        ToxGroupPeerNumber(999),
                        ToxMessageType.NORMAL,
                        ToxGroupPrivateMessage("hi".encodeToByteArray()),
                    )
                }
            assertEquals(ToxGroupSendPrivateMessageException.Code.PEER_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun groupSendCustomPacket_unknownGroup_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxGroupSendCustomPacketException> {
                    tox.groupSendCustomPacket(ToxGroupNumber(999), true, ToxGroupCustomPacket("hi".encodeToByteArray()))
                }
            assertEquals(ToxGroupSendCustomPacketException.Code.GROUP_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun groupSendCustomPrivatePacket_unknownPeer_throws() {
        withGroup { tox, group ->
            val ex =
                assertFailsWith<ToxGroupSendCustomPrivatePacketException> {
                    tox.groupSendCustomPrivatePacket(
                        group,
                        ToxGroupPeerNumber(999),
                        true,
                        ToxGroupCustomPrivatePacket("hi".encodeToByteArray()),
                    )
                }
            assertEquals(ToxGroupSendCustomPrivatePacketException.Code.PEER_NOT_FOUND, ex.code)
        }
    }

    // ---- groupInviteFriend / groupInviteAccept --------------------------

    @Test
    fun groupInviteFriend_unknownGroup_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxGroupInviteFriendException> {
                    tox.groupInviteFriend(ToxGroupNumber(999), ToxFriendNumber(0))
                }
            assertEquals(ToxGroupInviteFriendException.Code.GROUP_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun groupInviteFriend_unknownFriend_throws() {
        withGroup { tox, group ->
            val ex =
                assertFailsWith<ToxGroupInviteFriendException> {
                    tox.groupInviteFriend(group, ToxFriendNumber(999))
                }
            assertEquals(ToxGroupInviteFriendException.Code.FRIEND_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun groupInviteAccept_badInvite_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxGroupInviteAcceptException> {
                    tox.groupInviteAccept(
                        ToxFriendNumber(0),
                        ToxGroupInviteData(ByteArray(0)),
                        ToxGroupName("Me".encodeToByteArray()),
                        ToxGroupPassword(ByteArray(0)),
                    )
                }
            // An empty invite blob is malformed: c-toxcore's
            // @gc_accept_invite@ length-checks the buffer before
            // looking up the friend, so the result is deterministically
            // BAD_INVITE regardless of whether the friend number is
            // valid.
            assertEquals(ToxGroupInviteAcceptException.Code.BAD_INVITE, ex.code)
        }
    }
}
