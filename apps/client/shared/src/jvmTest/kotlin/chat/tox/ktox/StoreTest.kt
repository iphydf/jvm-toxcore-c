package chat.tox.ktox

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreTest {
    private val dir: File = createTempDirectory("ktox-store").toFile()
    private val store = Store(File(dir, "chats.db").path)
    private val alice = FriendKey("A".repeat(64))

    @AfterTest
    fun cleanUp() {
        store.close()
        dir.deleteRecursively()
    }

    @Test
    fun messages_pageFromTheNewest() {
        val all = (1..5).map { store.addMessage(alice, outgoing = false, action = false, "m$it", it.toLong(), delivered = true) }
        assertEquals(listOf("m4", "m5"), store.messages(alice, null, 2).map { it.text })
        assertEquals(listOf("m2", "m3"), store.messages(alice, all[3].id, 2).map { it.text })
        assertEquals("m5", store.lastMessage(alice)?.text)
    }

    @Test
    fun version1_keepsItsChats() = version1()

    /** Test builds made version 1 stores with the call columns too. */
    @Test
    fun version1WithCallColumns_keepsItsChats() = version1(", call_video INTEGER, call_outcome INTEGER, call_seconds INTEGER")

    private fun version1(callColumns: String = "") {
        val old = File(dir, "old.db")
        BundledSQLiteDriver().open(old.path).use { db ->
            db.execSQL(
                "CREATE TABLE messages (id INTEGER PRIMARY KEY, friend TEXT NOT NULL, outgoing INTEGER NOT NULL, " +
                    "action INTEGER NOT NULL, text TEXT NOT NULL, time INTEGER NOT NULL, delivered INTEGER NOT NULL, " +
                    "file_name TEXT, file_size INTEGER, file_state INTEGER, file_done INTEGER, file_id TEXT, file_path TEXT$callColumns)",
            )
            db.execSQL("INSERT INTO messages (friend, outgoing, action, text, time, delivered) VALUES ('${alice.hex}', 0, 0, 'hi', 1, 1)")
            db.execSQL("PRAGMA user_version = 1")
        }
        Store(old.path).use { store ->
            store.addCall(alice, outgoing = true, CallNote(video = false, CallOutcome.ANSWERED, 42), 2)
            assertEquals(listOf("hi", "Call, 0:42"), store.messages(alice, null, 10).map { it.callText() })
        }
        // And it opens as it is after that.
        Store(old.path).use { store -> assertEquals(2, store.messages(alice, null, 10).size) }
    }

    @Test
    fun undelivered_untilMarked() {
        val sent = store.addMessage(alice, outgoing = true, action = false, "hi", 1, delivered = false)
        assertEquals(listOf(sent), store.undelivered(alice))
        store.markDelivered(sent.id)
        assertEquals(emptyList(), store.undelivered(alice))
    }

    @Test
    fun friend_keepsNameAndUnread_andDeleteTakesTheChat() {
        store.addFriend(alice)
        store.setFriendName(alice, "Alice")
        store.setUnread(alice, 3)
        store.addMessage(alice, outgoing = false, action = false, "hi", 1, delivered = true)
        assertEquals(StoredFriend("Alice", "", 3), store.friends()[alice])
        store.deleteFriend(alice)
        assertEquals(emptyMap(), store.friends())
        assertEquals(null, store.lastMessage(alice))
    }

    @Test
    fun settings_roundTrip() {
        assertEquals(Settings(), store.settings())
        val changed = Settings(udp = false, ipv6 = false, localDiscovery = true)
        store.saveSettings(changed)
        assertEquals(changed, store.settings())
    }
}
