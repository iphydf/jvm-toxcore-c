package chat.tox.ktox

import im.tox.tox4j.core.ToxCoreConstants
import im.tox.tox4j.core.ToxCoreEvent
import im.tox.tox4j.core.data.ToxAddress
import im.tox.tox4j.core.data.ToxDhtId
import im.tox.tox4j.core.data.ToxFileId
import im.tox.tox4j.core.data.ToxFileNumber
import im.tox.tox4j.core.data.ToxFriendMessageId
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.core.data.ToxGroupChatId
import im.tox.tox4j.core.data.ToxGroupMessageId
import im.tox.tox4j.core.data.ToxGroupNumber
import im.tox.tox4j.core.data.ToxGroupPeerNumber
import im.tox.tox4j.core.data.ToxPort
import im.tox.tox4j.core.data.ToxPublicKey
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
import im.tox.tox4j.core.enums.ToxMessageType
import im.tox.tox4j.core.enums.ToxUserStatus
import im.tox.tox4j.core.exceptions.ToxBootstrapException
import im.tox.tox4j.core.exceptions.ToxFileControlException
import im.tox.tox4j.core.exceptions.ToxFileSendChunkException
import im.tox.tox4j.core.exceptions.ToxFileSendException
import im.tox.tox4j.core.exceptions.ToxFriendAddException
import im.tox.tox4j.core.exceptions.ToxFriendSendMessageException
import im.tox.tox4j.core.exceptions.ToxGroupInviteAcceptException
import im.tox.tox4j.core.exceptions.ToxGroupInviteFriendException
import im.tox.tox4j.core.exceptions.ToxGroupJoinException
import im.tox.tox4j.core.exceptions.ToxGroupNewException
import im.tox.tox4j.core.exceptions.ToxGroupSendMessageException
import im.tox.tox4j.core.exceptions.ToxGroupStateQueryException
import im.tox.tox4j.core.options.SavedataOptions
import im.tox.tox4j.core.options.ToxOptions
import im.tox.tox4j.impl.jni.ToxAvImpl
import im.tox.tox4j.impl.jni.ToxCoreImpl
import im.tox.tox4j.impl.jni.ToxCryptoImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * One Tox profile in [dir]: profile.tox for toxcore, chats.db for everything else, files/ for the files
 * in chats and avatars/ for friends' pictures.
 *
 * Tox and the store only run on one thread. The UI reads [state] and calls the functions here,
 * which do their work on that thread.
 */
