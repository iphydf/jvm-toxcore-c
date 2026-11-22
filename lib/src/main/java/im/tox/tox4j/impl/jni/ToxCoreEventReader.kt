package im.tox.tox4j.impl.jni

import im.tox.tox4j.core.ToxCoreEvent
import im.tox.tox4j.core.data.ToxConferenceNumber
import im.tox.tox4j.core.data.ToxConferencePeerNumber
import im.tox.tox4j.core.data.ToxFileNumber
import im.tox.tox4j.core.data.ToxFriendMessageId
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.core.data.ToxGroupMessageId
import im.tox.tox4j.core.data.ToxGroupNumber
import im.tox.tox4j.core.data.ToxGroupPeerNumber
import im.tox.tox4j.core.data.ToxPublicKey
import im.tox.tox4j.core.enums.ToxConferenceType
import im.tox.tox4j.core.enums.ToxConnection
import im.tox.tox4j.core.enums.ToxFileControl
import im.tox.tox4j.core.enums.ToxGroupExitType
import im.tox.tox4j.core.enums.ToxGroupJoinFail
import im.tox.tox4j.core.enums.ToxGroupModEvent
import im.tox.tox4j.core.enums.ToxGroupPrivacyState
import im.tox.tox4j.core.enums.ToxGroupTopicLock
import im.tox.tox4j.core.enums.ToxGroupVoiceState
import im.tox.tox4j.core.enums.ToxMessageType
import im.tox.tox4j.core.enums.ToxUserStatus
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads what the C++ callbacks wrote: each event's tag, then its fields. */
internal object ToxCoreEventReader {
    fun read(bytes: ByteArray?): List<ToxCoreEvent> {
        if (bytes == null) return emptyList()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        return buildList {
            while (buf.hasRemaining()) add(event(buf))
        }
    }

