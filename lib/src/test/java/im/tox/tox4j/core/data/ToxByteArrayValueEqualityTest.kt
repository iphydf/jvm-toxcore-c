package im.tox.tox4j.core.data

import im.tox.tox4j.core.ToxCoreConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pins content-based equality for the `ByteArray`-wrapping
 * wrappers in `im.tox.tox4j.core.data`. These used to be
 * `@JvmInline value class` which silently inherits `ByteArray`'s
 * reference equality — two equal blobs compared unequal, and a
 * downstream `Map<ToxPublicKey, …>` would silently miscount. Plain
 * `class` + content equals/hashCode fixes it; this test locks it in.
 *
 * One example per emission shape:
 *  - fixed-size, generator-emitted: `ToxPublicKey`
 *  - variable-size, hand-written:   `ToxFriendMessage`
 */
class ToxByteArrayValueEqualityTest {
    private val publicKeyBytes: () -> ByteArray = { ByteArray(ToxCoreConstants.PUBLIC_KEY_SIZE) { 0xA5.toByte() } }

    @Test
    fun fixedSize_equality_isContentBased() {
        val a = ToxPublicKey(publicKeyBytes())
        val b = ToxPublicKey(publicKeyBytes())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun fixedSize_distinguishesContent() {
        val a = ToxPublicKey(ByteArray(ToxCoreConstants.PUBLIC_KEY_SIZE) { 1 })
        val b = ToxPublicKey(ByteArray(ToxCoreConstants.PUBLIC_KEY_SIZE) { 2 })
        assertNotEquals(a, b)
    }

    @Test
    fun fixedSize_toString_omitsBytes() {
        // Public keys are not secret but the convention is uniform —
        // every wrapper hides payload bytes from logs so a
        // secret-bearing type (ToxSecretKey, ToxPassSalt,
        // ToxGroupPassword) can never leak by accident.
        assertEquals(
            "ToxPublicKey(<${ToxCoreConstants.PUBLIC_KEY_SIZE} bytes>)",
            ToxPublicKey(publicKeyBytes()).toString(),
        )
    }

    @Test
    fun fixedSize_worksAsMapKey() {
        val map = mutableMapOf<ToxPublicKey, String>()
        map[ToxPublicKey(publicKeyBytes())] = "alice"
        val lookup = map[ToxPublicKey(publicKeyBytes())]
        assertEquals("alice", lookup)
    }

    @Test
    fun variableSize_equality_isContentBased() {
        val a = ToxFriendMessage("hello".encodeToByteArray())
        val b = ToxFriendMessage("hello".encodeToByteArray())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun variableSize_distinguishesContent() {
        val a = ToxFriendMessage("hello".encodeToByteArray())
        val b = ToxFriendMessage("world".encodeToByteArray())
        assertNotEquals(a, b)
    }

    @Test
    fun variableSize_toString_omitsBytes() {
        assertEquals("ToxFriendMessage(<5 bytes>)", ToxFriendMessage("hello".encodeToByteArray()).toString())
    }

    @Test
    fun reference_equality_unchanged() {
        val sample = ToxPublicKey(publicKeyBytes())
        assertTrue(sample === sample)
        assertFalse(sample === ToxPublicKey(publicKeyBytes()))
    }

    @Test
    fun differentWrapperTypes_withIdenticalBytes_areNotEqual() {
        // The `other is ThisType` guard inside each `equals` rules out
        // cross-type collisions by construction. Pin it so a refactor
        // that swaps `other is X` for a parent-type check would fail.
        val bytes = ByteArray(ToxCoreConstants.PUBLIC_KEY_SIZE) { 1 }
        assertNotEquals<Any>(ToxPublicKey(bytes), ToxSecretKey(bytes))
    }
}
