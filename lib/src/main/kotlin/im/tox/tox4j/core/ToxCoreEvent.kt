package im.tox.tox4j.core

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

/** What [ToxCore.iterate] reports. */
sealed interface ToxCoreEvent {
    /** @property conferenceNumber The conference number of the conference to which we have connected. */
    class ConferenceConnected(
        val conferenceNumber: ToxConferenceNumber,
    ) : ToxCoreEvent

    /**
     * The invitation will remain valid until the inviting friend goes offline or exits the conference.
     *
     * @property friendNumber The friend who invited us.
     * @property type The conference type (text only or audio/video).
     * @property cookie A piece of data of variable length required to join the conference.
     */
    class ConferenceInvite(
        val friendNumber: ToxFriendNumber,
        val type: ToxConferenceType,
        val cookie: ByteArray,
    ) : ToxCoreEvent

    /**
     * @property conferenceNumber The conference number of the conference the message is intended for.
     * @property peerNumber The ID of the peer who sent the message.
     * @property type The type of message (normal, action, ...).
     * @property message The message data.
     */
    class ConferenceMessage(
        val conferenceNumber: ToxConferenceNumber,
        val peerNumber: ToxConferencePeerNumber,
        val type: ToxMessageType,
        val message: ByteArray,
    ) : ToxCoreEvent

    /** @property conferenceNumber The conference number of the conference the peer is in. */
    class ConferencePeerListChanged(
        val conferenceNumber: ToxConferenceNumber,
    ) : ToxCoreEvent

    /**
     * @property conferenceNumber The conference number of the conference the peer is in.
     * @property peerNumber The ID of the peer who changed their nickname.
     * @property name A byte array containing the new nickname.
     */
    class ConferencePeerName(
        val conferenceNumber: ToxConferenceNumber,
        val peerNumber: ToxConferencePeerNumber,
        val name: ByteArray,
    ) : ToxCoreEvent

    /**
     * @property conferenceNumber The conference number of the conference the title change is intended for.
     * @property peerNumber The ID of the peer who changed the title.
     * @property title The title data.
     */
    class ConferenceTitle(
        val conferenceNumber: ToxConferenceNumber,
        val peerNumber: ToxConferencePeerNumber,
        val title: ByteArray,
    ) : ToxCoreEvent

    /**
     * If the length parameter is 0, the file transfer is finished, and the client's resources associated with the file number should be released. After a call with zero length, the file number can be reused for future file transfers.
     *
     * If the requested position is not equal to the client's idea of the current file or stream position, it will need to seek. In case of read-once streams, the client should keep the last read chunk so that a seek back can be supported. A seek-back only ever needs to read from the last requested chunk. This happens when a chunk was requested, but the send failed. A seek-back request can occur an arbitrary number of times for any given chunk.
     *
     * In response to receiving this callback, the client should call the function [fileSendChunk] with the requested chunk. If the number of bytes sent through that function is zero, the file transfer is assumed complete. A client must send the full length of data requested with this callback.
     *
     * @property friendNumber The friend number of the receiving friend for this file.
     * @property fileNumber The file transfer identifier returned by [fileSend].
     * @property position The file or stream position from which to continue reading.
     */
    class FileChunkRequest(
        val friendNumber: ToxFriendNumber,
        val fileNumber: ToxFileNumber,
        val position: Long,
        val length: Long,
    ) : ToxCoreEvent

    /**
     * The client should acquire resources to be associated with the file transfer. Incoming file transfers start in the PAUSED state. After this callback returns, a transfer can be rejected by sending a [ToxCoreConstants.FILE_CONTROL_CANCEL] control command before any other control commands. It can be accepted by sending [ToxCoreConstants.FILE_CONTROL_RESUME].
     *
     * @property friendNumber The friend number of the friend who is sending the file transfer request.
     * @property fileNumber The friend-specific file number the data received is associated with.
     * @property kind The meaning of the file that was sent.
     * @property fileSize Size in bytes of the file the client wants to send, [ULong.MAX_VALUE] if unknown or streaming.
     * @property filename Name of the file. Does not need to be the actual name. This name will be sent along with the file send request.
     */
    class FileRecv(
        val friendNumber: ToxFriendNumber,
        val fileNumber: ToxFileNumber,
        val kind: Int,
        val fileSize: Long,
        val filename: ByteArray,
    ) : ToxCoreEvent

