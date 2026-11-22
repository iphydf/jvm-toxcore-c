package chat.tox.ktox

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.file.Files

/** A profile, by the name of its directory under the profiles directory. */
@JvmInline
value class ProfileId(
    val name: String,
)

/**
 * Who has the one call there can be: one microphone and one speaker go to one profile at a time.
 * Profiles take it on their own Tox threads.
 */
class CallLine {
    private val holder = MutableStateFlow<ProfileId?>(null)

    /** The profile with a call, ringing, calling out or talking. */
    val profile: StateFlow<ProfileId?> = holder.asStateFlow()

    /** Takes the line for [profile], unless another profile has it. */
    internal fun take(profile: ProfileId) = holder.compareAndSet(null, profile) || holder.value == profile

    internal fun release(profile: ProfileId) {
        holder.compareAndSet(profile, null)
    }
}

/**
 * Every profile in [root]'s profiles directory, each with its own Tox and all online at once, and the one the
 * UI shows. The UI thread uses it.
 */
class Profiles internal constructor(
    private val root: File,
    private val platform: Platform,
    /** Where to join the network: the built-in list, or a test's own nodes. */
    private val nodes: List<Node>,
) {
    constructor(root: File, platform: Platform) : this(root, platform, builtInNodes)

    private val dir = File(root, "profiles")
    private val shownFile = File(dir, SHOWN)

    val line = CallLine()

    private val all: MutableStateFlow<List<ToxClient>>
    private val current: MutableStateFlow<ToxClient>

    /** In the order they were made. */
    val clients: StateFlow<List<ToxClient>>

    val shown: StateFlow<ToxClient>

    private val stays = MutableStateFlow(platform.stayConnected)

    /** On phones: whether Tox stays connected while the app is closed. */
    val stayConnected: StateFlow<Boolean> = stays.asStateFlow()

    init {
        dir.mkdirs()
        moveOldProfile()
        val numbers =
            dir
                .listFiles()
                .orEmpty()
                .filter { it.isDirectory }
                .mapNotNull { it.name.toIntOrNull() }
                .sorted()
        val clients = numbers.ifEmpty { listOf(1) }.map(::client)
        val shownName = shownFile.takeIf { it.exists() }?.readText()?.trim()
        all = MutableStateFlow(clients)
        current = MutableStateFlow(clients.firstOrNull { it.id.name == shownName } ?: clients.first())
        this.clients = all.asStateFlow()
        shown = current.asStateFlow()
    }

    /** Profile [number], made if it's new: a profile is its directory. */
    private fun client(number: Int): ToxClient {
        val home = File(dir, "$number").apply { mkdirs() }
        return ToxClient(home, ProfileId("$number"), platform, line, nodes)
    }

    /** The profile with the call, if there's one. */
    val caller: ToxClient? get() = all.value.firstOrNull { it.id == line.profile.value }

    fun start() = all.value.forEach(ToxClient::start)

    /** Saves and stops them all. */
    fun close() = all.value.forEach(ToxClient::close)

    fun show(profile: ProfileId) {
        val client = all.value.firstOrNull { it.id == profile } ?: return
        current.value = client
        shownFile.writeText(profile.name)
    }

    /** Makes a new profile, starts it and shows it. */
    fun add() {
        val client = client(all.value.maxOf { it.id.name.toInt() } + 1)
        client.start()
        all.value += client
        show(client.id)
    }

    /** Stops [profile] and deletes it with its chats. The last one stays, as does one in a call. */
    fun delete(profile: ProfileId) {
        val client = all.value.firstOrNull { it.id == profile } ?: return
        if (all.value.size == 1 || line.profile.value == profile) return
        all.value -= client
        if (current.value == client) show(all.value.first().id)
        client.close()
        File(dir, profile.name).deleteRecursively()
        platform.clearProfile(profile)
    }

    fun setStayConnected(on: Boolean) {
        platform.stayConnected = on
        stays.value = on
    }

    /** kTox kept its one profile in [root] itself before it had several: that one becomes the first. */
    private fun moveOldProfile() {
        val first = File(dir, "1")
        for (name in OLD_PROFILE) {
            val old = File(root, name)
            if (!old.exists()) continue
            first.mkdirs()
            Files.move(old.toPath(), File(first, name).toPath())
        }
    }

    private companion object {
        const val SHOWN = "shown"

        /** What a profile's directory has: [ToxClient] says what each is. */
        val OLD_PROFILE =
            listOf("profile.tox", "chats.db", "chats.db-wal", "chats.db-shm", "chats.db-journal", "files", "avatars", "avatar.png")
    }
}
