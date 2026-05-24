package im.tox.tox4j.core

import im.tox.tox4j.core.data.ToxDhtId
import im.tox.tox4j.core.data.ToxPublicKey
import im.tox.tox4j.core.data.ToxSavedata
import im.tox.tox4j.core.data.ToxSecretKey
import im.tox.tox4j.core.options.ProxyOptions
import im.tox.tox4j.core.options.SaveDataOptions
import im.tox.tox4j.core.options.ToxOptions
import im.tox.tox4j.exceptions.ToxKilledException
import im.tox.tox4j.impl.jni.ToxCoreImpl
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Lifecycle of `ToxCoreImpl`: construction with each option permutation,
 * close behavior, savedata round-trip, and use as `AutoCloseable`. All tests
 * are local — no bootstrap, no iterate loop.
 *
 * Each test creates and (via `use`) closes its own `ToxCore`, so failures
 * don't leak handles into subsequent tests.
 */
class ToxCoreLifecycleTest {
    private val options = ToxOptions(localDiscoveryEnabled = false)

    @Test
    fun construct_withDefaults_succeeds() = ToxCoreImpl(options).use { }

    @Test
    fun construct_withIpv6Disabled_succeeds() = ToxCoreImpl(options.copy(ipv6Enabled = false)).use { }

    @Test
    fun construct_withUdpDisabled_succeeds() = ToxCoreImpl(options.copy(udpEnabled = false)).use { }

    @Test
    fun construct_withLocalDiscoveryEnabled_succeeds() = ToxCoreImpl(options.copy(localDiscoveryEnabled = true)).use { }

    @Test
    fun construct_withProxyNone_succeeds() = ToxCoreImpl(options.copy(proxy = ProxyOptions.None)).use { }

    @Test
    fun construct_withTcpServerDisabled_succeeds() = ToxCoreImpl(options.copy(tcpPort = 0.toUShort())).use { }

    @Test
    fun construct_withCustomPortRange_succeeds() =
        ToxCoreImpl(options.copy(startPort = 40000.toUShort(), endPort = 40100.toUShort())).use { }

    @Test
    fun construct_withEmptySaveData_succeeds() = ToxCoreImpl(options.copy(saveData = SaveDataOptions.None)).use { }

    @Test
    fun close_thenAnyCall_throwsToxKilled() {
        val tox = ToxCoreImpl(options)
        tox.close()
        assertFailsWith<ToxKilledException> { tox.publicKey }
    }

    @Test
    fun use_closesAutomatically() {
        val tox = ToxCoreImpl(options)
        tox.use { /* no-op */ }
        assertFailsWith<ToxKilledException> { tox.publicKey }
    }

    @Test
    fun finalize_afterClose_recoversInstanceSlot() {
        // finalize is the JVM cleanup hook: a real close() should have
        // already happened by the time the GC runs it. Calling it
        // directly here verifies the path doesn't throw (instance
        // manager would otherwise complain "Leaked Tox instance #N"
        // if close wasn't called, but the runCatching swallows that
        // anyway). Reaching the end of this test without crashing is
        // the assertion.
        val tox = ToxCoreImpl(options)
        tox.close()
        ToxCoreImpl::class.java
            .getDeclaredMethod("finalize")
            .apply { isAccessible = true }
            .invoke(tox)
    }

    @Test
    fun finalize_withoutClose_doesNotThrow() {
        // The unhappy path: user never called close(). finalize must
        // kill the instance first (idempotent) and then release the
        // slot. The runCatching guarantee is "no exception escapes
        // the finalizer" — that's what's being verified here.
        val tox = ToxCoreImpl(options)
        ToxCoreImpl::class.java
            .getDeclaredMethod("finalize")
            .apply { isAccessible = true }
            .invoke(tox)
        // The slot is now back on the freelist; subsequent calls
        // hit the instance manager's "thought to be garbage
        // collected" check rather than ToxKilledException (which is
        // the after-close-but-still-allocated path).
        assertFailsWith<IllegalStateException> { tox.publicKey }
    }