    /**
     * When length is 0, the transfer is finished and the client should release the resources it acquired for the transfer. After a call with length = 0, the file number can be reused for new file transfers.
     *
     * If position is equal to file_size (received in the file_receive callback) when the transfer finishes, the file was received completely. Otherwise, if file_size was [ULong.MAX_VALUE], streaming ended successfully when length is 0.
     *
     * @property friendNumber The friend number of the friend who is sending the file.
     * @property fileNumber The friend-specific file number the data received is associated with.
     * @property position The file position of the first byte in data.
     * @property data A byte array containing the received chunk.
     */
    class FileRecvChunk(
        val friendNumber: ToxFriendNumber,
        val fileNumber: ToxFileNumber,
        val position: Long,
        val data: ByteArray,
    ) : ToxCoreEvent

    /**
     * When receiving [ToxCoreConstants.FILE_CONTROL_CANCEL], the client should release the resources associated with the file number and consider the transfer failed.
     *
     * @property friendNumber The friend number of the friend who is sending the file.
     * @property fileNumber The friend-specific file number the data received is associated with.
     * @property control The file control command received.
     */
    class FileRecvControl(
        val friendNumber: ToxFriendNumber,
        val fileNumber: ToxFileNumber,
        val control: ToxFileControl,
    ) : ToxCoreEvent

    /**
     * @property friendNumber The friend number of the friend whose connection status changed.
     * @property connectionStatus The result of calling `tox_friend_get_connection_status` on the passed friend_number.
     */
    class FriendConnectionStatus(
        val friendNumber: ToxFriendNumber,
        val connectionStatus: ToxConnection,
    ) : ToxCoreEvent

    /**
     * @property friendNumber The friend number of the friend who sent the packet.
     * @property data A byte array containing the received packet data.
     */
    class FriendLosslessPacket(
        val friendNumber: ToxFriendNumber,
        val data: ByteArray,
    ) : ToxCoreEvent

    /**
     * [callbackFriendLossyPacket] is the compatibility function to set the callback for all packet IDs except those reserved for ToxAV.
     *
     * @property friendNumber The friend number of the friend who sent a lossy packet.
     * @property data A byte array containing the received packet data.
     */
    class FriendLossyPacket(
        val friendNumber: ToxFriendNumber,
        val data: ByteArray,
    ) : ToxCoreEvent

    /**
     * @property friendNumber The friend number of the friend who sent the message.
     * @property type The type of the message (normal, action, ...).
     * @property message The message data they sent.
     */
    class FriendMessage(
        val friendNumber: ToxFriendNumber,
        val type: ToxMessageType,
        val message: ByteArray,
    ) : ToxCoreEvent

    /**
     * @property friendNumber The friend number of the friend whose name changed.
     * @property name A byte array containing the same data as `tox_friend_get_name` would write to its `name` parameter.
     */
    class FriendName(
        val friendNumber: ToxFriendNumber,
        val name: ByteArray,
    ) : ToxCoreEvent

    /**
     * @property friendNumber The friend number of the friend who received the message.
     * @property messageId The message ID as returned from [friendSendMessage] corresponding to the message sent.
     */
    class FriendReadReceipt(
        val friendNumber: ToxFriendNumber,
        val messageId: ToxFriendMessageId,
    ) : ToxCoreEvent

    /**
     * @property publicKey The Public Key of the user who sent the friend request.
     * @property message The message they sent along with the request.
     */
    class FriendRequest(
        val publicKey: ToxPublicKey,
        val message: ByteArray,
    ) : ToxCoreEvent

