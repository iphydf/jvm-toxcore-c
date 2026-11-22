package chat.tox.ktox

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File

/** What the store keeps about a friend. toxcore's save has no names, so they live here. */
internal data class StoredFriend(
    val name: String,
    val statusMessage: String,
    val unread: Int,
    /** The hash of the friend's avatar we have, or empty. */
    val avatarHash: String = "",
)

/** Chats, friends' details and settings, in SQLite. Only the Tox thread uses it. */
internal class Store(
    path: String,
) : AutoCloseable {
    private val db: SQLiteConnection = BundledSQLiteDriver().open(path)

    init {
        db.execSQL("PRAGMA journal_mode = WAL")
        val version = query("PRAGMA user_version") { it.getLong(0).toInt() }.single()
        if (version == 0) {
            db.execSQL("BEGIN")
            SCHEMA.forEach(db::execSQL)
            db.execSQL("PRAGMA user_version = $SCHEMA_VERSION")
            db.execSQL("COMMIT")
        } else if (version < SCHEMA_VERSION) {
            db.execSQL("BEGIN")
            MIGRATIONS.drop(version - 1).forEach { it(this) }
            db.execSQL("PRAGMA user_version = $SCHEMA_VERSION")
            db.execSQL("COMMIT")
        } else {
            check(version == SCHEMA_VERSION) { "chats.db has schema $version, this kTox knows up to $SCHEMA_VERSION" }
        }
    }

    override fun close() = db.close()

    /** Adds a column ("name TYPE") to [table] unless it's there. */
    private fun addColumn(
        table: String,
        column: String,
    ) {
        val name = column.substringBefore(' ')
        if (name !in query("SELECT name FROM pragma_table_info('$table')") { it.getText(0) }) {
            db.execSQL("ALTER TABLE $table ADD COLUMN $column")
        }
    }

    fun friends(): Map<FriendKey, StoredFriend> =
        query("SELECT key, name, status_message, unread, avatar_hash FROM friends") {
            FriendKey(it.getText(0)) to StoredFriend(it.getText(1), it.getText(2), it.getLong(3).toInt(), it.getText(4))
        }.toMap()

    fun addFriend(key: FriendKey) = update("INSERT OR IGNORE INTO friends (key) VALUES (?)", key.hex)

    fun setFriendName(
        key: FriendKey,
        name: String,
    ) = update("UPDATE friends SET name = ? WHERE key = ?", name, key.hex)

    fun setFriendStatusMessage(
        key: FriendKey,
        statusMessage: String,
    ) = update("UPDATE friends SET status_message = ? WHERE key = ?", statusMessage, key.hex)

    fun setUnread(
        key: FriendKey,
        unread: Int,
    ) = update("UPDATE friends SET unread = ? WHERE key = ?", unread.toLong(), key.hex)

    fun setAvatarHash(
        key: FriendKey,
        hash: String,
    ) = update("UPDATE friends SET avatar_hash = ? WHERE key = ?", hash, key.hex)

    /** The files in [friend]'s chat. */
    fun files(friend: FriendKey): List<File> =
        query("SELECT file_path FROM messages WHERE friend = ? AND file_path IS NOT NULL", friend.hex) { File(it.getText(0)) }

    fun deleteFriend(key: FriendKey) {
        update("DELETE FROM friends WHERE key = ?", key.hex)
        update("DELETE FROM messages WHERE friend = ?", key.hex)
    }

    fun addMessage(
        friend: FriendKey,
        outgoing: Boolean,
        action: Boolean,
        text: String,
        time: Long,
        delivered: Boolean,
    ): Message {
        val id =
            query(
                "INSERT INTO messages (friend, outgoing, action, text, time, delivered) VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                friend.hex,
                outgoing,
                action,
                text,
                time,
                delivered,
            ) { it.getLong(0) }.single()
        return Message(MessageId(id), friend, outgoing, action, text, time, delivered)
    }

    /** A call that ended, in the chat: [outgoing] if we called. */
    fun addCall(
        friend: FriendKey,
        outgoing: Boolean,
        note: CallNote,
        time: Long,
    ): Message {
        val id =
            query(
                """
                INSERT INTO messages (friend, outgoing, action, text, time, delivered, call_video, call_outcome, call_seconds)
                VALUES (?, ?, 0, '', ?, 1, ?, ?, ?) RETURNING id
                """,
                friend.hex,
                outgoing,
                time,
                note.video,
                note.outcome.value.toLong(),
                note.seconds,
            ) { it.getLong(0) }.single()
        return messageById(MessageId(id))
    }

    fun addFile(
        friend: FriendKey,
        outgoing: Boolean,
        name: String,
        size: Long,
        fileId: String,
        file: File,
        time: Long,
    ): Message {
        val id =
            query(
                """
                INSERT INTO messages (friend, outgoing, action, text, time, delivered, file_name, file_size, file_state, file_done,
                    file_id, file_path)
                VALUES (?, ?, 0, '', ?, ?, ?, ?, ?, 0, ?, ?) RETURNING id
                """,
                friend.hex,
                outgoing,
                time,
                !outgoing,
                name,
                size,
                FileState.OFFERED.value.toLong(),
                fileId,
                file.path,
            ) { it.getLong(0) }.single()
        return messageById(MessageId(id))
    }

    fun messageById(id: MessageId): Message = query("$SELECT_MESSAGE WHERE id = ?", id.value, row = ::message).single()

    fun setFileState(
        id: MessageId,
        state: FileState,
        done: Long,
    ) = update("UPDATE messages SET file_state = ?, file_done = ? WHERE id = ?", state.value.toLong(), done, id.value)

    /** The file id of a file message, which a transfer keeps across reconnections. */
    fun fileId(id: MessageId): String = query("SELECT file_id FROM messages WHERE id = ?", id.value) { it.getText(0) }.single()

    /** Files the friend and we haven't finished with: offered or moving. */
    fun unfinishedFiles(
        friend: FriendKey,
        outgoing: Boolean,
    ): List<Message> =
        query(
            "$SELECT_MESSAGE WHERE friend = ? AND outgoing = ? AND file_state IN (?, ?) ORDER BY id",
            friend.hex,
            outgoing,
            FileState.OFFERED.value.toLong(),
            FileState.ACTIVE.value.toLong(),
            row = ::message,
        )

    /** The unfinished file [friend] sends with [fileId], if one is already here. */
    fun incomingFile(
        friend: FriendKey,
        fileId: String,
    ): Message? = unfinishedFiles(friend, outgoing = false).firstOrNull { fileId(it.id) == fileId }

    fun markDelivered(id: MessageId) = update("UPDATE messages SET delivered = 1 WHERE id = ?", id.value)

    /** Sent messages the friend hasn't got yet, oldest first. */
    fun undelivered(friend: FriendKey): List<Message> =
        query("$SELECT_MESSAGE WHERE friend = ? AND outgoing = 1 AND delivered = 0 ORDER BY id", friend.hex, row = ::message)

    /** Up to [limit] messages before [before] (or the newest), oldest first. */
    fun messages(
        friend: FriendKey,
        before: MessageId?,
        limit: Int,
    ): List<Message> =
        query(
            "$SELECT_MESSAGE WHERE friend = ? AND id < ? ORDER BY id DESC LIMIT ?",
            friend.hex,
            before?.value ?: Long.MAX_VALUE,
            limit.toLong(),
            row = ::message,
        ).asReversed()

    fun lastMessage(friend: FriendKey): Message? = messages(friend, null, 1).firstOrNull()

    /** The groups we're in and how many of their messages are unread. */
    fun groups(): Map<GroupId, Int> =
        query("SELECT chat_id, unread FROM groups") { GroupId(it.getText(0)) to it.getLong(1).toInt() }.toMap()

    fun addGroup(id: GroupId) = update("INSERT OR IGNORE INTO groups (chat_id) VALUES (?)", id.hex)

    fun setGroupUnread(
        id: GroupId,
        unread: Int,
    ) = update("UPDATE groups SET unread = ? WHERE chat_id = ?", unread.toLong(), id.hex)

    fun deleteGroup(id: GroupId) {
        update("DELETE FROM groups WHERE chat_id = ?", id.hex)
        update("DELETE FROM group_messages WHERE chat_id = ?", id.hex)
    }

    fun addGroupMessage(message: GroupMessage): GroupMessage {
        val id =
            query(
                """
                INSERT INTO group_messages (chat_id, peer_key, peer_name, outgoing, action, private, text, time, sent)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """,
                message.group.hex,
                message.peerKey,
                message.peerName,
                message.outgoing,
                message.action,
                message.private,
                message.text,
                message.time,
                message.sent,
            ) { it.getLong(0) }.single()
        return message.copy(id = MessageId(id))
    }

    /** Up to [limit] of a group's messages before [before] (or the newest), oldest first. */
    fun groupMessages(
        group: GroupId,
        before: MessageId?,
        limit: Int,
    ): List<GroupMessage> =
        query(
            """
            SELECT id, chat_id, peer_key, peer_name, outgoing, action, private, text, time, sent FROM group_messages
            WHERE chat_id = ? AND id < ? ORDER BY id DESC LIMIT ?
            """,
            group.hex,
            before?.value ?: Long.MAX_VALUE,
            limit.toLong(),
        ) {
            GroupMessage(
                id = MessageId(it.getLong(0)),
                group = GroupId(it.getText(1)),
                peerKey = it.getText(2),
                peerName = it.getText(3),
                outgoing = it.getBoolean(4),
                action = it.getBoolean(5),
                private = it.getBoolean(6),
                text = it.getText(7),
                time = it.getLong(8),
                sent = it.getBoolean(9),
            )
        }.asReversed()

    fun lastGroupMessage(group: GroupId): GroupMessage? = groupMessages(group, null, 1).firstOrNull()

    fun invites(): List<GroupInvite> =
        query("SELECT friend, name, data, time FROM group_invites ORDER BY time") {
            GroupInvite(FriendKey(it.getText(0)), it.getText(1), it.getText(2), it.getLong(3))
        }

    fun addInvite(invite: GroupInvite) =
        update(
            "INSERT OR REPLACE INTO group_invites (friend, name, data, time) VALUES (?, ?, ?, ?)",
            invite.friend.hex,
            invite.name,
            invite.data,
            invite.time,
        )

    fun deleteInvite(invite: GroupInvite) =
        update("DELETE FROM group_invites WHERE friend = ? AND data = ?", invite.friend.hex, invite.data)

    fun requests(): List<FriendRequest> =
        query("SELECT key, message, time FROM requests ORDER BY time") {
            FriendRequest(FriendKey(it.getText(0)), it.getText(1), it.getLong(2))
        }

    fun addRequest(request: FriendRequest) =
        update(
            "INSERT OR REPLACE INTO requests (key, message, time) VALUES (?, ?, ?)",
            request.key.hex,
            request.message,
            request.time,
        )

    fun deleteRequest(key: FriendKey) = update("DELETE FROM requests WHERE key = ?", key.hex)

    fun settings(): Settings {
        val values = query("SELECT name, value FROM settings") { it.getText(0) to it.getText(1) }.toMap()
        val defaults = Settings()
        return Settings(
            udp = values["udp"]?.toBooleanStrict() ?: defaults.udp,
            ipv6 = values["ipv6"]?.toBooleanStrict() ?: defaults.ipv6,
            localDiscovery = values["local_discovery"]?.toBooleanStrict() ?: defaults.localDiscovery,
        )
    }

    fun saveSettings(settings: Settings) {
        listOf(
            "udp" to settings.udp,
            "ipv6" to settings.ipv6,
            "local_discovery" to settings.localDiscovery,
        ).forEach { (name, value) -> update("INSERT OR REPLACE INTO settings (name, value) VALUES (?, ?)", name, "$value") }
    }

    private fun message(row: SQLiteStatement) =
        Message(
            id = MessageId(row.getLong(0)),
            friend = FriendKey(row.getText(1)),
            outgoing = row.getBoolean(2),
            action = row.getBoolean(3),
            text = row.getText(4),
            time = row.getLong(5),
            delivered = row.getBoolean(6),
            file =
                if (row.isNull(7)) {
                    null
                } else {
                    FileTransfer(
                        name = row.getText(7),
                        size = row.getLong(8),
                        state = FileState.of(row.getLong(9).toInt()),
                        done = row.getLong(10),
                        file = File(row.getText(11)),
                    )
                },
            call =
                if (row.isNull(12)) {
                    null
                } else {
                    CallNote(row.getBoolean(12), CallOutcome.of(row.getLong(13).toInt()), row.getLong(14))
                },
        )

    private fun update(
        sql: String,
        vararg args: Any,
    ) {
        query(sql, *args) { }
    }

    private fun <T> query(
        sql: String,
        vararg args: Any,
        row: (SQLiteStatement) -> T,
    ): List<T> =
        db.prepare(sql).use { statement ->
            args.forEachIndexed { i, arg ->
                when (arg) {
                    is String -> statement.bindText(i + 1, arg)
                    is Long -> statement.bindLong(i + 1, arg)
                    is Boolean -> statement.bindBoolean(i + 1, arg)
                    is Int -> statement.bindLong(i + 1, arg.toLong())
                    else -> error("can't bind ${arg::class}")
                }
            }
            buildList { while (statement.step()) add(row(statement)) }
        }

    private companion object {
        const val SELECT_MESSAGE =
            "SELECT id, friend, outgoing, action, text, time, delivered, file_name, file_size, file_state, file_done, file_path, " +
                "call_video, call_outcome, call_seconds " +
                "FROM messages"

        const val SCHEMA_VERSION = 2

        /** What takes each version to the next: the first takes 1 to 2. [SCHEMA] makes the newest at once. */
        val MIGRATIONS =
            listOf<Store.() -> Unit>(
                {
                    // Some version 1 stores, made by test builds, have these already.
                    addColumn("messages", "call_video INTEGER")
                    addColumn("messages", "call_outcome INTEGER")
                    addColumn("messages", "call_seconds INTEGER")
                },
            )

        val SCHEMA =
            listOf(
                """
                CREATE TABLE friends (
                    key TEXT PRIMARY KEY,
                    name TEXT NOT NULL DEFAULT '',
                    status_message TEXT NOT NULL DEFAULT '',
                    unread INTEGER NOT NULL DEFAULT 0,
                    avatar_hash TEXT NOT NULL DEFAULT ''
                )
                """,
                """
                CREATE TABLE messages (
                    id INTEGER PRIMARY KEY,
                    friend TEXT NOT NULL,
                    outgoing INTEGER NOT NULL,
                    action INTEGER NOT NULL,
                    text TEXT NOT NULL,
                    time INTEGER NOT NULL,
                    delivered INTEGER NOT NULL,
                    -- Set for files only.
                    file_name TEXT,
                    file_size INTEGER,
                    file_state INTEGER,
                    file_done INTEGER,
                    file_id TEXT,
                    file_path TEXT,
                    -- Set for calls only.
                    call_video INTEGER,
                    call_outcome INTEGER,
                    call_seconds INTEGER
                )
                """,
                "CREATE INDEX messages_by_friend ON messages (friend, id)",
                "CREATE TABLE requests (key TEXT PRIMARY KEY, message TEXT NOT NULL, time INTEGER NOT NULL)",
                "CREATE TABLE groups (chat_id TEXT PRIMARY KEY, unread INTEGER NOT NULL DEFAULT 0)",
                """
                CREATE TABLE group_messages (
                    id INTEGER PRIMARY KEY,
                    chat_id TEXT NOT NULL,
                    peer_key TEXT NOT NULL,
                    peer_name TEXT NOT NULL,
                    outgoing INTEGER NOT NULL,
                    action INTEGER NOT NULL,
                    private INTEGER NOT NULL,
                    text TEXT NOT NULL,
                    time INTEGER NOT NULL,
                    sent INTEGER NOT NULL
                )
                """,
                "CREATE INDEX group_messages_by_group ON group_messages (chat_id, id)",
                """
                CREATE TABLE group_invites (
                    friend TEXT NOT NULL,
                    name TEXT NOT NULL,
                    data TEXT NOT NULL,
                    time INTEGER NOT NULL,
                    PRIMARY KEY (friend, data)
                )
                """,
                "CREATE TABLE settings (name TEXT PRIMARY KEY, value TEXT NOT NULL)",
            )
    }
}