    @Test
    fun load_fromSavedata_preservesIdentity() {
        val savedata: ToxSavedata
        val originalPublicKey: ToxPublicKey
        val originalAddress: ByteArray
        ToxCoreImpl(options).use { tox ->
            savedata = tox.savedata
            originalPublicKey = tox.publicKey
            originalAddress = tox.address.value
        }
        assertTrue(savedata.value.isNotEmpty())

        val reloaded = options.copy(saveData = SaveDataOptions.ToxSave(savedata.value))
        ToxCoreImpl(reloaded).use { tox ->
            assertContentEquals(originalPublicKey.value, tox.publicKey.value)
            assertContentEquals(originalAddress, tox.address.value)
        }
    }

    @Test
    fun secretKey_savedataLoad_preservesPublicKey() {
        val secretKey: ToxSecretKey
        val originalPublicKey: ToxPublicKey
        ToxCoreImpl(options).use { tox ->
            secretKey = tox.secretKey
            originalPublicKey = tox.publicKey
        }
        val reloaded = options.copy(saveData = SaveDataOptions.SecretKey(secretKey))
        ToxCoreImpl(reloaded).use { tox ->
            assertContentEquals(originalPublicKey.value, tox.publicKey.value)
        }
    }

    @Test
    fun iterationInterval_isPositive() = ToxCoreImpl(options).use { tox -> assertTrue(tox.iterationInterval > 0) }

    @Test
    fun toxDhtId_withShortKey_throwsIllegalArgument() {
        // The value class validates its size at construction, so
        // wrong-sized keys fail at the boundary rather than further
        // down inside the JNI assert.
        assertFailsWith<IllegalArgumentException> {
            ToxDhtId(ByteArray(16))
        }
    }

    @Test
    fun toxDhtId_withLongKey_throwsIllegalArgument() {
        assertFailsWith<IllegalArgumentException> {
            ToxDhtId(ByteArray(64))
        }
    }

    @Test
    fun savedata_isNonTrivial() {
        ToxCoreImpl(options).use { tox ->
            val savedata = tox.savedata
            // Empirically the savedata is hundreds of bytes; assert it's
            // clearly not a stub by checking it dwarfs just the keys.
            assertTrue(savedata.value.size > ToxCoreConstants.SECRET_KEY_SIZE + ToxCoreConstants.PUBLIC_KEY_SIZE)
        }
    }

    @Test
    fun friendList_isEmptyInitially() {
        ToxCoreImpl(options).use { tox ->
            assertEquals(0, tox.friendList.size)
        }
    }

    @Test
    fun dhtId_hasPublicKeySize() {
        ToxCoreImpl(options).use { tox ->
            assertEquals(ToxCoreConstants.PUBLIC_KEY_SIZE, tox.dhtId.value.size)
        }
    }

    @Test
    fun publicKey_hasExpectedSize() {
        ToxCoreImpl(options).use { tox ->
            assertEquals(ToxCoreConstants.PUBLIC_KEY_SIZE, tox.publicKey.value.size)
        }
    }

    @Test
    fun secretKey_hasExpectedSize() {
        ToxCoreImpl(options).use { tox ->
            assertEquals(ToxCoreConstants.SECRET_KEY_SIZE, tox.secretKey.value.size)
        }
    }

    @Test
    fun address_hasExpectedSize() {
        ToxCoreImpl(options).use { tox ->
            assertEquals(ToxCoreConstants.ADDRESS_SIZE, tox.address.value.size)
        }
    }

    @Test
    fun address_startsWithPublicKey() {
        ToxCoreImpl(options).use { tox ->
            val publicKey = tox.publicKey.value
            val address = tox.address.value
            assertContentEquals(publicKey, address.copyOfRange(0, ToxCoreConstants.PUBLIC_KEY_SIZE))
        }
    }

    @Test
    fun udpPort_isPositive() {
        ToxCoreImpl(options).use { tox ->
            assertTrue(tox.udpPort.value.toInt() > 0)
        }
    }

    @Test
    fun publicKey_isDifferentBetweenInstances() {
        // Two fresh instances should generate distinct keypairs.
        ToxCoreImpl(options).use { a ->
            ToxCoreImpl(options).use { b ->
                assertNotNull(a.publicKey)
                assertNotNull(b.publicKey)
                assertTrue(!a.publicKey.value.contentEquals(b.publicKey.value))
            }
        }
    }
}