    /**
     * @property friendNumber The friend number of the friend whose user status changed.
     * @property status The new user status.
     */
    class FriendStatus(
        val friendNumber: ToxFriendNumber,
        val status: ToxUserStatus,
    ) : ToxCoreEvent

    /**
     * @property friendNumber The friend number of the friend whose status message changed.
     * @property message A byte array containing the same data as `tox_friend_get_status_message` would write to its `status_message` parameter.
     */
    class FriendStatusMessage(
        val friendNumber: ToxFriendNumber,
        val message: ByteArray,
    ) : ToxCoreEvent

    /**
     * @property friendNumber The friend number of the friend who started or stopped typing.
     * @property typing The result of calling [friendGetTyping] on the passed friend_number.
     */
    class FriendTyping(
        val friendNumber: ToxFriendNumber,
        val typing: Boolean,
    ) : ToxCoreEvent

    /**
     * @property groupNumber The group number of the group the packet is intended for.
     * @property peerId The ID of the peer who sent the packet.
     * @property data The packet data.
     */
    class GroupCustomPacket(
        val groupNumber: ToxGroupNumber,
        val peerId: ToxGroupPeerNumber,
        val data: ByteArray,
    ) : ToxCoreEvent

    /**
     * @property groupNumber The group number of the group the packet is intended for.
     * @property peerId The ID of the peer who sent the packet.
     * @property data The packet data.
     */
    class GroupCustomPrivatePacket(
        val groupNumber: ToxGroupNumber,
        val peerId: ToxGroupPeerNumber,
        val data: ByteArray,
    ) : ToxCoreEvent

    /**
     * @property friendNumber The friend number of the contact who sent the invite.
     * @property inviteData The invite data.
     * @property groupName The name of the group. In conferences, this is "title".
     */
    class GroupInvite(
        val friendNumber: ToxFriendNumber,
        val inviteData: ByteArray,
        val groupName: ByteArray,
    ) : ToxCoreEvent

    /**
     * @property groupNumber The group number of the group for which the join has failed.
     * @property failType The type of group rejection.
     */
    class GroupJoinFail(
        val groupNumber: ToxGroupNumber,
        val failType: ToxGroupJoinFail,
    ) : ToxCoreEvent

    /**
     * @property groupNumber The group number of the group the message is intended for.
     * @property peerId The ID of the peer who sent the message.
     * @property messageType The type of message (normal, action, ...).
     * @property message The message data.
     * @property messageId A pseudo message id that clients can use to uniquely identify this group message.
     */
    class GroupMessage(
        val groupNumber: ToxGroupNumber,
        val peerId: ToxGroupPeerNumber,
        val messageType: ToxMessageType,
        val message: ByteArray,
        val messageId: ToxGroupMessageId,
    ) : ToxCoreEvent

    /**
     * @property groupNumber The group number of the group the event is intended for.
     * @property sourcePeerId The ID of the peer who initiated the event.
     * @property targetPeerId The ID of the peer who is the target of the event.
     * @property modType The type of event.
     */
    class GroupModeration(
        val groupNumber: ToxGroupNumber,
        val sourcePeerId: ToxGroupPeerNumber,
        val targetPeerId: ToxGroupPeerNumber,
        val modType: ToxGroupModEvent,
    ) : ToxCoreEvent

    /**
     * @property groupNumber The group number of the group for which the password has changed.
     * @property password The new group password.
     */
    class GroupPassword(
        val groupNumber: ToxGroupNumber,
        val password: ByteArray,
    ) : ToxCoreEvent

