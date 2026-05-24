package im.tox.tox4j.core

import im.tox.tox4j.core.data.ToxName
import im.tox.tox4j.core.data.ToxStatusMessage
import im.tox.tox4j.core.enums.ToxUserStatus
import im.tox.tox4j.core.options.ToxOptions
import im.tox.tox4j.impl.jni.ToxCoreImpl
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Round-trip every `self_*` getter/setter pair on `ToxCore`. The goal is to
 * pin the JNI marshaling: a value set on the Kotlin side must come back
 * identical from the C side after a get. Each value class that wraps a
 * `ByteArray` (`ToxName`, `ToxStatusMessage`) gets exercised at multiple
 * lengths to surface any silent truncation.
 */
@OptIn(kotlin.ExperimentalStdlibApi::class)
class ToxCoreSelfTest {
    private val options = ToxOptions(localDiscoveryEnabled = false)

    @Test
    fun nospam_roundtrip() {
        ToxCoreImpl(options).use { tox ->
            tox.setNospam(0x12345678)
            assertEquals(0x12345678, tox.nospam)
        }
    }

    @Test
    fun nospam_zero_roundtrip() {
        ToxCoreImpl(options).use { tox ->
            tox.setNospam(0)
            assertEquals(0, tox.nospam)
        }
    }

    @Test
    fun nospam_negative_roundtrip() {
        // Int spans the full uint32 range via two's-complement reinterpretation.
        ToxCoreImpl(options).use { tox ->
            tox.setNospam(-1)
            assertEquals(-1, tox.nospam)
        }
    }

    @Test
    fun name_short_roundtrip() {
        ToxCoreImpl(options).use { tox ->
            val name = "Alice".encodeToByteArray()
            tox.setName(ToxName(name))
            assertContentEquals(name, tox.name.value)
        }
    }

    @Test
    fun name_empty_roundtrip() {
        ToxCoreImpl(options).use { tox ->
            tox.setName(ToxName(ByteArray(0)))
            assertContentEquals(ByteArray(0), tox.name.value)
        }
    }

    @Test
    fun name_max_length_roundtrip() {
        ToxCoreImpl(options).use { tox ->
            val name = ByteArray(ToxCoreConstants.MAX_NAME_LENGTH) { 'a'.code.toByte() }
            tox.setName(ToxName(name))
            assertContentEquals(name, tox.name.value)
        }
    }

    @Test
    fun name_nonAscii_roundtrip() {
        ToxCoreImpl(options).use { tox ->
            val name = "Алисa🐍".encodeToByteArray()
            tox.setName(ToxName(name))
            assertContentEquals(name, tox.name.value)
        }
    }

    @Test
    fun statusMessage_short_roundtrip() {
        ToxCoreImpl(options).use { tox ->
            val msg = "online".encodeToByteArray()
            tox.setStatusMessage(ToxStatusMessage(msg))
            assertContentEquals(msg, tox.statusMessage.value)
        }
    }

    @Test
    fun statusMessage_empty_roundtrip() {
        ToxCoreImpl(options).use { tox ->
            tox.setStatusMessage(ToxStatusMessage(ByteArray(0)))
            assertContentEquals(ByteArray(0), tox.statusMessage.value)
        }
    }

    @Test
    fun statusMessage_max_length_roundtrip() {
        ToxCoreImpl(options).use { tox ->
            val msg = ByteArray(ToxCoreConstants.MAX_STATUS_MESSAGE_LENGTH) { 'x'.code.toByte() }
            tox.setStatusMessage(ToxStatusMessage(msg))
            assertContentEquals(msg, tox.statusMessage.value)
        }
    }

    @Test
    fun status_none_roundtrip() = statusRoundtripOf(ToxUserStatus.NONE)

    @Test
    fun status_away_roundtrip() = statusRoundtripOf(ToxUserStatus.AWAY)

    @Test
    fun status_busy_roundtrip() = statusRoundtripOf(ToxUserStatus.BUSY)

    private fun statusRoundtripOf(status: ToxUserStatus) {
        ToxCoreImpl(options).use { tox ->
            tox.setStatus(status)
            assertEquals(status, tox.status)
        }
    }

    @Test
    fun status_default_isNone() {
        ToxCoreImpl(options).use { tox ->
            assertEquals(ToxUserStatus.NONE, tox.status)
        }
    }

    @Test
    fun name_default_isEmpty() {
        ToxCoreImpl(options).use { tox ->
            assertEquals(0, tox.name.value.size)
        }
    }

    @Test
    fun statusMessage_default_isEmpty() {
        ToxCoreImpl(options).use { tox ->
            assertEquals(0, tox.statusMessage.value.size)
        }
    }

    @Test
    fun setNospam_modifiesAddress() {
        ToxCoreImpl(options).use { tox ->
            tox.setNospam(0x11111111)
            val addrA = tox.address.value.copyOf()
            tox.setNospam(0x22222222)
            val addrB = tox.address.value
            // Public-key prefix unchanged.
            assertContentEquals(
                addrA.copyOfRange(0, ToxCoreConstants.PUBLIC_KEY_SIZE),
                addrB.copyOfRange(0, ToxCoreConstants.PUBLIC_KEY_SIZE),
            )
            // The nospam/checksum tail must change (we can't check byte-by-byte:
            // individual bytes can coincidentally match between two distinct
            // ints, but the 6-byte range as a whole cannot).
            val tailA = addrA.copyOfRange(ToxCoreConstants.PUBLIC_KEY_SIZE, ToxCoreConstants.ADDRESS_SIZE)
            val tailB = addrB.copyOfRange(ToxCoreConstants.PUBLIC_KEY_SIZE, ToxCoreConstants.ADDRESS_SIZE)
            kotlin.test.assertFalse(tailA.contentEquals(tailB))
        }
    }
}