    private fun event(buf: ByteBuffer): ToxCoreEvent =
        when (val tag = buf.get().toInt()) {
            0 -> {
                ToxCoreEvent.ConferenceConnected(ToxConferenceNumber(buf.int))
            }

            1 -> {
                ToxCoreEvent.ConferenceInvite(ToxFriendNumber(buf.int), ToxConferenceType.entries.atOrFirst(buf.int), buf.bytes())
            }

            2 -> {
                ToxCoreEvent.ConferenceMessage(
                    ToxConferenceNumber(buf.int),
                    ToxConferencePeerNumber(buf.int),
                    ToxMessageType.entries.atOrFirst(buf.int),
                    buf.bytes(),
                )
            }

            3 -> {
                ToxCoreEvent.ConferencePeerListChanged(ToxConferenceNumber(buf.int))
            }

            4 -> {
                ToxCoreEvent.ConferencePeerName(ToxConferenceNumber(buf.int), ToxConferencePeerNumber(buf.int), buf.bytes())
            }

            5 -> {
                ToxCoreEvent.ConferenceTitle(ToxConferenceNumber(buf.int), ToxConferencePeerNumber(buf.int), buf.bytes())
            }

            6 -> {
                ToxCoreEvent.FileChunkRequest(ToxFriendNumber(buf.int), ToxFileNumber(buf.int), buf.long, buf.long)
            }

            7 -> {
                ToxCoreEvent.FileRecv(ToxFriendNumber(buf.int), ToxFileNumber(buf.int), buf.int, buf.long, buf.bytes())
            }

            8 -> {
                ToxCoreEvent.FileRecvChunk(ToxFriendNumber(buf.int), ToxFileNumber(buf.int), buf.long, buf.bytes())
            }

            9 -> {
                ToxCoreEvent.FileRecvControl(ToxFriendNumber(buf.int), ToxFileNumber(buf.int), ToxFileControl.entries.atOrFirst(buf.int))
            }

            10 -> {
                ToxCoreEvent.FriendConnectionStatus(ToxFriendNumber(buf.int), ToxConnection.entries.atOrFirst(buf.int))
            }

            11 -> {
                ToxCoreEvent.FriendLosslessPacket(ToxFriendNumber(buf.int), buf.bytes())
            }

            12 -> {
                ToxCoreEvent.FriendLossyPacket(ToxFriendNumber(buf.int), buf.bytes())
            }

            13 -> {
                ToxCoreEvent.FriendMessage(ToxFriendNumber(buf.int), ToxMessageType.entries.atOrFirst(buf.int), buf.bytes())
            }

            14 -> {
                ToxCoreEvent.FriendName(ToxFriendNumber(buf.int), buf.bytes())
            }

            15 -> {
                ToxCoreEvent.FriendReadReceipt(ToxFriendNumber(buf.int), ToxFriendMessageId(buf.int))
            }

            16 -> {
                ToxCoreEvent.FriendRequest(ToxPublicKey(buf.bytes()), buf.bytes())
            }

            17 -> {
                ToxCoreEvent.FriendStatus(ToxFriendNumber(buf.int), ToxUserStatus.entries.atOrFirst(buf.int))
            }

            18 -> {
                ToxCoreEvent.FriendStatusMessage(ToxFriendNumber(buf.int), buf.bytes())
            }

            19 -> {
                ToxCoreEvent.FriendTyping(ToxFriendNumber(buf.int), buf.bool())
            }

            20 -> {
                ToxCoreEvent.GroupCustomPacket(ToxGroupNumber(buf.int), ToxGroupPeerNumber(buf.int), buf.bytes())
            }

            21 -> {
                ToxCoreEvent.GroupCustomPrivatePacket(ToxGroupNumber(buf.int), ToxGroupPeerNumber(buf.int), buf.bytes())
            }

            22 -> {
                ToxCoreEvent.GroupInvite(ToxFriendNumber(buf.int), buf.bytes(), buf.bytes())
            }

            23 -> {
                ToxCoreEvent.GroupJoinFail(ToxGroupNumber(buf.int), ToxGroupJoinFail.entries.atOrFirst(buf.int))
            }

            24 -> {
                ToxCoreEvent.GroupMessage(
                    ToxGroupNumber(buf.int),
                    ToxGroupPeerNumber(buf.int),
                    ToxMessageType.entries.atOrFirst(buf.int),
                    buf.bytes(),
                    ToxGroupMessageId(buf.int),
                )
            }

            25 -> {
                ToxCoreEvent.GroupModeration(
                    ToxGroupNumber(buf.int),
                    ToxGroupPeerNumber(buf.int),
                    ToxGroupPeerNumber(buf.int),
                    ToxGroupModEvent.entries.atOrFirst(buf.int),
                )
            }

            26 -> {
                ToxCoreEvent.GroupPassword(ToxGroupNumber(buf.int), buf.bytes())
            }

            27 -> {
                ToxCoreEvent.GroupPeerExit(
                    ToxGroupNumber(buf.int),
                    ToxGroupPeerNumber(buf.int),
                    ToxGroupExitType.entries.atOrFirst(buf.int),
                    buf.bytes(),
                    buf.bytes(),
                )
            }

            28 -> {
                ToxCoreEvent.GroupPeerJoin(ToxGroupNumber(buf.int), ToxGroupPeerNumber(buf.int))
            }

            29 -> {
                ToxCoreEvent.GroupPeerLimit(ToxGroupNumber(buf.int), buf.int)
            }

            30 -> {
                ToxCoreEvent.GroupPeerName(ToxGroupNumber(buf.int), ToxGroupPeerNumber(buf.int), buf.bytes())
            }

            31 -> {
                ToxCoreEvent.GroupPeerStatus(ToxGroupNumber(buf.int), ToxGroupPeerNumber(buf.int), ToxUserStatus.entries.atOrFirst(buf.int))
            }

            32 -> {
                ToxCoreEvent.GroupPrivacyState(ToxGroupNumber(buf.int), ToxGroupPrivacyState.entries.atOrFirst(buf.int))
            }

            33 -> {
                ToxCoreEvent.GroupPrivateMessage(
                    ToxGroupNumber(buf.int),
                    ToxGroupPeerNumber(buf.int),
                    ToxMessageType.entries.atOrFirst(buf.int),
                    buf.bytes(),
                    ToxGroupMessageId(buf.int),
                )
            }

            34 -> {
                ToxCoreEvent.GroupSelfJoin(ToxGroupNumber(buf.int))
            }

            35 -> {
                ToxCoreEvent.GroupTopic(ToxGroupNumber(buf.int), ToxGroupPeerNumber(buf.int), buf.bytes())
            }

            36 -> {
                ToxCoreEvent.GroupTopicLock(ToxGroupNumber(buf.int), ToxGroupTopicLock.entries.atOrFirst(buf.int))
            }

            37 -> {
                ToxCoreEvent.GroupVoiceState(ToxGroupNumber(buf.int), ToxGroupVoiceState.entries.atOrFirst(buf.int))
            }

            38 -> {
                ToxCoreEvent.SelfConnectionStatus(ToxConnection.entries.atOrFirst(buf.int))
            }

            else -> {
                error("Unknown event $tag")
            }
        }
}

private fun ByteBuffer.bool(): Boolean = get() != 0.toByte()

private fun ByteBuffer.bytes(): ByteArray = ByteArray(int).also(this::get)

// A newer C library can send values we don't know.
private fun <T> List<T>.atOrFirst(index: Int): T = getOrNull(index) ?: first()
