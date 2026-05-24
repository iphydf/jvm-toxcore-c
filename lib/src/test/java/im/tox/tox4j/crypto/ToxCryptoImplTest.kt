package im.tox.tox4j.crypto

import im.tox.tox4j.core.data.ToxPassSalt
import im.tox.tox4j.crypto.exceptions.ToxDecryptionException
import im.tox.tox4j.crypto.exceptions.ToxEncryptionException
import im.tox.tox4j.crypto.exceptions.ToxGetSaltException
import im.tox.tox4j.impl.jni.PassKey
import im.tox.tox4j.impl.jni.ToxCryptoImpl
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Local tests for the encryptsave layer: pass-key derive, encrypt/decrypt
 * round-trip, salt extraction, magic-number detection, and the hash function.
 * All operations run inside this process — no network, no second instance.
 *
 * `PassKey` is a handle-backed resource; every test that derives one uses
 * `.use { }` so the underlying `Tox_Pass_Key*` is freed.
 */
@OptIn(kotlin.ExperimentalStdlibApi::class)
class ToxCryptoImplTest {
    private val passphrase = "correct horse battery staple".encodeToByteArray()
    private val plaintext = "hello, world".encodeToByteArray()

    @Test
    fun passKeyDerive_succeeds() = ToxCryptoImpl.passKeyDerive(passphrase).use { }

    @Test
    fun passKeyDeriveWithSalt_isDeterministic() {
        val salt = ByteArray(ToxCryptoConstants.SALT_LENGTH) { it.toByte() }
        ToxCryptoImpl.passKeyDeriveWithSalt(passphrase, ToxPassSalt(salt)).use { a ->
            ToxCryptoImpl.passKeyDeriveWithSalt(passphrase, ToxPassSalt(salt)).use { b ->
                // Same passphrase + salt must encrypt+decrypt round-trip
                // across two independently-derived keys.
                val cipher = ToxCryptoImpl.encrypt(a, plaintext)
                assertContentEquals(plaintext, ToxCryptoImpl.decrypt(b, cipher))
            }
        }
    }

    @Test
    fun passKeyDeriveWithSalt_differentSalt_differentKey() {
        val saltA = ByteArray(ToxCryptoConstants.SALT_LENGTH) { 0 }
        val saltB = ByteArray(ToxCryptoConstants.SALT_LENGTH) { 1 }
        ToxCryptoImpl.passKeyDeriveWithSalt(passphrase, ToxPassSalt(saltA)).use { a ->
            ToxCryptoImpl.passKeyDeriveWithSalt(passphrase, ToxPassSalt(saltB)).use { b ->
                val cipher = ToxCryptoImpl.encrypt(a, plaintext)
                // Wrong key → past the magic / length checks but the
                // MAC verification fails → FAILED (toxencryptsave.c:343).
                val ex = assertFailsWith<ToxDecryptionException> { ToxCryptoImpl.decrypt(b, cipher) }
                assertEquals(ToxDecryptionException.Code.FAILED, ex.code)
            }
        }
    }

    @Test
    fun passKeyDerive_emptyPassphrase_isAllowed() {
        // The C API accepts an empty passphrase (no NULL check).
        ToxCryptoImpl.passKeyDerive(ByteArray(0)).use { }
    }

    @Test
    fun encrypt_outputIsExpectedLength() {
        ToxCryptoImpl.passKeyDerive(passphrase).use { key ->
            val cipher = ToxCryptoImpl.encrypt(key, plaintext)
            assertEquals(plaintext.size + ToxCryptoConstants.ENCRYPTION_EXTRA_LENGTH, cipher.size)
        }
    }

    @Test
    fun encrypt_isNonDeterministic() {
        ToxCryptoImpl.passKeyDerive(passphrase).use { key ->
            val a = ToxCryptoImpl.encrypt(key, plaintext)
            val b = ToxCryptoImpl.encrypt(key, plaintext)
            assertFalse(a.contentEquals(b))
        }
    }