class ToxClient internal constructor(
    private val dir: File,
    val id: ProfileId,
    private val platform: Platform,
    /** The one call there can be, which all profiles share. */
    val line: CallLine,
    /** Where to join the network: the built-in list, or a test's own nodes. */
    private val nodes: List<Node>,
) {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "tox").apply { isDaemon = true } }
    private val thread = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + thread)

    private val mutableState = MutableStateFlow(ClientState())
    val state: StateFlow<ClientState> = mutableState.asStateFlow()

    private val files = File(dir, "files")
    private val avatars = File(dir, "avatars")
    private val ownAvatar = File(dir, "avatar.png")

    // Everything below belongs to the Tox thread.
    private lateinit var store: Store
    private lateinit var tox: ToxCoreImpl
    private var settings = Settings()
    private var profile = Profile()
    private val friends = LinkedHashMap<FriendKey, Friend>()
    private val numbers = HashMap<FriendKey, ToxFriendNumber>()
    private val keys = HashMap<ToxFriendNumber, FriendKey>()
    private var requests = listOf<FriendRequest>()
    private var chat: Chat? = null
    private var visible = false

    /** Messages toxcore sent and hasn't confirmed, by friend. toxcore forgets them when the friend goes offline. */
    private val receipts = HashMap<FriendKey, HashMap<ToxFriendMessageId, MessageId>>()

    /** The files moving now. toxcore forgets them when the friend goes offline; the store keeps how far they got. */
    private val transfers = HashMap<TransferKey, Transfer>()
    private var progressChanged = false
    private var lastProgress = 0L

    private val groups = LinkedHashMap<GroupId, Group>()
    private val groupNumbers = HashMap<GroupId, ToxGroupNumber>()
    private val groupIds = HashMap<ToxGroupNumber, GroupId>()

    /** Who's in each group now, by their number there. toxcore tells us again after each (re)join. */
    private val peers = HashMap<GroupId, HashMap<ToxGroupPeerNumber, Peer>>()
    private var invites = listOf<GroupInvite>()
    private var groupChat: GroupChat? = null

    private val calls =
        Calls(
            id,
            line,
            platform,
            post = { scope.launch { it() } },
            number = numbers::get,
            key = keys::get,
            friend = friends::get,
            changed = ::publish,
            ended = ::noteCall,
        )

    /** The call's pictures, which change too often for [state]. */
    val callVideo: CallVideo get() = calls.video

    private var unsaved = false
    private var restart = false
    private var lastBootstrap = 0L

    private data class TransferKey(
        val friend: FriendKey,
        val file: ToxFileNumber,
    )

    /** A file moving now: a chat's file, or an avatar if [message] is null. */
    private class Transfer(
        val message: MessageId?,
        val outgoing: Boolean,
        val file: File,
        val size: Long,
        var done: Long,
    ) {
        var channel: FileChannel? = null

        fun close() {
            channel?.close()
            channel = null
        }
    }

    fun start() {
        scope.launch { run() }
    }

    /** Saves and stops. The client can't start again. */
    fun close() {
        runBlocking(thread) {
            // The loop and queued commands use Tox after their waits.
            scope.coroutineContext.job.cancelAndJoin()
            if (::tox.isInitialized) {
                calls.stop()
                save()
                friends.keys.toList().forEach(::stopTransfers)
                tox.close()
            }
            if (::store.isInitialized) store.close()
        }
        executor.shutdown()
    }

    private suspend fun run() {
        dir.mkdirs()
        store = Store(File(dir, "chats.db").path)
        settings = store.settings()
        requests = store.requests()
        invites = store.invites()
        while (true) {
            open()
            while (!restart) {
                tox.iterate().forEach(::on)
                calls.iterate()
                bootstrap()
                if (unsaved) save()
                if (progressChanged && System.currentTimeMillis() - lastProgress >= PROGRESS_INTERVAL_MS) publish()
                // toxav says 0 while decoding falls behind: then what's queued here (sound, pictures, the user's
                // taps) goes first, else the loop would spin and starve it.
                val wait = minOf(tox.iterationInterval, calls.iterationInterval).toLong()
                if (wait > 0) delay(wait) else yield()
            }
            calls.stop()
            save()
            friends.keys.toList().forEach(::stopTransfers)
            tox.close()
            restart = false
        }
    }

    private fun open() {
        val profileFile = File(dir, PROFILE)
        val savedata = if (profileFile.exists()) SavedataOptions.ToxSave(profileFile.readBytes()) else SavedataOptions.None
        tox =
            ToxCoreImpl(
                ToxOptions(
                    ipv6Enabled = settings.ipv6,
                    udpEnabled = settings.udp,
                    localDiscoveryEnabled = settings.localDiscovery,
                    savedata = savedata,
                    // Like toxic: a group we founded keeps its founder key across restarts this way.
                    experimentalGroupsPersistence = true,
                ),
            )
        if (savedata == SavedataOptions.None) save()
        calls.start(ToxAvImpl(tox))
        profile =
            Profile(
                address = tox.address.toByteArray().hex(),
                name = tox.name.decode(),
                statusMessage = tox.statusMessage.decode(),
                status = tox.status,
                avatar = picture(ownAvatar),
            )
        val stored = store.friends()
        friends.clear()
        numbers.clear()
        keys.clear()
        receipts.clear()
        transfers.clear()
        for (number in tox.friendList) {
            val key = FriendKey(tox.friendGetPublicKey(number).toByteArray().hex())
            store.addFriend(key)
            numbers[key] = number
            keys[number] = key
            friends[key] = newFriend(key, stored[key] ?: StoredFriend("", "", 0))
        }
        loadGroups()
        lastBootstrap = 0
        publish()
    }

    /** The groups from the save. */
    private fun loadGroups() {
        groups.clear()
        groupNumbers.clear()
        groupIds.clear()
        peers.clear()
        val unread = store.groups()
        for (number in tox.groupList) {
            val id = GroupId(tox.groupGetChatId(number).toByteArray().hex())
            addGroup(number, id, unread[id] ?: 0)
        }
    }

    private fun addGroup(
        number: ToxGroupNumber,
        id: GroupId,
        unread: Int,
    ) {
        store.addGroup(id)
        groupNumbers[id] = number
        groupIds[number] = id
        peers[id] = HashMap()
        groups[id] =
            Group(
                id = id,
                name = "",
                topic = "",
                privacy = ToxGroupPrivacyState.PUBLIC,
                connected = false,
                peers = emptyList(),
                selfRole = ToxGroupRole.USER,
                password = "",
                peerLimit = 0,
                topicLocked = false,
                unread = unread,
                last = store.lastGroupMessage(id),
                problem = null,
            )
        refreshGroup(id)
    }

    /** Reads what toxcore knows about a group now. */
    private fun refreshGroup(id: GroupId) {
        val number = groupNumbers[id] ?: return
        val group = groups[id] ?: return
        groups[id] =
            try {
                group.copy(
                    name = tox.groupGetName(number).decode(),
                    topic = tox.groupGetTopic(number).decode(),
                    privacy = tox.groupGetPrivacyState(number),
                    connected = tox.groupIsConnected(number),
                    selfRole = tox.groupSelfGetRole(number),
                    password = tox.groupGetPassword(number).decode(),
                    peerLimit = tox.groupGetPeerLimit(number),
                    topicLocked = tox.groupGetTopicLock(number) == ToxGroupTopicLock.ENABLED,
                )
            } catch (_: ToxGroupStateQueryException) {
                group
            }
        publishPeers(id)
    }

    /** Shows a group's people, us among them, moderators first. */
    private fun publishPeers(id: GroupId) {
        val number = groupNumbers[id] ?: return
        val group = groups[id] ?: return
        val self =
            runCatching {
                Peer(
                    key = tox.groupSelfGetPublicKey(number).toByteArray().hex(),
                    name = tox.groupSelfGetName(number).decode(),
                    role = tox.groupSelfGetRole(number),
                    status = tox.groupSelfGetStatus(number),
                    self = true,
                )
            }.getOrNull()
        val everyone = peers[id].orEmpty().values + listOfNotNull(self.takeIf { group.connected })
        groups[id] = group.copy(peers = everyone.sortedWith(compareBy<Peer> { it.role.ordinal }.thenBy { it.displayName.lowercase() }))
        publish()
    }

    private fun peer(
        id: GroupId,
        number: ToxGroupNumber,
        peer: ToxGroupPeerNumber,
    ): Peer? =
        runCatching {
            Peer(
                key = tox.groupPeerGetPublicKey(number, peer).toByteArray().hex(),
                name = tox.groupPeerGetName(number, peer).decode(),
                role = tox.groupPeerGetRole(number, peer),
                status = tox.groupPeerGetStatus(number, peer),
                self = false,
            )
        }.getOrNull().also { if (it != null) peers.getOrPut(id) { HashMap() }[peer] = it }

    /** The group number of a group event, as our group; learns groups toxcore made itself. */
    private fun groupOf(number: ToxGroupNumber): GroupId? {
        groupIds[number]?.let { return it }
        val id = runCatching { GroupId(tox.groupGetChatId(number).toByteArray().hex()) }.getOrNull() ?: return null
        addGroup(number, id, 0)
        return id
    }

    private fun newFriend(
        key: FriendKey,
        stored: StoredFriend,
    ) = Friend(
        key = key,
        name = stored.name,
        statusMessage = stored.statusMessage,
        status = ToxUserStatus.NONE,
        connection = ToxConnection.NONE,
        typing = false,
        unread = stored.unread,
        last = store.lastMessage(key),
        avatar = stored.avatarHash.takeIf { it.isNotEmpty() }?.let { Picture(avatarFile(key), it) },
    )

    private fun avatarFile(key: FriendKey) = File(avatars, "${key.hex}.png")

    private fun picture(file: File): Picture? = if (file.exists()) Picture(file, ToxCryptoImpl.hash(file.readBytes()).hex()) else null

    /** While offline, asks a few nodes from the built-in list every so often. */
    private fun bootstrap() {
        val now = System.currentTimeMillis()
        if (profile.connection != ToxConnection.NONE || now - lastBootstrap < BOOTSTRAP_INTERVAL_MS) return
        lastBootstrap = now
        for (node in nodes.shuffled().take(BOOTSTRAP_NODES)) {
            val key = ToxDhtId(node.key.unhex())
            try {
                tox.bootstrap(node.host, ToxPort(node.port), key)
                for (port in node.tcpPorts) tox.addTcpRelay(node.host, ToxPort(port), key)
            } catch (_: ToxBootstrapException) {
                // The next round tries other nodes.
            }
        }
    }

    private fun save() {
        writeAtomically(File(dir, PROFILE), tox.savedata)
        unsaved = false
    }

    private fun publish() {
        mutableState.value =
            ClientState(
                profile = profile,
                friends = friends.values.sortedWith(compareByDescending<Friend> { it.last?.time ?: 0L }.thenBy { it.displayName }),
                requests = requests,
                settings = settings,
                chat = chat,
                progress = transfers.values.mapNotNull { t -> t.message?.let { it to t.done } }.toMap(),
                groups = groups.values.toList(),
                invites = invites,
                groupChat = groupChat,
                call = calls.call,
            )
        progressChanged = false
        lastProgress = System.currentTimeMillis()
    }

    private fun updateFriend(
        key: FriendKey,
        change: (Friend) -> Friend,
    ) {
        friends[key]?.let { friends[key] = change(it) }
        publish()
    }

    /** This client as a node on this machine, for other clients to bootstrap from. */
    internal suspend fun localNode(): Node =
        withContext(thread) { Node("127.0.0.1", tox.udpPort.value, emptyList(), tox.dhtId.toByteArray().hex()) }

    private fun on(event: ToxCoreEvent) {
        when (event) {
            is ToxCoreEvent.SelfConnectionStatus -> event.handle()
            is ToxCoreEvent.FriendRequest -> event.handle()
            is ToxCoreEvent.FriendMessage -> event.handle()
            is ToxCoreEvent.FriendName -> event.handle()
            is ToxCoreEvent.FriendStatusMessage -> event.handle()
            is ToxCoreEvent.FriendStatus -> event.handle()
            is ToxCoreEvent.FriendConnectionStatus -> event.handle()
            is ToxCoreEvent.FriendTyping -> event.handle()
            is ToxCoreEvent.FriendReadReceipt -> event.handle()
            is ToxCoreEvent.FileRecv -> event.handle()
            is ToxCoreEvent.FileRecvChunk -> event.handle()
            is ToxCoreEvent.FileRecvControl -> event.handle()
            is ToxCoreEvent.FileChunkRequest -> event.handle()
            is ToxCoreEvent.GroupInvite -> event.handle()
            is ToxCoreEvent.GroupSelfJoin -> event.handle()
            is ToxCoreEvent.GroupJoinFail -> event.handle()
            is ToxCoreEvent.GroupPeerJoin -> event.handle()
            is ToxCoreEvent.GroupPeerExit -> event.handle()
            is ToxCoreEvent.GroupPeerName -> event.handle()
            is ToxCoreEvent.GroupPeerStatus -> event.handle()
            is ToxCoreEvent.GroupModeration -> event.handle()
            is ToxCoreEvent.GroupMessage -> event.handle()
            is ToxCoreEvent.GroupPrivateMessage -> event.handle()
            is ToxCoreEvent.GroupTopic -> event.handle()
            is ToxCoreEvent.GroupPrivacyState -> event.handle()
            is ToxCoreEvent.GroupPassword -> event.handle()
            is ToxCoreEvent.GroupPeerLimit -> event.handle()
            is ToxCoreEvent.GroupTopicLock -> event.handle()
            is ToxCoreEvent.GroupVoiceState -> event.handle()
            is ToxCoreEvent.ConferenceConnected, is ToxCoreEvent.ConferenceInvite, is ToxCoreEvent.ConferenceMessage -> Unit
            is ToxCoreEvent.ConferencePeerListChanged, is ToxCoreEvent.ConferencePeerName, is ToxCoreEvent.ConferenceTitle -> Unit
            is ToxCoreEvent.FriendLosslessPacket, is ToxCoreEvent.FriendLossyPacket, is ToxCoreEvent.GroupCustomPacket -> Unit
            is ToxCoreEvent.GroupCustomPrivatePacket -> Unit
        }
    }

    private fun ToxCoreEvent.SelfConnectionStatus.handle() {
        profile = profile.copy(connection = connectionStatus)
        publish()
    }

    private fun ToxCoreEvent.FriendRequest.handle() {
        val request = FriendRequest(FriendKey(publicKey.toByteArray().hex()), message.decode(), System.currentTimeMillis())
        store.addRequest(request)
        requests = requests.filter { it.key != request.key } + request
        publish()
        platform.notifyRequest(id, request)
    }

    private fun ToxCoreEvent.FriendMessage.handle() {
        val key = keys[friendNumber] ?: return
        val now = System.currentTimeMillis()
        received(store.addMessage(key, outgoing = false, action = type == ToxMessageType.ACTION, message.decode(), now, true))
    }

    private fun ToxCoreEvent.FriendName.handle() {
        val key = keys[friendNumber] ?: return
        store.setFriendName(key, name.decode())
        updateFriend(key) { it.copy(name = name.decode()) }
    }

    private fun ToxCoreEvent.FriendStatusMessage.handle() {
        val key = keys[friendNumber] ?: return
        store.setFriendStatusMessage(key, message.decode())
        updateFriend(key) { it.copy(statusMessage = message.decode()) }
    }

    private fun ToxCoreEvent.FriendStatus.handle() {
        val key = keys[friendNumber] ?: return
        updateFriend(key) { it.copy(status = status) }
    }

    private fun ToxCoreEvent.FriendConnectionStatus.handle() {
        val key = keys[friendNumber] ?: return
        val wasOnline = friends[key]?.online == true
        updateFriend(key) { it.copy(connection = connectionStatus, typing = false) }
        if (connectionStatus == ToxConnection.NONE) {
            receipts.remove(key)
            stopTransfers(key)
            calls.friendGone(key)
        } else if (!wasOnline) {
            store.undelivered(key).forEach(::sendToTox)
            store.unfinishedFiles(key, outgoing = true).forEach(::offerFile)
            sendAvatar(key)
        }
    }

    private fun ToxCoreEvent.FriendTyping.handle() {
        val key = keys[friendNumber] ?: return
        updateFriend(key) { it.copy(typing = typing) }
    }

    private fun ToxCoreEvent.FriendReadReceipt.handle() {
        val key = keys[friendNumber] ?: return
        val id = receipts[key]?.remove(messageId) ?: return
        store.markDelivered(id)
        refresh(id)
    }

    private fun ToxCoreEvent.FileRecv.handle() {
        val key = keys[friendNumber] ?: return
        val fileId = tox.fileGetFileId(friendNumber, fileNumber).toByteArray().hex()
        if (kind == ToxFileKind.AVATAR) {
            receiveAvatar(key, fileNumber, fileSize, fileId)
        } else {
            receiveFile(key, fileNumber, fileSize, fileId, safeName(filename.decode()))
        }
    }

    private fun ToxCoreEvent.FileRecvChunk.handle() {
        val key = keys[friendNumber] ?: return
        val transfer = transfers[TransferKey(key, fileNumber)] ?: return
        if (data.isEmpty()) {
            finish(key, fileNumber, FileState.DONE)
            return
        }
        transfer.channel?.write(ByteBuffer.wrap(data), position)
        transfer.done = position + data.size
        progressChanged = true
    }

    private fun ToxCoreEvent.FileRecvControl.handle() {
        val key = keys[friendNumber] ?: return
        val transfer = transfers[TransferKey(key, fileNumber)] ?: return
        when (control) {
            ToxFileControl.CANCEL -> {
                finish(key, fileNumber, FileState.CANCELLED)
            }

            ToxFileControl.RESUME -> {
                transfer.message?.let {
                    store.setFileState(it, FileState.ACTIVE, transfer.done)
                    refresh(it)
                }
            }

            ToxFileControl.PAUSE -> {}
        }
    }

    private fun ToxCoreEvent.FileChunkRequest.handle() {
        val key = keys[friendNumber] ?: return
        val transfer = transfers[TransferKey(key, fileNumber)] ?: return
        if (length == 0L) {
            finish(key, fileNumber, FileState.DONE)
            return
        }
        val chunk = ByteBuffer.allocate(length.toInt())
        transfer.channel?.read(chunk, position)
        try {
            tox.fileSendChunk(friendNumber, fileNumber, position, chunk.array().copyOf(chunk.position()))
            transfer.done = position + length
            progressChanged = true
        } catch (_: ToxFileSendChunkException) {
            // toxcore asks for the chunk again.
        }
    }

    private fun ToxCoreEvent.GroupInvite.handle() {
        val friend = keys[friendNumber] ?: return
        val invite = GroupInvite(friend, groupName.decode(), inviteData.hex(), now())
        store.addInvite(invite)
        invites = invites.filterNot { it.friend == friend && it.name == invite.name } + invite
        publish()
        friends[friend]?.let { platform.notifyInvite(id, it, invite) }
    }

    private fun ToxCoreEvent.GroupSelfJoin.handle() {
        val id = groupOf(groupNumber) ?: return
        groups[id]?.let { groups[id] = it.copy(problem = null) }
        // toxcore saves a group only once it's in, with the group's state: until now, the save lacked it.
        unsaved = true
        refreshGroup(id)
    }

    private fun ToxCoreEvent.GroupJoinFail.handle() {
        val id = groupOf(groupNumber) ?: return
        val problem =
            when (failType) {
                ToxGroupJoinFail.PEER_LIMIT -> "The group is full."
                ToxGroupJoinFail.INVALID_PASSWORD -> "The password is wrong."
                ToxGroupJoinFail.UNKNOWN -> "Couldn't join the group."
            }
        groups[id]?.let { groups[id] = it.copy(problem = problem) }
        publish()
    }

    private fun ToxCoreEvent.GroupPeerJoin.handle() {
        val id = groupOf(groupNumber) ?: return
        peer(id, groupNumber, peerId)
        publishPeers(id)
    }

    private fun ToxCoreEvent.GroupPeerExit.handle() {
        val id = groupOf(groupNumber) ?: return
        if (exitType == ToxGroupExitType.SELF_DISCONNECTED) {
            peers[id]?.clear()
            refreshGroup(id)
            return
        }
        peers[id]?.remove(peerId)
        publishPeers(id)
    }

    private fun ToxCoreEvent.GroupPeerName.handle() = groupPeerChanged(groupNumber, peerId)

    private fun ToxCoreEvent.GroupPeerStatus.handle() = groupPeerChanged(groupNumber, peerId)

    private fun ToxCoreEvent.GroupModeration.handle() {
        val id = groupOf(groupNumber) ?: return
        // A kick also comes as the peer leaving; anything else changes a role, maybe ours.
        if (modType != ToxGroupModEvent.KICK) peer(id, groupNumber, targetPeerId)
        unsaved = true
        refreshGroup(id)
    }

    private fun ToxCoreEvent.GroupMessage.handle() = groupMessageArrived(groupNumber, peerId, messageType, message, private = false)

    private fun ToxCoreEvent.GroupPrivateMessage.handle() = groupMessageArrived(groupNumber, peerId, messageType, message, private = true)

    private fun ToxCoreEvent.GroupTopic.handle() = groupChanged(groupNumber)

    private fun ToxCoreEvent.GroupPrivacyState.handle() = groupChanged(groupNumber)

    private fun ToxCoreEvent.GroupPassword.handle() = groupChanged(groupNumber)

    private fun ToxCoreEvent.GroupPeerLimit.handle() = groupChanged(groupNumber)

    private fun ToxCoreEvent.GroupTopicLock.handle() = groupChanged(groupNumber)

    private fun ToxCoreEvent.GroupVoiceState.handle() = groupChanged(groupNumber)

    /** Something toxcore saves of a group changed. */
    private fun groupChanged(number: ToxGroupNumber) {
        unsaved = true
        groupOf(number)?.let(::refreshGroup)
    }

    private fun groupPeerChanged(
        number: ToxGroupNumber,
        peer: ToxGroupPeerNumber,
    ) {
        val id = groupOf(number) ?: return
        peer(id, number, peer)
        publishPeers(id)
    }

    private fun groupMessageArrived(
        number: ToxGroupNumber,
        peerNumber: ToxGroupPeerNumber,
        type: ToxMessageType,
        message: ByteArray,
        private: Boolean,
    ) {
        val id = groupOf(number) ?: return
        val peer = peers[id]?.get(peerNumber) ?: peer(id, number, peerNumber) ?: return
        val stored =
            store.addGroupMessage(
                GroupMessage(
                    MessageId(0),
                    id,
                    peer.key,
                    peer.name,
                    false,
                    type == ToxMessageType.ACTION,
                    private,
                    message.decode(),
                    now(),
                    true,
                ),
            )
        val reading = visible && groupChat?.group == id
        groupChat?.let { if (it.group == id) groupChat = it.copy(messages = it.messages + stored) }
        val group = groups[id] ?: return
        val unread = if (reading) group.unread else group.unread + 1
        groups[id] = group.copy(last = stored, unread = unread)
        publish()
        if (reading) return
        store.setGroupUnread(id, unread)
        // Groups can be busy: only messages that mention us, or are for us alone, notify.
        val name = profile.name
        if (private || (name.isNotBlank() && stored.text.contains(name, ignoreCase = true))) {
            platform.notifyGroupMessage(this.id, groups[id]!!, stored)
        }
    }

    private fun addToGroupChat(message: GroupMessage) {
        groupChat?.let { if (it.group == message.group) groupChat = it.copy(messages = it.messages + message) }
        groups[message.group]?.let { groups[message.group] = it.copy(last = message) }
        publish()
    }

    /** Whether the user sees [key]'s chat now. */
    private fun reading(key: FriendKey) = visible && chat?.friend == key

    /** A new message from a friend: in the chat, and counted and shown as a notification if the chat isn't open. */
    private fun received(message: Message) {
        val key = message.friend
        val reading = reading(key)
        chat?.let { if (it.friend == key) chat = it.copy(messages = it.messages + message) }
        val friend = friends[key] ?: return
        val unread = if (reading) friend.unread else friend.unread + 1
        if (!reading) store.setUnread(key, unread)
        updateFriend(key) { it.copy(last = message, unread = unread) }
        if (!reading) friends[key]?.let { platform.notifyMessage(id, it, message) }
    }

    /** A call that ended goes in its chat; one we missed counts as unread. */
    private fun noteCall(
        call: Call,
        note: CallNote,
    ) {
        val message = store.addCall(call.friend, call.outgoing, note, now())
        if (!call.outgoing && note.outcome == CallOutcome.MISSED) received(message) else addToChat(message)
    }

    private fun addToChat(message: Message) {
        chat?.let { if (it.friend == message.friend) chat = it.copy(messages = it.messages + message) }
        updateFriend(message.friend) { it.copy(last = message) }
    }

    /** Shows the stored state of a message wherever it's on screen. */
    private fun refresh(id: MessageId) {
        val message = store.messageById(id)
        chat?.let { open ->
            if (open.friend == message.friend) chat = open.copy(messages = open.messages.map { if (it.id == id) message else it })
        }
        updateFriend(message.friend) { friend -> friend.copy(last = friend.last?.let { if (it.id == id) message else it }) }
    }

    /** Hands a stored message to toxcore if the friend is online. If not, it goes when they come online. */
    private fun sendToTox(message: Message) {
        val number = numbers[message.friend] ?: return
        if (friends[message.friend]?.online != true) return
        val type = if (message.action) ToxMessageType.ACTION else ToxMessageType.NORMAL
        try {
            val id = tox.friendSendMessage(number, type, message.text.encodeToByteArray())
            receipts.getOrPut(message.friend) { HashMap() }[id] = message.id
        } catch (_: ToxFriendSendMessageException) {
            // Still undelivered in the store: sent again when the friend next comes online.
        }
    }

    // Files.

    /** Offers a stored file to its friend, if they're online. The same file id lets them go on where they stopped. */
    private fun offerFile(message: Message) {
        val file = message.file ?: return
        val number = numbers[message.friend] ?: return
        if (friends[message.friend]?.online != true) return
        try {
            val fileNumber =
                tox.fileSend(
                    number,
                    ToxFileKind.DATA,
                    file.size,
                    ToxFileId(store.fileId(message.id).unhex()),
                    file.name.encodeToByteArray(),
                )
            val transfer = Transfer(message.id, outgoing = true, file.file, file.size, file.done)
            transfer.channel = FileChannel.open(file.file.toPath(), StandardOpenOption.READ)
            transfers[TransferKey(message.friend, fileNumber)] = transfer
        } catch (_: ToxFileSendException) {
            // Offered again when the friend next comes online.
        }
    }

    private fun receiveFile(
        key: FriendKey,
        fileNumber: ToxFileNumber,
        size: Long,
        fileId: String,
        name: String,
    ) {
        // A file we already have part of comes again after a reconnection: it goes on where it stopped.
        val known = store.incomingFile(key, fileId)
        val message = known ?: store.addFile(key, false, name, size, fileId, File(File(files, randomHex(DIR_ID)), name), now())
        val file = message.file!!
        transfers[TransferKey(key, fileNumber)] = Transfer(message.id, outgoing = false, file.file, size, file.done)
        if (known == null) {
            received(message)
        } else if (file.state == FileState.ACTIVE) {
            startReceiving(key, fileNumber)
        }
    }

    private fun startReceiving(
        key: FriendKey,
        fileNumber: ToxFileNumber,
    ) {
        val transfer = transfers[TransferKey(key, fileNumber)] ?: return
        val number = numbers[key] ?: return
        transfer.file.parentFile.mkdirs()
        transfer.channel = FileChannel.open(transfer.file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        // What arrived before is in the file, so the friend sends only the rest.
        val have = minOf(transfer.done, transfer.file.length())
        try {
            if (have > 0) tox.fileSeek(number, fileNumber, have)
            transfer.done = have
            tox.fileControl(number, fileNumber, ToxFileControl.RESUME)
        } catch (_: ToxFileControlException) {
            // Gone already: the next offer starts it again.
        }
        transfer.message?.let {
            store.setFileState(it, FileState.ACTIVE, have)
            refresh(it)
        }
    }

    /** Ends a transfer for good: done, or cancelled by either side. */
    private fun finish(
        key: FriendKey,
        fileNumber: ToxFileNumber,
        state: FileState,
    ) {
        val transfer = transfers.remove(TransferKey(key, fileNumber)) ?: return
        transfer.close()
        val id = transfer.message
        if (id == null) {
            if (!transfer.outgoing) avatarArrived(key, transfer, state == FileState.DONE)
            return
        }
        if (state == FileState.CANCELLED && !transfer.outgoing) transfer.file.delete()
        store.setFileState(id, state, if (state == FileState.DONE) transfer.size else transfer.done)
        refresh(id)
    }

    /** Remembers how far [key]'s transfers got, for when they come back. */
    private fun stopTransfers(key: FriendKey) {
        val gone = transfers.filterKeys { it.friend == key }
        for ((transferKey, transfer) in gone) {
            transfers.remove(transferKey)
            transfer.close()
            transfer.message?.let { id -> store.setFileState(id, store.messageById(id).file!!.state, transfer.done) }
        }
        if (gone.isNotEmpty()) publish()
    }

    private fun liveTransfer(id: MessageId) = transfers.entries.firstOrNull { it.value.message == id }

    /** Sends a file the user picked. It's copied first, so it can go on after a reconnection or restart. */
    fun sendFile(
        friend: FriendKey,
        picked: PickedFile,
    ) {
        scope.launch(Dispatchers.IO) {
            val name = safeName(picked.name)
            val file = File(File(files, randomHex(DIR_ID)), name)
            file.parentFile.mkdirs()
            picked.open().use { input -> file.outputStream().use { input.copyTo(it) } }
            withContext(thread) {
                val message = store.addFile(friend, true, name, file.length(), randomHex(ToxCoreConstants.FILE_ID_LENGTH), file, now())
                addToChat(message)
                offerFile(message)
            }
        }
    }

    fun acceptFile(message: Message) =
        on {
            val live = liveTransfer(message.id)
            if (live != null) {
                startReceiving(live.key.friend, live.key.file)
            } else {
                // The friend offers it again when they're back, and then it starts.
                store.setFileState(message.id, FileState.ACTIVE, 0)
                refresh(message.id)
            }
        }

    /** Declines, or stops, a file in either direction. */
    fun cancelFile(message: Message) =
        on {
            val live = liveTransfer(message.id)
            if (live != null) {
                numbers[live.key.friend]?.let {
                    try {
                        tox.fileControl(it, live.key.file, ToxFileControl.CANCEL)
                    } catch (_: ToxFileControlException) {
                        // Gone already.
                    }
                }
                finish(live.key.friend, live.key.file, FileState.CANCELLED)
            } else {
                if (!message.outgoing) message.file?.file?.delete()
                store.setFileState(message.id, FileState.CANCELLED, message.file?.done ?: 0)
                refresh(message.id)
            }
        }

    // Avatars.

    private fun sendAvatar(key: FriendKey) {
        val number = numbers[key] ?: return
        val avatar = profile.avatar
        try {
            if (avatar == null) {
                // No picture: tells the friend to drop the one they have.
                tox.fileSend(number, ToxFileKind.AVATAR, 0, ToxFileId(ByteArray(ToxCoreConstants.FILE_ID_LENGTH)), byteArrayOf())
                return
            }
            val size = avatar.file.length()
            val fileNumber = tox.fileSend(number, ToxFileKind.AVATAR, size, ToxFileId(avatar.hash.unhex()), AVATAR_NAME)
            val transfer = Transfer(null, outgoing = true, avatar.file, size, 0)
            transfer.channel = FileChannel.open(avatar.file.toPath(), StandardOpenOption.READ)
            transfers[TransferKey(key, fileNumber)] = transfer
        } catch (_: ToxFileSendException) {
            // Sent when the friend next comes online.
        }
    }

    private fun receiveAvatar(
        key: FriendKey,
        fileNumber: ToxFileNumber,
        size: Long,
        hash: String,
    ) {
        val number = numbers[key] ?: return
        val have = friends[key]?.avatar
        if (size == 0L || size > MAX_AVATAR_SIZE || have?.hash == hash) {
            if (size == 0L && have != null) {
                have.file.delete()
                store.setAvatarHash(key, "")
                updateFriend(key) { it.copy(avatar = null) }
            }
            try {
                tox.fileControl(number, fileNumber, ToxFileControl.CANCEL)
            } catch (_: ToxFileControlException) {
                // Gone already.
            }
            return
        }
        avatars.mkdirs()
        val temporary = File(avatars, "${key.hex}.tmp")
        val transfer = Transfer(null, outgoing = false, temporary, size, 0)
        transfer.channel =
            FileChannel.open(temporary.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        transfers[TransferKey(key, fileNumber)] = transfer
        try {
            tox.fileControl(number, fileNumber, ToxFileControl.RESUME)
        } catch (_: ToxFileControlException) {
            transfers.remove(TransferKey(key, fileNumber))
            transfer.close()
        }
    }

    private fun avatarArrived(
        key: FriendKey,
        transfer: Transfer,
        complete: Boolean,
    ) {
        if (!complete || transfer.file.length() != transfer.size) {
            transfer.file.delete()
            return
        }
        val file = avatarFile(key)
        Files.move(transfer.file.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        // The hash of what came, not what the friend said it would be.
        val picture = picture(file) ?: return
        store.setAvatarHash(key, picture.hash)
        updateFriend(key) { it.copy(avatar = picture) }
    }

    /** Sets our picture, made into a small PNG, or removes it. Returns false if [image] isn't a picture. */
    suspend fun setAvatar(image: ByteArray?): Boolean {
        val png = image?.let { withContext(Dispatchers.IO) { platform.avatarPng(it, AVATAR_PIXELS) } ?: return false }
        withContext(thread) {
            if (png == null) ownAvatar.delete() else writeAtomically(ownAvatar, png)
            profile = profile.copy(avatar = picture(ownAvatar))
            publish()
            friends.values.filter { it.online }.forEach { sendAvatar(it.key) }
        }
        return true
    }

    // Groups.

    /** Our name for groups: groups need one. */
    private fun groupSelfName() = profile.name.ifBlank { DEFAULT_GROUP_NAME }.encodeToByteArray()

    /** Starts a group. Returns why it failed, or null. */
    suspend fun createGroup(
        name: String,
        private: Boolean,
    ): String? =
        withContext(thread) {
            val privacy = if (private) ToxGroupPrivacyState.PRIVATE else ToxGroupPrivacyState.PUBLIC
            try {
                val number = tox.groupNew(privacy, name.encodeToByteArray(), groupSelfName())
                groupOf(number)
                unsaved = true
                null
            } catch (e: ToxGroupNewException) {
                "Couldn't start the group: ${e.code}."
            }
        }

    /** Joins a public group by its chat ID. Returns why it failed, or null. */
    suspend fun joinGroup(
        chatId: String,
        password: String,
    ): String? =
        withContext(thread) {
            val bytes = chatId.trim().unhexOrNull()
            if (bytes == null || bytes.size != ToxCoreConstants.GROUP_CHAT_ID_SIZE) {
                return@withContext "A chat ID is ${ToxCoreConstants.GROUP_CHAT_ID_SIZE * 2} hex digits."
            }
            if (GroupId(bytes.hex()) in groups) return@withContext "You're in this group already."
            try {
                groupOf(tox.groupJoin(ToxGroupChatId(bytes), groupSelfName(), password.encodeToByteArray()))
                unsaved = true
                null
            } catch (e: ToxGroupJoinException) {
                "Couldn't join the group: ${e.code}."
            }
        }

    fun acceptInvite(invite: GroupInvite) =
        on {
            val friend = numbers[invite.friend] ?: return@on
            try {
                groupOf(tox.groupInviteAccept(friend, invite.data.unhex(), groupSelfName(), byteArrayOf()))
                unsaved = true
            } catch (_: ToxGroupInviteAcceptException) {
                // Out of date: the friend can invite us again.
            }
            declineInvite(invite)
        }

    fun declineInvite(invite: GroupInvite) =
        on {
            store.deleteInvite(invite)
            invites = invites - invite
            publish()
        }

    fun inviteFriend(
        group: GroupId,
        friend: FriendKey,
    ) = on {
        val number = groupNumbers[group] ?: return@on
        try {
            tox.groupInviteFriend(number, numbers[friend] ?: return@on)
        } catch (_: ToxGroupInviteFriendException) {
            // Offline friends can't be invited: the page only offers online ones.
        }
    }

    fun leaveGroup(group: GroupId) =
        on {
            groupNumbers.remove(group)?.let {
                runCatching { tox.groupLeave(it, byteArrayOf()) }
                groupIds.remove(it)
            }
            groups.remove(group)
            peers.remove(group)
            store.deleteGroup(group)
            if (groupChat?.group == group) groupChat = null
            unsaved = true
            publish()
        }

    fun sendGroupMessage(
        group: GroupId,
        text: String,
        action: Boolean = false,
    ) = on {
        if (text.isBlank()) return@on
        val number = groupNumbers[group] ?: return@on
        val self = groups[group]?.peers?.firstOrNull { it.self }
        for (part in splitMessage(text, ToxCoreConstants.GROUP_MAX_MESSAGE_LENGTH)) {
            val type = if (action) ToxMessageType.ACTION else ToxMessageType.NORMAL
            val sent =
                try {
                    tox.groupSendMessage(number, type, part.encodeToByteArray())
                    true
                } catch (_: ToxGroupSendMessageException) {
                    false
                }
            val message =
                GroupMessage(MessageId(0), group, self?.key ?: "", self?.name ?: profile.name, true, action, false, part, now(), sent)
            addToGroupChat(store.addGroupMessage(message))
        }
    }

    /** Runs a group setting and shows the result; toxcore says why if it's not allowed. */
    private fun groupSetting(
        group: GroupId,
        set: (ToxGroupNumber) -> Unit,
    ) = on {
        val number = groupNumbers[group] ?: return@on
        runCatching { set(number) }
        unsaved = true
        refreshGroup(group)
    }

    fun setTopic(
        group: GroupId,
        topic: String,
    ) = groupSetting(group) { tox.groupSetTopic(it, topic.encodeToByteArray()) }

    fun setGroupPrivacy(
        group: GroupId,
        private: Boolean,
    ) = groupSetting(group) { tox.groupSetPrivacyState(it, if (private) ToxGroupPrivacyState.PRIVATE else ToxGroupPrivacyState.PUBLIC) }

    fun setGroupPassword(
        group: GroupId,
        password: String,
    ) = groupSetting(group) { tox.groupSetPassword(it, password.encodeToByteArray()) }

    fun setPeerLimit(
        group: GroupId,
        limit: Int,
    ) = groupSetting(group) { tox.groupSetPeerLimit(it, limit) }

    fun setTopicLock(
        group: GroupId,
        locked: Boolean,
    ) = groupSetting(group) { tox.groupSetTopicLock(it, if (locked) ToxGroupTopicLock.ENABLED else ToxGroupTopicLock.DISABLED) }

    private fun peerNumber(
        group: GroupId,
        key: String,
    ) = peers[group]?.entries?.firstOrNull { it.value.key == key }?.key

    fun setRole(
        group: GroupId,
        peer: String,
        role: ToxGroupRole,
    ) = groupSetting(group) { number -> peerNumber(group, peer)?.let { tox.groupSetRole(number, it, role) } }

    fun kick(
        group: GroupId,
        peer: String,
    ) = groupSetting(group) { number -> peerNumber(group, peer)?.let { tox.groupKickPeer(number, it) } }

    /** Shows [group]'s chat, or none. */
    fun openGroup(group: GroupId?) =
        on {
            groupChat =
                group?.let {
                    val messages = store.groupMessages(it, null, PAGE)
                    GroupChat(it, messages, hasOlder = messages.size == PAGE)
                }
            markRead()
            publish()
        }

    fun loadOlderGroupMessages() =
        on {
            val open = groupChat ?: return@on
            val older = store.groupMessages(open.group, open.messages.firstOrNull()?.id, PAGE)
            groupChat = open.copy(messages = older + open.messages, hasOlder = older.size == PAGE)
            publish()
        }

    private fun on(block: () -> Unit) {
        scope.launch { block() }
    }

    /** Calls [friend], with video if [video]. */
    fun call(
        friend: FriendKey,
        video: Boolean,
    ) = on { calls.call(friend, video) }

    fun answer(video: Boolean) = on { calls.answer(video) }

    /** Ends the call, or says no to one that rings. */
    fun hangUp() = on { calls.hangUp() }

    fun setMuted(muted: Boolean) = on { calls.setMuted(muted) }

    fun setVideo(video: Boolean) = on { calls.setVideo(video) }

    fun switchCamera() = on { calls.switchCamera() }

    fun setSpeaker(speaker: Boolean) = on { calls.setSpeaker(speaker) }

    fun send(
        friend: FriendKey,
        text: String,
        action: Boolean = false,
    ) = on {
        if (text.isBlank()) return@on
        for (part in splitMessage(text)) {
            val message = store.addMessage(friend, outgoing = true, action, part, now(), delivered = false)
            addToChat(message)
            sendToTox(message)
        }
    }

    /** Adds a friend by Tox ID. Returns why it failed, or null. */
    suspend fun addFriend(
        id: String,
        message: String,
    ): String? =
        withContext(thread) {
            val bytes = id.trim().removePrefix("tox:").unhexOrNull()
            if (bytes == null ||
                bytes.size != ToxCoreConstants.ADDRESS_SIZE
            ) {
                return@withContext "A Tox ID is ${ToxCoreConstants.ADDRESS_SIZE * 2} hex digits."
            }
            try {
                val number = tox.friendAdd(ToxAddress(bytes), message.ifEmpty { DEFAULT_REQUEST }.encodeToByteArray())
                addFriendNumber(number)
                null
            } catch (e: ToxFriendAddException) {
                when (e.code) {
                    ToxFriendAddException.Code.OWN_KEY -> "That's your own Tox ID."
                    ToxFriendAddException.Code.ALREADY_SENT -> "You've already added this friend."
                    ToxFriendAddException.Code.BAD_CHECKSUM -> "That Tox ID has a typo in it."
                    ToxFriendAddException.Code.SET_NEW_NOSPAM -> "Your friend has a new Tox ID now: sent again."
                    ToxFriendAddException.Code.TOO_LONG -> "The message is too long."
                    else -> "Couldn't add the friend: ${e.code}."
                }
            }
        }

    private fun addFriendNumber(number: ToxFriendNumber) {
        val key = FriendKey(tox.friendGetPublicKey(number).toByteArray().hex())
        store.addFriend(key)
        numbers[key] = number
        keys[number] = key
        friends[key] = newFriend(key, StoredFriend("", "", 0))
        unsaved = true
        publish()
    }

    fun accept(request: FriendRequest) =
        on {
            val key = ToxPublicKey(request.key.hex.unhex())
            val number =
                try {
                    tox.friendAddNorequest(key)
                } catch (e: ToxFriendAddException) {
                    // Already a friend: accepting again just clears the request.
                    if (e.code != ToxFriendAddException.Code.ALREADY_SENT) throw e
                    tox.friendByPublicKey(key)
                }
            if (number !in keys) addFriendNumber(number)
            decline(request.key)
        }

    fun decline(request: FriendRequest) = on { decline(request.key) }

    private fun decline(key: FriendKey) {
        store.deleteRequest(key)
        requests = requests.filter { it.key != key }
        publish()
    }

    fun delete(friend: FriendKey) =
        on {
            if (calls.call?.friend == friend) calls.hangUp()
            stopTransfers(friend)
            numbers.remove(friend)?.let {
                tox.friendDelete(it)
                keys.remove(it)
            }
            friends
                .remove(friend)
                ?.avatar
                ?.file
                ?.delete()
            receipts.remove(friend)
            store.files(friend).forEach { it.parentFile.deleteRecursively() }
            store.deleteFriend(friend)
            if (chat?.friend == friend) chat = null
            unsaved = true
            publish()
        }

    fun setName(name: String) =
        on {
            tox.setName(name.encodeToByteArray())
            profile = profile.copy(name = name)
            groupNumbers.values.forEach { runCatching { tox.groupSelfSetName(it, groupSelfName()) } }
            unsaved = true
            publish()
        }

    fun setStatusMessage(message: String) =
        on {
            tox.setStatusMessage(message.encodeToByteArray())
            profile = profile.copy(statusMessage = message)
            unsaved = true
            publish()
        }

    fun setStatus(status: ToxUserStatus) =
        on {
            tox.setStatus(status)
            profile = profile.copy(status = status)
            unsaved = true
            publish()
        }

    /** Shows [friend]'s chat, or none. */
    fun open(friend: FriendKey?) =
        on {
            chat =
                friend?.let {
                    val messages = store.messages(it, null, PAGE)
                    Chat(it, messages, hasOlder = messages.size == PAGE)
                }
            markRead()
            publish()
        }

    fun loadOlder() =
        on {
            val open = chat ?: return@on
            val older = store.messages(open.friend, open.messages.firstOrNull()?.id, PAGE)
            chat = open.copy(messages = older + open.messages, hasOlder = older.size == PAGE)
            publish()
        }

    /** Whether the window is in front, so the open chat counts as read. */
    fun setVisible(visible: Boolean) =
        on {
            this.visible = visible
            markRead()
            publish()
        }

    private fun markRead() {
        if (!visible) return
        groupChat?.group?.let { id ->
            platform.clearGroupNotifications(this.id, id)
            groups[id]?.let { if (it.unread > 0) groups[id] = it.copy(unread = 0) }
            store.setGroupUnread(id, 0)
        }
        val key = chat?.friend ?: return
        platform.clearNotifications(id, key)
        if (friends[key]?.unread == 0) return
        store.setUnread(key, 0)
        friends[key]?.let { friends[key] = it.copy(unread = 0) }
    }

    fun setTyping(
        friend: FriendKey,
        typing: Boolean,
    ) = on {
        val number = numbers[friend] ?: return@on
        if (friends[friend]?.online == true) tox.setTyping(number, typing)
    }

    fun updateSettings(settings: Settings) =
        on {
            if (settings == this.settings) return@on
            store.saveSettings(settings)
            restart = true
            this.settings = settings
            publish()
        }

    private companion object {
        const val PROFILE = "profile.tox"
        const val PAGE = 100
        const val BOOTSTRAP_NODES = 4
        const val BOOTSTRAP_INTERVAL_MS = 10_000L
        const val PROGRESS_INTERVAL_MS = 250L
        const val DEFAULT_REQUEST = "Hi, I'd like to add you as a friend."
        const val DEFAULT_GROUP_NAME = "Tox user"

        /** Bytes of randomness in the name of each file's directory. */
        const val DIR_ID = 8

        /** Our picture's width and height: enough for the largest place it's shown. */
        const val AVATAR_PIXELS = 256

        /** Friends' pictures bigger than this aren't taken. */
        const val MAX_AVATAR_SIZE = 1L shl 20
        val AVATAR_NAME = "avatar.png".encodeToByteArray()
    }
}

private val random = SecureRandom()

private fun randomHex(bytes: Int) = ByteArray(bytes).also(random::nextBytes).hex()

private fun now() = System.currentTimeMillis()

private fun writeAtomically(
    file: File,
    bytes: ByteArray,
) {
    val temporary = File(file.parentFile, "${file.name}.tmp")
    temporary.writeBytes(bytes)
    Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}

/** A friend's file name, without anything that would put it in another directory. */
internal fun safeName(name: String): String =
    name
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .filterNot { it.isISOControl() }
        .trim()
        .trimStart('.')
        .take(MAX_FILE_NAME)
        .ifEmpty { "file" }

/** Longer names are cut: file systems allow 255 bytes. */
private const val MAX_FILE_NAME = 120

private fun ByteArray.decode() = decodeToString()

internal fun ByteArray.hex() = joinToString("") { "%02X".format(it) }

private fun String.unhex(): ByteArray = unhexOrNull() ?: error("not hex: $this")

private fun String.unhexOrNull(): ByteArray? {
    if (length % 2 != 0) return null
    return ByteArray(length / 2) { i -> (substring(2 * i, 2 * i + 2).toIntOrNull(HEX) ?: return null).toByte() }
}

private const val HEX = 16

/** Splits text into messages toxcore takes, at a space or line end where it can, never inside a character. */
internal fun splitMessage(
    text: String,
    max: Int = ToxCoreConstants.MAX_MESSAGE_LENGTH,
): List<String> {
    val parts = mutableListOf<String>()
    var rest = text
    while (rest.encodeToByteArray().size > max) {
        var end = 0
        var bytes = 0
        while (end < rest.length) {
            val codePoint = rest.codePointAt(end)
            val size = String(Character.toChars(codePoint)).encodeToByteArray().size
            if (bytes + size > max) break
            bytes += size
            end += Character.charCount(codePoint)
        }
        val space = rest.lastIndexOfAny(charArrayOf(' ', '\n'), end - 1)
        val cut = if (space > end / 2) space + 1 else end
        parts += rest.substring(0, cut)
        rest = rest.substring(cut)
    }
    parts += rest
    return parts
}
