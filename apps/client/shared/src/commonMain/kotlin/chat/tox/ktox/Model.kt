package chat.tox.ktox

import im.tox.tox4j.core.enums.ToxConnection
import im.tox.tox4j.core.enums.ToxGroupPrivacyState
import im.tox.tox4j.core.enums.ToxGroupRole
import im.tox.tox4j.core.enums.ToxUserStatus
import java.io.File

/** A friend's public key in hex, which names them in the store and the UI. */
@JvmInline
value class FriendKey(
    val hex: String,
)

/** A message's row in the store. */
@JvmInline
value class MessageId(
    val value: Long,
)

/** A bootstrap node: its UDP port, its TCP relay ports and its DHT key. */
internal data class Node(
    val host: String,
    val port: Int,
    val tcpPorts: List<Int>,
    val key: String,
)

/** A picture file and its hash, which changes when the picture does. */
data class Picture(
    val file: File,
    val hash: String,
)

data class Profile(
    val address: String = "",
    val name: String = "",
    val statusMessage: String = "",
    val status: ToxUserStatus = ToxUserStatus.NONE,
    val connection: ToxConnection = ToxConnection.NONE,
    val avatar: Picture? = null,
)

data class Friend(
    val key: FriendKey,
    val name: String,
    val statusMessage: String,
    val status: ToxUserStatus,
    val connection: ToxConnection,
    val typing: Boolean,
    val unread: Int,
    val last: Message?,
    val avatar: Picture?,
) {
    val displayName: String get() = name.ifEmpty { key.hex.take(SHORT_KEY) }
    val online: Boolean get() = connection != ToxConnection.NONE
}

data class FriendRequest(
    val key: FriendKey,
    val message: String,
    val time: Long,
)

data class Message(
    val id: MessageId,
    val friend: FriendKey,
    val outgoing: Boolean,
    val action: Boolean,
    val text: String,
    val time: Long,
    /** Whether the friend got it: incoming messages always are. */
    val delivered: Boolean,
    /** A file instead of text. */
    val file: FileTransfer? = null,
    /** A call instead of text: who called is [outgoing]. */
    val call: CallNote? = null,
)

/** The message as one line, for lists and notifications. */
val Message.preview: String
    get() =
        when {
            file != null -> "📎 ${file.name}"
            call != null -> "${if (call.video) "📹" else "📞"} ${callText()}"
            else -> text
        }

/** A call in a chat: "Call, 3:21", "Missed call" and so on. */
fun Message.callText(): String {
    val call = call ?: return text
    val what = if (call.video) "Video call" else "Call"
    return when (call.outcome) {
        CallOutcome.ANSWERED -> "$what, ${duration(call.seconds)}"
        CallOutcome.MISSED -> if (outgoing) "$what, not answered" else "Missed ${what.lowercase()}"
        CallOutcome.DECLINED -> if (outgoing) "$what, declined" else "Declined ${what.lowercase()}"
    }
}

/** A call's length, like a phone shows it: 0:42, 3:21, 1:02:03. */
fun duration(seconds: Long): String =
    if (seconds >= 3600) {
        "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    } else {
        "%d:%02d".format(seconds / 60, seconds % 60)
    }

data class CallNote(
    val video: Boolean,
    val outcome: CallOutcome,
    /** How long it lasted, if answered. */
    val seconds: Long,
)

/** How a call ended. The numbers are stored, so they stay as they are. */
enum class CallOutcome(
    val value: Int,
) {
    ANSWERED(0),

    /** Nobody answered: the caller gave up, or we were in another call. */
    MISSED(1),

    /** The one called said no. */
    DECLINED(2),
    ;

    companion object {
        fun of(value: Int) = entries.first { it.value == value }
    }
}

/** The one call there can be at a time. */
data class Call(
    val friend: FriendKey,
    /** We called. An incoming call rings until the user answers. */
    val outgoing: Boolean,
    /** Whether it was a video call when it started ringing. */
    val videoCall: Boolean,
    /** Both sides are in the call, since [started]. */
    val answered: Boolean = false,
    val started: Long = 0,
    /** What the friend sends now; while it rings, what they offer. */
    val friendAudio: Boolean = true,
    val friendVideo: Boolean = false,
    val muted: Boolean = false,
    /** Whether we send video. */
    val video: Boolean = false,
    val frontCamera: Boolean = true,
    /** On phones: the loudspeaker rather than the earpiece. */
    val speaker: Boolean = false,
    /** What doesn't work on our side, for the user. */
    val problem: String? = null,
) {
    val ringing: Boolean get() = !answered
}