    @Test
    fun encrypt_thenDecrypt_roundtrip() {
        ToxCryptoImpl.passKeyDerive(passphrase).use { key ->
            val cipher = ToxCryptoImpl.encrypt(key, plaintext)
            val recovered = ToxCryptoImpl.decrypt(key, cipher)
            assertContentEquals(plaintext, recovered)
        }
    }

    @Test
    fun encrypt_emptyData_throws() {
        // The C API rejects empty input with TOX_ERR_ENCRYPTION_NULL
        // (toxencryptsave.c:223 — @plaintext_len == 0@ trips the NULL
        // guard).
        ToxCryptoImpl.passKeyDerive(passphrase).use { key ->
            val ex =
                assertFailsWith<ToxEncryptionException> {
                    ToxCryptoImpl.encrypt(key, ByteArray(0))
                }
            assertEquals(ToxEncryptionException.Code.NULL, ex.code)
        }
    }

    @Test
    fun encrypt_largeData_roundtrip() {
        ToxCryptoImpl.passKeyDerive(passphrase).use { key ->
            val data = ByteArray(10000) { it.toByte() }
            val cipher = ToxCryptoImpl.encrypt(key, data)
            val recovered = ToxCryptoImpl.decrypt(key, cipher)
            assertContentEquals(data, recovered)
        }
    }

    @Test
    fun decrypt_wrongKey_throws() {
        ToxCryptoImpl.passKeyDerive(passphrase).use { keyA ->
            ToxCryptoImpl.passKeyDerive("different".encodeToByteArray()).use { keyB ->
                val cipher = ToxCryptoImpl.encrypt(keyA, plaintext)
                val ex =
                    assertFailsWith<ToxDecryptionException> {
                        ToxCryptoImpl.decrypt(keyB, cipher)
                    }
                // Past the magic / length checks; the MAC verification
                // fails with the wrong key → FAILED.
                assertEquals(ToxDecryptionException.Code.FAILED, ex.code)
            }
        }
    }

    @Test
    fun decrypt_unencryptedData_throws() {
        ToxCryptoImpl.passKeyDerive(passphrase).use { key ->
            val ex =
                assertFailsWith<ToxDecryptionException> {
                    ToxCryptoImpl.decrypt(key, "not encrypted".encodeToByteArray())
                }
            // 13 bytes is below TOX_PASS_ENCRYPTION_EXTRA_LENGTH, so
            // c-toxcore trips the length check (toxencryptsave.c:316)
            // before getting to the magic-number compare.
            assertEquals(ToxDecryptionException.Code.INVALID_LENGTH, ex.code)
        }
    }

    @Test
    fun decrypt_corruptedCipher_throws() {
        ToxCryptoImpl.passKeyDerive(passphrase).use { key ->
            val cipher = ToxCryptoImpl.encrypt(key, plaintext)
            cipher[cipher.size / 2] = (cipher[cipher.size / 2].toInt() xor 0xFF).toByte()
            val ex =
                assertFailsWith<ToxDecryptionException> {
                    ToxCryptoImpl.decrypt(key, cipher)
                }
            // Corruption is past the magic header but before the MAC;
            // decryption hits the MAC verification and fails.
            assertEquals(ToxDecryptionException.Code.FAILED, ex.code)
        }
    }

    @Test
    fun isDataEncrypted_true_forCiphertext() {
        ToxCryptoImpl.passKeyDerive(passphrase).use { key ->
            val cipher = ToxCryptoImpl.encrypt(key, plaintext)
            assertTrue(ToxCryptoImpl.isDataEncrypted(cipher))
        }
    }

    @Test
    fun isDataEncrypted_false_forPlaintext() {
        assertFalse(ToxCryptoImpl.isDataEncrypted(plaintext))
    }

    @Test
    fun isDataEncrypted_false_forEmpty() {
        assertFalse(ToxCryptoImpl.isDataEncrypted(ByteArray(0)))
    }

