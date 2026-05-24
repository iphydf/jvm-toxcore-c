package im.tox.tox4j.impl.jni

import com.google.protobuf.ByteString
import im.tox.tox4j.core.ToxCore
import im.tox.tox4j.core.callbacks.ToxCoreEventListener
import im.tox.tox4j.core.data.Port
import im.tox.tox4j.core.data.ToxAddress
import im.tox.tox4j.core.data.ToxConferenceCookie
import im.tox.tox4j.core.data.ToxConferenceId
import im.tox.tox4j.core.data.ToxConferenceMessage
import im.tox.tox4j.core.data.ToxConferenceNumber
import im.tox.tox4j.core.data.ToxConferenceOfflinePeerName
import im.tox.tox4j.core.data.ToxConferenceOfflinePeerNumber
import im.tox.tox4j.core.data.ToxConferencePeerName
import im.tox.tox4j.core.data.ToxConferencePeerNumber
import im.tox.tox4j.core.data.ToxConferenceTitle
import im.tox.tox4j.core.data.ToxDhtId
import im.tox.tox4j.core.data.ToxFileChunk
import im.tox.tox4j.core.data.ToxFileId
import im.tox.tox4j.core.data.ToxFileNumber
import im.tox.tox4j.core.data.ToxFilename
import im.tox.tox4j.core.data.ToxFriendLosslessPacket
import im.tox.tox4j.core.data.ToxFriendLossyPacket
import im.tox.tox4j.core.data.ToxFriendMessage
import im.tox.tox4j.core.data.ToxFriendMessageId
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.core.data.ToxGroupChatId
import im.tox.tox4j.core.data.ToxGroupCustomPacket
import im.tox.tox4j.core.data.ToxGroupCustomPrivatePacket
import im.tox.tox4j.core.data.ToxGroupInviteData
import im.tox.tox4j.core.data.ToxGroupMessage
import im.tox.tox4j.core.data.ToxGroupMessageId
import im.tox.tox4j.core.data.ToxGroupName
import im.tox.tox4j.core.data.ToxGroupNumber
import im.tox.tox4j.core.data.ToxGroupPartMessage
import im.tox.tox4j.core.data.ToxGroupPassword
import im.tox.tox4j.core.data.ToxGroupPeerName
import im.tox.tox4j.core.data.ToxGroupPeerNumber
import im.tox.tox4j.core.data.ToxGroupPrivateMessage
import im.tox.tox4j.core.data.ToxGroupTopic
import im.tox.tox4j.core.data.ToxName
import im.tox.tox4j.core.data.ToxPublicKey
import im.tox.tox4j.core.data.ToxSavedata
import im.tox.tox4j.core.data.ToxSecretKey
import im.tox.tox4j.core.data.ToxStatusMessage
import im.tox.tox4j.core.enums.ToxConferenceType
import im.tox.tox4j.core.enums.ToxConnection
import im.tox.tox4j.core.enums.ToxFileControl
import im.tox.tox4j.core.enums.ToxGroupPrivacyState
import im.tox.tox4j.core.enums.ToxGroupRole
import im.tox.tox4j.core.enums.ToxGroupTopicLock
import im.tox.tox4j.core.enums.ToxGroupVoiceState
import im.tox.tox4j.core.enums.ToxMessageType
import im.tox.tox4j.core.enums.ToxUserStatus
import im.tox.tox4j.core.options.SaveDataOptions
import im.tox.tox4j.core.options.ToxOptions
import im.tox.tox4j.core.proto.Options

private fun <T> Array<T>.atOrFirst(index: Int): T = getOrNull(index) ?: this[0]

