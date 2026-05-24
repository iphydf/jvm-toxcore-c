package im.tox.tox4j.impl.jni

import im.tox.tox4j.crypto.exceptions.ToxDecryptionException
import im.tox.tox4j.crypto.exceptions.ToxEncryptionException
import im.tox.tox4j.crypto.exceptions.ToxGetSaltException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The Kotlin `ToxCryptoImpl` surface takes non-null `ByteArray`, but
 * the package-private JNI shims are reachable from Java callers that
 * can pass `null`. Without an explicit guard the shim would forward a
 * `nullptr` data pointer to c-toxcore and crash on the first deref.
 *
 * Each test pins the specific `Code` c-toxcore returns for the null
 * path so a future shim change that swaps error codes (e.g. NULL ↔
 * INVALID_LENGTH ↔ BAD_FORMAT) doesn't slip through.
 *
 * Tests live in `im.tox.tox4j.impl.jni` so they can call the
 * package-private `static native` methods directly.
 */
class ToxCryptoJniNullGuardTest {
    private val passphrase = "test-passphrase".encodeToByteArray()

    @Test
    fun passKeyEncrypt_nullData_throws() {
        val pk = ToxCryptoJni.toxPassKeyDerive(passphrase)
        try {
            // c-toxcore tox_pass_key_encrypt at toxencryptsave.c:223
            // trips the NULL guard on @plaintext_len == 0 || plaintext
            // == nullptr@ → ENCRYPTION_NULL.
            val ex =
                assertFailsWith<ToxEncryptionException> {
                    ToxCryptoJni.toxPassKeyEncrypt(pk, null)
                }
            assertEquals(ToxEncryptionException.Code.NULL, ex.code)
        } finally {
            ToxCryptoJni.toxPassKeyFree(pk)
        }
    }

    @Test
    fun passKeyDecrypt_nullData_throws() {
        val pk = ToxCryptoJni.toxPassKeyDerive(passphrase)
        try {
            // c-toxcore tox_pass_key_decrypt at toxencryptsave.c:316
            // checks @ciphertext_len <= TOX_PASS_ENCRYPTION_EXTRA_LENGTH@
            // BEFORE the null check, so a null jbyteArray (which
            // surfaces as 0-size in the JNI layer) trips INVALID_LENGTH
            // not NULL. Pinning this catches a future shim change that
            // reorders the checks.
            val ex =
                assertFailsWith<ToxDecryptionException> {
                    ToxCryptoJni.toxPassKeyDecrypt(pk, null)
                }
            assertEquals(ToxDecryptionException.Code.INVALID_LENGTH, ex.code)
        } finally {
            ToxCryptoJni.toxPassKeyFree(pk)
        }
    }

    @Test
    fun getSalt_nullData_throws() {
        // The toxGetSalt JNI shim pre-checks @data.size() <
        // TOX_PASS_ENCRYPTION_EXTRA_LENGTH@ before c-toxcore would
        // memcmp the magic header; a null jbyteArray surfaces as a
        // 0-size buffer and trips this guard → BAD_FORMAT.
        val ex =
            assertFailsWith<ToxGetSaltException> {
                ToxCryptoJni.toxGetSalt(null)
            }
        assertEquals(ToxGetSaltException.Code.BAD_FORMAT, ex.code)
    }
}