/** Whether a file can be shown as a picture in the chat. */
val FileTransfer.isImage: Boolean get() = name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

/** Where a file transfer is. The numbers are stored, so they stay as they are. */
enum class FileState(
    val value: Int,
) {
    /** Offered and not accepted yet. */
    OFFERED(0),

    /** Accepted: moving, or waiting for the friend to come back online to go on. */
    ACTIVE(1),
    DONE(2),
    CANCELLED(3),
    ;

    companion object {
        fun of(value: Int) = entries.first { it.value == value }
    }
}

data class FileTransfer(
    val name: String,
    val size: Long,
    val state: FileState,
    /** How many bytes have arrived or gone. */
    val done: Long,
    /** The copy on this device: the whole file for sent ones, what arrived so far for received ones. */
    val file: File,
)

/** A profile's network options for toxcore: changing one restarts Tox. */
data class Settings(
    val udp: Boolean = true,
    val ipv6: Boolean = true,
    val localDiscovery: Boolean = true,
)

data class ClientState(
    val profile: Profile = Profile(),
    val friends: List<Friend> = emptyList(),
    val requests: List<FriendRequest> = emptyList(),
    val settings: Settings = Settings(),
    /** The open chat's messages, oldest first. */
    val chat: Chat? = null,
    /** How far the files moving now have got, in bytes: newer than their messages. */
    val progress: Map<MessageId, Long> = emptyMap(),
    val groups: List<Group> = emptyList(),
    val invites: List<GroupInvite> = emptyList(),
    /** The open group's messages, oldest first. */
    val groupChat: GroupChat? = null,
    val call: Call? = null,
)

data class Chat(
    val friend: FriendKey,
    val messages: List<Message>,
    val hasOlder: Boolean,
)

/** How much of a key the UI shows where a name is missing. */
const val SHORT_KEY = 8

/** A group's chat ID in hex, which names it in the store and the UI. */
@JvmInline
value class GroupId(
    val hex: String,
)

/** Someone in a group. Their key is the one they have in this group only. */
data class Peer(
    val key: String,
    val name: String,
    val role: ToxGroupRole,
    val status: ToxUserStatus,
    val self: Boolean,
) {
    val displayName: String get() = name.ifEmpty { key.take(SHORT_KEY) }
}

data class Group(
    val id: GroupId,
    val name: String,
    val topic: String,
    val privacy: ToxGroupPrivacyState,
    /** Whether we're in: false while joining, or after losing the connection. */
    val connected: Boolean,
    /** Everyone there, us too, moderators first. */
    val peers: List<Peer>,
    val selfRole: ToxGroupRole,
    val password: String,
    val peerLimit: Int,
    val topicLocked: Boolean,
    val unread: Int,
    val last: GroupMessage?,
    /** Why joining failed, if it did. */
    val problem: String?,
) {
    val displayName: String get() = name.ifEmpty { id.hex.take(SHORT_KEY) }
    val moderator: Boolean get() = selfRole == ToxGroupRole.FOUNDER || selfRole == ToxGroupRole.MODERATOR
    val canSetTopic: Boolean get() = moderator || (!topicLocked && selfRole != ToxGroupRole.OBSERVER)
}

data class GroupMessage(
    val id: MessageId,
    val group: GroupId,
    val peerKey: String,
    val peerName: String,
    val outgoing: Boolean,
    val action: Boolean,
    /** Sent to one person only. */
    val private: Boolean,
    val text: String,
    val time: Long,
    /** For ours: whether toxcore took it. Groups have no receipts. */
    val sent: Boolean,
)

data class GroupInvite(
    val friend: FriendKey,
    val name: String,
    /** toxcore's invite data in hex: it's what accepting needs. */
    val data: String,
    val time: Long,
)

data class GroupChat(
    val group: GroupId,
    val messages: List<GroupMessage>,
    val hasOlder: Boolean,
)