class ToxCoreImpl(
    initOptions: ToxOptions,
) : ToxCore {
    val options: ToxOptions = initOptions.copy(saveData = SaveDataOptions.None)

    internal val instanceNumber =
        run {
            val opts =
                Options
                    .newBuilder()
                    .setIpv6Enabled(initOptions.ipv6Enabled)
                    .setUdpEnabled(initOptions.udpEnabled)
                    .setLocalDiscoveryEnabled(initOptions.localDiscoveryEnabled)
                    .setDhtAnnouncementsEnabled(initOptions.dhtAnnouncementsEnabled)
                    .setProxyType(initOptions.proxy.proxyType.ordinal)
                    .setProxyHost(initOptions.proxy.proxyAddress)
                    .setProxyPort(initOptions.proxy.proxyPort.toInt())
                    .setStartPort(initOptions.startPort.toInt())
                    .setEndPort(initOptions.endPort.toInt())
                    .setTcpPort(initOptions.tcpPort.toInt())
                    .setHolePunchingEnabled(initOptions.holePunchingEnabled)
                    .setSavedataType(initOptions.saveData.kind.ordinal)
                    .setSavedata(ByteString.copyFrom(initOptions.saveData.data))
                    .setExperimentalOwnedData(initOptions.experimentalOwnedData)
                    .setExperimentalThreadSafety(initOptions.experimentalThreadSafety)
                    .setExperimentalGroupsPersistence(initOptions.experimentalGroupsPersistence)
                    .setExperimentalDisableDns(initOptions.experimentalDisableDns)
                    .build()
            ToxCoreJni.toxNew(opts.toByteArray())
        }

    override fun close(): Unit = ToxCoreJni.toxKill(instanceNumber)

    override val iterationInterval: Int
        get() = ToxCoreJni.toxIterationInterval(instanceNumber)

    override fun <ToxCoreState> iterate(
        handler: ToxCoreEventListener<ToxCoreState>,
        state: ToxCoreState,
    ): ToxCoreState = ToxCoreEventDispatch.dispatch(handler, ToxCoreJni.toxIterate(instanceNumber), state)

    override fun conferenceNew(): ToxConferenceNumber = ToxConferenceNumber(ToxCoreJni.toxConferenceNew(instanceNumber))

    override fun conferenceDelete(conferenceNumber: ToxConferenceNumber) =
        ToxCoreJni.toxConferenceDelete(instanceNumber, conferenceNumber.value)

    override fun conferencePeerCount(conferenceNumber: ToxConferenceNumber): Int =
        ToxCoreJni.toxConferencePeerCount(instanceNumber, conferenceNumber.value)

    override fun conferenceOfflinePeerCount(conferenceNumber: ToxConferenceNumber): Int =
        ToxCoreJni.toxConferenceOfflinePeerCount(instanceNumber, conferenceNumber.value)

    override fun conferenceSetMaxOffline(
        conferenceNumber: ToxConferenceNumber,
        maxOffline: Int,
    ) = ToxCoreJni.toxConferenceSetMaxOffline(instanceNumber, conferenceNumber.value, maxOffline)

    override fun conferenceJoin(
        friendNumber: ToxFriendNumber,
        cookie: ToxConferenceCookie,
    ): ToxConferenceNumber = ToxConferenceNumber(ToxCoreJni.toxConferenceJoin(instanceNumber, friendNumber.value, cookie.value))

    override fun conferenceSendMessage(
        conferenceNumber: ToxConferenceNumber,
        type: ToxMessageType,
        message: ToxConferenceMessage,
    ) = ToxCoreJni.toxConferenceSendMessage(instanceNumber, conferenceNumber.value, type.ordinal, message.value)

    override fun conferenceGetTitle(conferenceNumber: ToxConferenceNumber): ToxConferenceTitle =
        ToxConferenceTitle(ToxCoreJni.toxConferenceGetTitle(instanceNumber, conferenceNumber.value))

    override fun conferenceSetTitle(
        conferenceNumber: ToxConferenceNumber,
        title: ToxConferenceTitle,
    ) = ToxCoreJni.toxConferenceSetTitle(instanceNumber, conferenceNumber.value, title.value)

    override fun conferenceGetType(conferenceNumber: ToxConferenceNumber): ToxConferenceType =
        ToxConferenceType.values().atOrFirst(ToxCoreJni.toxConferenceGetType(instanceNumber, conferenceNumber.value))

    override fun conferenceGetId(conferenceNumber: ToxConferenceNumber): ToxConferenceId =
        ToxConferenceId(ToxCoreJni.toxConferenceGetId(instanceNumber, conferenceNumber.value))

    override fun conferenceById(id: ToxConferenceId): ToxConferenceNumber =
        ToxConferenceNumber(ToxCoreJni.toxConferenceById(instanceNumber, id.value))

    override fun conferenceOfflinePeerGetName(
        conferenceNumber: ToxConferenceNumber,
        offlinePeerNumber: ToxConferenceOfflinePeerNumber,
    ): ToxConferenceOfflinePeerName =
        ToxConferenceOfflinePeerName(
            ToxCoreJni.toxConferenceOfflinePeerGetName(instanceNumber, conferenceNumber.value, offlinePeerNumber.value),
        )

    override fun conferenceOfflinePeerGetPublicKey(
        conferenceNumber: ToxConferenceNumber,
        offlinePeerNumber: ToxConferenceOfflinePeerNumber,
    ): ToxPublicKey =
        ToxPublicKey(ToxCoreJni.toxConferenceOfflinePeerGetPublicKey(instanceNumber, conferenceNumber.value, offlinePeerNumber.value))

    override fun conferenceOfflinePeerGetLastActive(
        conferenceNumber: ToxConferenceNumber,
        offlinePeerNumber: ToxConferenceOfflinePeerNumber,
    ): Long = ToxCoreJni.toxConferenceOfflinePeerGetLastActive(instanceNumber, conferenceNumber.value, offlinePeerNumber.value)

    override fun conferencePeerGetName(
        conferenceNumber: ToxConferenceNumber,
        peerNumber: ToxConferencePeerNumber,
    ): ToxConferencePeerName =
        ToxConferencePeerName(ToxCoreJni.toxConferencePeerGetName(instanceNumber, conferenceNumber.value, peerNumber.value))

    override fun conferencePeerGetPublicKey(
        conferenceNumber: ToxConferenceNumber,
        peerNumber: ToxConferencePeerNumber,
    ): ToxPublicKey = ToxPublicKey(ToxCoreJni.toxConferencePeerGetPublicKey(instanceNumber, conferenceNumber.value, peerNumber.value))

    override fun conferencePeerNumberIsOurs(
        conferenceNumber: ToxConferenceNumber,
        peerNumber: ToxConferencePeerNumber,
    ): Boolean = ToxCoreJni.toxConferencePeerNumberIsOurs(instanceNumber, conferenceNumber.value, peerNumber.value)

    override fun fileSend(
        friendNumber: ToxFriendNumber,
        kind: Int,
        fileSize: Long,
        fileId: ToxFileId,
        filename: ToxFilename,
    ): ToxFileNumber =
        ToxFileNumber(ToxCoreJni.toxFileSend(instanceNumber, friendNumber.value, kind, fileSize, fileId.value, filename.value))

    override fun friendAdd(
        address: ToxAddress,
        message: ToxFriendMessage,
    ): ToxFriendNumber = ToxFriendNumber(ToxCoreJni.toxFriendAdd(instanceNumber, address.value, message.value))

    override fun friendAddNorequest(publicKey: ToxPublicKey): ToxFriendNumber =
        ToxFriendNumber(ToxCoreJni.toxFriendAddNorequest(instanceNumber, publicKey.value))

    override fun friendDelete(friendNumber: ToxFriendNumber) = ToxCoreJni.toxFriendDelete(instanceNumber, friendNumber.value)

    override fun friendByPublicKey(publicKey: ToxPublicKey): ToxFriendNumber =
        ToxFriendNumber(ToxCoreJni.toxFriendByPublicKey(instanceNumber, publicKey.value))

    override fun friendExists(friendNumber: ToxFriendNumber): Boolean = ToxCoreJni.toxFriendExists(instanceNumber, friendNumber.value)

    override fun friendGetPublicKey(friendNumber: ToxFriendNumber): ToxPublicKey =
        ToxPublicKey(ToxCoreJni.toxFriendGetPublicKey(instanceNumber, friendNumber.value))

    override fun friendGetLastOnline(friendNumber: ToxFriendNumber): Long =
        ToxCoreJni.toxFriendGetLastOnline(instanceNumber, friendNumber.value)

    override fun friendGetTyping(friendNumber: ToxFriendNumber) = ToxCoreJni.toxFriendGetTyping(instanceNumber, friendNumber.value)

    override fun friendSendMessage(
        friendNumber: ToxFriendNumber,
        type: ToxMessageType,
        message: ToxFriendMessage,
    ): ToxFriendMessageId =
        ToxFriendMessageId(ToxCoreJni.toxFriendSendMessage(instanceNumber, friendNumber.value, type.ordinal, message.value))

    override fun friendSendLossyPacket(
        friendNumber: ToxFriendNumber,
        data: ToxFriendLossyPacket,
    ) = ToxCoreJni.toxFriendSendLossyPacket(instanceNumber, friendNumber.value, data.value)

    override fun friendSendLosslessPacket(
        friendNumber: ToxFriendNumber,
        data: ToxFriendLosslessPacket,
    ) = ToxCoreJni.toxFriendSendLosslessPacket(instanceNumber, friendNumber.value, data.value)

    override fun groupNew(
        privacyState: ToxGroupPrivacyState,
        groupName: ToxGroupName,
        name: ToxGroupName,
    ): ToxGroupNumber = ToxGroupNumber(ToxCoreJni.toxGroupNew(instanceNumber, privacyState.ordinal, groupName.value, name.value))

    override fun groupJoin(
        chatId: ToxGroupChatId,
        name: ToxGroupName,
        password: ToxGroupPassword,
    ): ToxGroupNumber = ToxGroupNumber(ToxCoreJni.toxGroupJoin(instanceNumber, chatId.value, name.value, password.value))

    override fun groupIsConnected(groupNumber: ToxGroupNumber): Boolean = ToxCoreJni.toxGroupIsConnected(instanceNumber, groupNumber.value)

    override fun groupDisconnect(groupNumber: ToxGroupNumber) = ToxCoreJni.toxGroupDisconnect(instanceNumber, groupNumber.value)

    override fun groupLeave(
        groupNumber: ToxGroupNumber,
        partMessage: ToxGroupPartMessage,
    ) = ToxCoreJni.toxGroupLeave(instanceNumber, groupNumber.value, partMessage.value)

    override fun groupSelfSetName(
        groupNumber: ToxGroupNumber,
        name: ToxGroupName,
    ) = ToxCoreJni.toxGroupSelfSetName(instanceNumber, groupNumber.value, name.value)

    override fun groupSelfGetName(groupNumber: ToxGroupNumber): ToxGroupName =
        ToxGroupName(ToxCoreJni.toxGroupSelfGetName(instanceNumber, groupNumber.value))

    override fun groupSelfSetStatus(
        groupNumber: ToxGroupNumber,
        status: ToxUserStatus,
    ) = ToxCoreJni.toxGroupSelfSetStatus(instanceNumber, groupNumber.value, status.ordinal)

    override fun groupSelfGetStatus(groupNumber: ToxGroupNumber): ToxUserStatus =
        ToxUserStatus.values().atOrFirst(ToxCoreJni.toxGroupSelfGetStatus(instanceNumber, groupNumber.value))

    override fun groupSelfGetRole(groupNumber: ToxGroupNumber): ToxGroupRole =
        ToxGroupRole.values().atOrFirst(ToxCoreJni.toxGroupSelfGetRole(instanceNumber, groupNumber.value))

    override fun groupSelfGetPeerId(groupNumber: ToxGroupNumber): ToxGroupPeerNumber =
        ToxGroupPeerNumber(ToxCoreJni.toxGroupSelfGetPeerId(instanceNumber, groupNumber.value))

    override fun groupSelfGetPublicKey(groupNumber: ToxGroupNumber): ToxPublicKey =
        ToxPublicKey(ToxCoreJni.toxGroupSelfGetPublicKey(instanceNumber, groupNumber.value))

    override fun groupSetTopic(
        groupNumber: ToxGroupNumber,
        topic: ToxGroupTopic,
    ) = ToxCoreJni.toxGroupSetTopic(instanceNumber, groupNumber.value, topic.value)

    override fun groupGetTopic(groupNumber: ToxGroupNumber): ToxGroupTopic =
        ToxGroupTopic(ToxCoreJni.toxGroupGetTopic(instanceNumber, groupNumber.value))

    override fun groupGetName(groupNumber: ToxGroupNumber): ToxGroupName =
        ToxGroupName(ToxCoreJni.toxGroupGetName(instanceNumber, groupNumber.value))

    override fun groupGetChatId(groupNumber: ToxGroupNumber): ToxGroupChatId =
        ToxGroupChatId(ToxCoreJni.toxGroupGetChatId(instanceNumber, groupNumber.value))

    override fun groupGetPrivacyState(groupNumber: ToxGroupNumber): ToxGroupPrivacyState =
        ToxGroupPrivacyState.values().atOrFirst(ToxCoreJni.toxGroupGetPrivacyState(instanceNumber, groupNumber.value))

    override fun groupGetVoiceState(groupNumber: ToxGroupNumber): ToxGroupVoiceState =
        ToxGroupVoiceState.values().atOrFirst(ToxCoreJni.toxGroupGetVoiceState(instanceNumber, groupNumber.value))

    override fun groupGetTopicLock(groupNumber: ToxGroupNumber): ToxGroupTopicLock =
        ToxGroupTopicLock.values().atOrFirst(ToxCoreJni.toxGroupGetTopicLock(instanceNumber, groupNumber.value))

    override fun groupGetPeerLimit(groupNumber: ToxGroupNumber): Int = ToxCoreJni.toxGroupGetPeerLimit(instanceNumber, groupNumber.value)

    override fun groupGetPassword(groupNumber: ToxGroupNumber): ToxGroupPassword =
        ToxGroupPassword(ToxCoreJni.toxGroupGetPassword(instanceNumber, groupNumber.value))

    override fun groupSendMessage(
        groupNumber: ToxGroupNumber,
        messageType: ToxMessageType,
        message: ToxGroupMessage,
    ): ToxGroupMessageId =
        ToxGroupMessageId(ToxCoreJni.toxGroupSendMessage(instanceNumber, groupNumber.value, messageType.ordinal, message.value))

    override fun groupSendPrivateMessage(
        groupNumber: ToxGroupNumber,
        peerId: ToxGroupPeerNumber,
        messageType: ToxMessageType,
        message: ToxGroupPrivateMessage,
    ): ToxGroupMessageId =
        ToxGroupMessageId(
            ToxCoreJni.toxGroupSendPrivateMessage(instanceNumber, groupNumber.value, peerId.value, messageType.ordinal, message.value),
        )

    override fun groupSendCustomPacket(
        groupNumber: ToxGroupNumber,
        lossless: Boolean,
        data: ToxGroupCustomPacket,
    ) = ToxCoreJni.toxGroupSendCustomPacket(instanceNumber, groupNumber.value, lossless, data.value)

    override fun groupSendCustomPrivatePacket(
        groupNumber: ToxGroupNumber,
        peerId: ToxGroupPeerNumber,
        lossless: Boolean,
        data: ToxGroupCustomPrivatePacket,
    ) = ToxCoreJni.toxGroupSendCustomPrivatePacket(instanceNumber, groupNumber.value, peerId.value, lossless, data.value)

    override fun groupInviteFriend(
        groupNumber: ToxGroupNumber,
        friendNumber: ToxFriendNumber,
    ) = ToxCoreJni.toxGroupInviteFriend(instanceNumber, groupNumber.value, friendNumber.value)

    override fun groupInviteAccept(
        friendNumber: ToxFriendNumber,
        inviteData: ToxGroupInviteData,
        name: ToxGroupName,
        password: ToxGroupPassword,
    ): ToxGroupNumber =
        ToxGroupNumber(ToxCoreJni.toxGroupInviteAccept(instanceNumber, friendNumber.value, inviteData.value, name.value, password.value))

    override fun groupSetPassword(
        groupNumber: ToxGroupNumber,
        password: ToxGroupPassword,
    ) = ToxCoreJni.toxGroupSetPassword(instanceNumber, groupNumber.value, password.value)

    override fun groupSetTopicLock(
        groupNumber: ToxGroupNumber,
        topicLock: ToxGroupTopicLock,
    ) = ToxCoreJni.toxGroupSetTopicLock(instanceNumber, groupNumber.value, topicLock.ordinal)

    override fun groupSetVoiceState(
        groupNumber: ToxGroupNumber,
        voiceState: ToxGroupVoiceState,
    ) = ToxCoreJni.toxGroupSetVoiceState(instanceNumber, groupNumber.value, voiceState.ordinal)

    override fun groupSetPrivacyState(
        groupNumber: ToxGroupNumber,
        privacyState: ToxGroupPrivacyState,
    ) = ToxCoreJni.toxGroupSetPrivacyState(instanceNumber, groupNumber.value, privacyState.ordinal)

    override fun groupSetPeerLimit(
        groupNumber: ToxGroupNumber,
        peerLimit: Int,
    ) = ToxCoreJni.toxGroupSetPeerLimit(instanceNumber, groupNumber.value, peerLimit)

    override fun groupSetIgnore(
        groupNumber: ToxGroupNumber,
        peerId: ToxGroupPeerNumber,
        ignore: Boolean,
    ) = ToxCoreJni.toxGroupSetIgnore(instanceNumber, groupNumber.value, peerId.value, ignore)

    override fun groupSetRole(
        groupNumber: ToxGroupNumber,
        peerId: ToxGroupPeerNumber,
        role: ToxGroupRole,
    ) = ToxCoreJni.toxGroupSetRole(instanceNumber, groupNumber.value, peerId.value, role.ordinal)

    override fun groupKickPeer(
        groupNumber: ToxGroupNumber,
        peerId: ToxGroupPeerNumber,
    ) = ToxCoreJni.toxGroupKickPeer(instanceNumber, groupNumber.value, peerId.value)

    override fun groupPeerGetName(
        groupNumber: ToxGroupNumber,
        peerNumber: ToxGroupPeerNumber,
    ): ToxGroupPeerName = ToxGroupPeerName(ToxCoreJni.toxGroupPeerGetName(instanceNumber, groupNumber.value, peerNumber.value))

    override fun groupPeerGetStatus(
        groupNumber: ToxGroupNumber,
        peerNumber: ToxGroupPeerNumber,
    ): ToxUserStatus =
        ToxUserStatus.values().atOrFirst(ToxCoreJni.toxGroupPeerGetStatus(instanceNumber, groupNumber.value, peerNumber.value))

    override fun groupPeerGetRole(
        groupNumber: ToxGroupNumber,
        peerNumber: ToxGroupPeerNumber,
    ): ToxGroupRole = ToxGroupRole.values().atOrFirst(ToxCoreJni.toxGroupPeerGetRole(instanceNumber, groupNumber.value, peerNumber.value))

    override fun groupPeerGetConnectionStatus(
        groupNumber: ToxGroupNumber,
        peerNumber: ToxGroupPeerNumber,
    ): ToxConnection =
        ToxConnection.values().atOrFirst(ToxCoreJni.toxGroupPeerGetConnectionStatus(instanceNumber, groupNumber.value, peerNumber.value))

    override fun groupPeerGetPublicKey(
        groupNumber: ToxGroupNumber,
        peerNumber: ToxGroupPeerNumber,
    ): ToxPublicKey = ToxPublicKey(ToxCoreJni.toxGroupPeerGetPublicKey(instanceNumber, groupNumber.value, peerNumber.value))

    override val savedata: ToxSavedata get() = ToxSavedata(ToxCoreJni.toxGetSavedata(instanceNumber))

    override fun bootstrap(
        host: String,
        port: Port,
        publicKey: ToxDhtId,
    ) = ToxCoreJni.toxBootstrap(instanceNumber, host, port.value.toInt(), publicKey.value)

    override fun addTcpRelay(
        host: String,
        port: Port,
        publicKey: ToxDhtId,
    ) = ToxCoreJni.toxAddTcpRelay(instanceNumber, host, port.value.toInt(), publicKey.value)

    override val address: ToxAddress get() = ToxAddress(ToxCoreJni.toxSelfGetAddress(instanceNumber))

    override fun setNospam(nospam: Int) = ToxCoreJni.toxSelfSetNospam(instanceNumber, nospam)

    override val nospam: Int get() = ToxCoreJni.toxSelfGetNospam(instanceNumber)

    override val publicKey: ToxPublicKey get() = ToxPublicKey(ToxCoreJni.toxSelfGetPublicKey(instanceNumber))

    override val secretKey: ToxSecretKey get() = ToxSecretKey(ToxCoreJni.toxSelfGetSecretKey(instanceNumber))

    override fun setName(name: ToxName) = ToxCoreJni.toxSelfSetName(instanceNumber, name.value)

    override val name: ToxName get() = ToxName(ToxCoreJni.toxSelfGetName(instanceNumber))

    override fun setStatusMessage(statusMessage: ToxStatusMessage) = ToxCoreJni.toxSelfSetStatusMessage(instanceNumber, statusMessage.value)

    override val statusMessage: ToxStatusMessage get() = ToxStatusMessage(ToxCoreJni.toxSelfGetStatusMessage(instanceNumber))

    override fun setStatus(status: ToxUserStatus) = ToxCoreJni.toxSelfSetStatus(instanceNumber, status.ordinal)

    override val status: ToxUserStatus get() = ToxUserStatus.values().atOrFirst(ToxCoreJni.toxSelfGetStatus(instanceNumber))

    override val friendList: List<ToxFriendNumber> get() = ToxCoreJni.toxSelfGetFriendList(instanceNumber).map { ToxFriendNumber(it) }

    override fun setTyping(
        friendNumber: ToxFriendNumber,
        typing: Boolean,
    ) = ToxCoreJni.toxSelfSetTyping(instanceNumber, friendNumber.value, typing)

    override fun fileControl(
        friendNumber: ToxFriendNumber,
        fileNumber: ToxFileNumber,
        control: ToxFileControl,
    ) = ToxCoreJni.toxFileControl(instanceNumber, friendNumber.value, fileNumber.value, control.ordinal)

    override fun fileSeek(
        friendNumber: ToxFriendNumber,
        fileNumber: ToxFileNumber,
        position: Long,
    ) = ToxCoreJni.toxFileSeek(instanceNumber, friendNumber.value, fileNumber.value, position)

    override fun fileGetFileId(
        friendNumber: ToxFriendNumber,
        fileNumber: ToxFileNumber,
    ): ToxFileId = ToxFileId(ToxCoreJni.toxFileGetFileId(instanceNumber, friendNumber.value, fileNumber.value))

    override fun fileSendChunk(
        friendNumber: ToxFriendNumber,
        fileNumber: ToxFileNumber,
        position: Long,
        data: ToxFileChunk,
    ) = ToxCoreJni.toxFileSendChunk(instanceNumber, friendNumber.value, fileNumber.value, position, data.value)

    override fun conferenceInvite(
        friendNumber: ToxFriendNumber,
        conferenceNumber: ToxConferenceNumber,
    ) = ToxCoreJni.toxConferenceInvite(instanceNumber, friendNumber.value, conferenceNumber.value)

    override val conferenceGetChatlist: List<ToxConferenceNumber> get() =
        ToxCoreJni.toxConferenceGetChatlist(instanceNumber).map { ToxConferenceNumber(it) }

    override val dhtId: ToxDhtId get() = ToxDhtId(ToxCoreJni.toxSelfGetDhtId(instanceNumber))

    override val udpPort: Port get() = Port(ToxCoreJni.toxSelfGetUdpPort(instanceNumber).toUShort())

    override val tcpPort: Port get() = Port(ToxCoreJni.toxSelfGetTcpPort(instanceNumber).toUShort())

    protected fun finalize() {
        runCatching {
            ToxCoreJni.toxKill(instanceNumber)
            ToxCoreJni.toxFinalize(instanceNumber)
        }
    }
}
