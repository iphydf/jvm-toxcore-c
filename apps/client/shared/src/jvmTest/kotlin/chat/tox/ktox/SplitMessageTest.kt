package chat.tox.ktox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplitMessageTest {
    @Test
    fun shortMessage_staysWhole() {
        assertEquals(listOf("hello"), splitMessage("hello", max = 10))
    }

    @Test
    fun longMessage_splitsAtSpaces() {
        assertEquals(listOf("hello ", "world"), splitMessage("hello world", max = 8))
    }

    @Test
    fun longWord_splitsAtTheLimit() {
        assertEquals(listOf("abcd", "efgh", "ij"), splitMessage("abcdefghij", max = 4))
    }

    @Test
    fun multiByteCharacters_stayWhole() {
        // "é" is 2 bytes and "🙂" 4 bytes in UTF-8.
        val text = "é🙂".repeat(50)
        val parts = splitMessage(text, max = 13)
        assertEquals(text, parts.joinToString(""))
        assertTrue(parts.all { it.encodeToByteArray().size <= 13 })
        assertTrue(parts.all { it.encodeToByteArray().decodeToString() == it })
    }
}