    @Test
    fun getSalt_extractsCipherSalt() {
        val salt = ByteArray(ToxCryptoConstants.SALT_LENGTH) { 7 }
        ToxCryptoImpl.passKeyDeriveWithSalt(passphrase, ToxPassSalt(salt)).use { key ->
            val cipher = ToxCryptoImpl.encrypt(key, plaintext)
            assertContentEquals(salt, ToxCryptoImpl.getSalt(cipher).value)
        }
    }

    @Test
    fun getSalt_tooShort_throws() {
        // tox_get_salt reads the magic-number header before validating
        // the buffer length. A short input would be undefined behaviour
        // on the C side; the JNI layer pre-checks and throws
        // BAD_FORMAT instead.
        val ex =
            assertFailsWith<ToxGetSaltException> {
                ToxCryptoImpl.getSalt(ByteArray(8))
            }
        assertEquals(ToxGetSaltException.Code.BAD_FORMAT, ex.code)
    }

    @Test
    fun getSalt_empty_throws() {
        val ex =
            assertFailsWith<ToxGetSaltException> {
                ToxCryptoImpl.getSalt(ByteArray(0))
            }
        // Below the magic-header length → JNI shim's pre-check fires.
        assertEquals(ToxGetSaltException.Code.BAD_FORMAT, ex.code)
    }

    @Test
    fun getSalt_unencryptedButLongEnough_throws() {
        // Past the length check, tox_get_salt's magic-number compare
        // rejects non-toxsave data and returns BAD_FORMAT through
        // the C error param.
        val ex =
            assertFailsWith<ToxGetSaltException> {
                ToxCryptoImpl.getSalt(ByteArray(ToxCryptoConstants.ENCRYPTION_EXTRA_LENGTH))
            }
        assertEquals(ToxGetSaltException.Code.BAD_FORMAT, ex.code)
    }

    @Test
    fun passKey_close_releasesHandle() {
        // Smoke test: explicit close() succeeds. The native side would
        // SIGSEGV if the handle were already freed, so reaching the end
        // of this test is the assertion.
        val key = ToxCryptoImpl.passKeyDerive(passphrase)
        key.close()
    }

    @Test
    fun passKey_canDecryptOriginalCipher() {
        // The same passphrase + same salt must reconstitute a key that
        // decrypts a ciphertext produced by the original derivation.
        val salt = ByteArray(ToxCryptoConstants.SALT_LENGTH) { 3 }
        ToxCryptoImpl.passKeyDeriveWithSalt(passphrase, ToxPassSalt(salt)).use { key1 ->
            val cipher = ToxCryptoImpl.encrypt(key1, plaintext)
            ToxCryptoImpl.passKeyDeriveWithSalt(passphrase, ToxPassSalt(salt)).use { key2 ->
                assertContentEquals(plaintext, ToxCryptoImpl.decrypt(key2, cipher))
            }
        }
    }

    @Test
    fun hash_returnsExpectedLength() {
        val h = ToxCryptoImpl.hash(plaintext)
        assertEquals(ToxCryptoConstants.HASH_LENGTH, h.size)
    }

    @Test
    fun hash_isDeterministic() {
        val a = ToxCryptoImpl.hash(plaintext)
        val b = ToxCryptoImpl.hash(plaintext)
        assertContentEquals(a, b)
    }

    @Test
    fun hash_differentData_differentResult() {
        val a = ToxCryptoImpl.hash(plaintext)
        val b = ToxCryptoImpl.hash("different".encodeToByteArray())
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun hash_emptyData_isWellDefined() {
        val h = ToxCryptoImpl.hash(ByteArray(0))
        assertEquals(ToxCryptoConstants.HASH_LENGTH, h.size)
    }

    // PassKey is referenced for `use { }` typing in the tests above.
    @Suppress("unused")
    private fun typeCheck(passKey: PassKey): PassKey = passKey
}