    /**
     * @property groupNumber The group number of the group in which a peer has left.
     * @property peerId The ID of the peer who left the group. This ID no longer designates a valid peer and cannot be used for API calls.
     * @property exitType The type of exit event. One of Tox_Group_Exit_Type.
     * @property name The nickname of the peer who left the group.
     * @property partMessage The parting message data.
     */
    class GroupPeerExit(
        val groupNumber: ToxGroupNumber,
        val peerId: ToxGroupPeerNumber,
        val exitType: ToxGroupExitType,
        val name: ByteArray,
        val partMessage: ByteArray,
    ) : ToxCoreEvent

    /**
     * @property groupNumber The group number of the group in which a new peer has joined.
     * @property peerId The permanent ID of the new peer. This id should not be relied on for client behaviour and should be treated as a random value.
     */
    class GroupPeerJoin(
        val groupNumber: ToxGroupNumber,
        val peerId: ToxGroupPeerNumber,
    ) : ToxCoreEvent

    /**
     * @property groupNumber The group number of the group for which the peer limit has changed.
     * @property peerLimit The new peer limit for the group.
     */
    class GroupPeerLimit(
        val groupNumber: ToxGroupNumber,
        val peerLimit: Int,
    ) : ToxCoreEvent

    /**
     * @property groupNumber The group number of the group the name change is intended for.
     * @property peerId The ID of the peer who has changed their name.
     * @property name The name data.
     */
    class GroupPeerName(
        val groupNumber: ToxGroupNumber,
        val peerId: ToxGroupPeerNumber,
        val name: ByteArray,
    ) : ToxCoreEvent

    /**
     * @property groupNumber The group number of the group the status change is intended for.
     * @property peerId The ID of the peer who has changed their status.
     * @property status The new status of the peer.
     */
    class GroupPeerStatus(
        val groupNumber: ToxGroupNumber,
        val peerId: ToxGroupPeerNumber,
        val status: ToxUserStatus,
    ) : ToxCoreEvent

    /**
     * @property groupNumber The group number of the group the privacy state is intended for.
     * @property privacyState The new privacy state.
     */
    class GroupPrivacyState(
        val groupNumber: ToxGroupNumber,
        val privacyState: ToxGroupPrivacyState,
    ) : ToxCoreEvent

    /**
     * @property groupNumber The group number of the group the private message is intended for.
     * @property peerId The ID of the peer who sent the private message.
     * @property messageType The type of message (normal, action, ...).
     * @property message The message data.
     * @property messageId A pseudo message id that clients can use to uniquely identify this group message.
     */
    class GroupPrivateMessage(
        val groupNumber: ToxGroupNumber,
        val peerId: ToxGroupPeerNumber,
        val messageType: ToxMessageType,
        val message: ByteArray,
        val messageId: ToxGroupMessageId,
    ) : ToxCoreEvent

    /** @property groupNumber The group number of the group that the client has joined. */
    class GroupSelfJoin(
        val groupNumber: ToxGroupNumber,
    ) : ToxCoreEvent

    /**
     * @property groupNumber The group number of the group the topic change is intended for.
     * @property peerId The ID of the peer who changed the topic. If the peer who set the topic is not present in our peer list this value will be set to 0.
     * @property topic The topic data.
     */
    class GroupTopic(
        val groupNumber: ToxGroupNumber,
        val peerId: ToxGroupPeerNumber,
        val topic: ByteArray,
    ) : ToxCoreEvent

    /**
     * @property groupNumber The group number of the group for which the topic lock has changed.
     * @property topicLock The new topic lock state.
     */
    class GroupTopicLock(
        val groupNumber: ToxGroupNumber,
        val topicLock: ToxGroupTopicLock,
    ) : ToxCoreEvent

    /**
     * @property groupNumber The group number of the group the voice state change is intended for.
     * @property voiceState The new voice state.
     */
    class GroupVoiceState(
        val groupNumber: ToxGroupNumber,
        val voiceState: ToxGroupVoiceState,
    ) : ToxCoreEvent

    /** @property connectionStatus Whether we are connected to the DHT. */
    class SelfConnectionStatus(
        val connectionStatus: ToxConnection,
    ) : ToxCoreEvent
}
