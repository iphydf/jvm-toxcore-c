package im.tox.tox4j.crypto

import im.tox.tox4j.crypto.exceptions.ToxEncryptionException
import im.tox.tox4j.impl.jni.ToxCryptoImpl
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stress the PassKey pool under concurrent encrypt-and-close — the
 * scenario where the C-side raw-pointer design (`get_pass_key`
 * returning a `Tox_Pass_Key *` outside the pool lock) would
 * use-after-free when one thread's `close()` destroys the underlying
 * object while another thread's `encrypt()` is in flight.
 *
 * The shared_ptr-based pool keeps each in-flight operation alive
 * through its own owning reference; closing only drops the pool's
 * reference, and the real `tox_pass_key_free` runs after the last
 * borrowed reference is released.
 *
 * The race is non-deterministic — a real UAF won't fire on every
 * run without ASAN/TSan. The test still pins three observable
 * properties that any UAF would corrupt:
 *
 *  - `close()` doesn't throw (would happen if a freed-twice
 *    pass-key crashed the JNI side).
 *  - Encryptors that complete successfully produce content that
 *    round-trips to the original plaintext (a corrupted in-flight
 *    encrypt would silently produce garbled bytes — we decrypt the
 *    first successful cipher and assertContentEquals).
 *  - Encryptors that fail fail with the *documented* post-close
 *    `ToxEncryptionException(NULL)` and nothing else. Any other
 *    exception (e.g. an `IllegalStateException` from a half-freed
 *    pool slot, an `IndexOutOfBoundsException` from a clobbered
 *    output buffer) is the UAF symptom and fails the test.
 */
@OptIn(kotlin.ExperimentalStdlibApi::class)
class ToxCryptoConcurrencyTest {
    @Test
    fun encrypt_concurrentWithClose_isSafe() {
        val passphrase = "race condition demo".encodeToByteArray()
        val plaintext = "payload".encodeToByteArray()

        // 32 concurrent worker pairs: each derives a key, fires a
        // tight encrypt loop on one thread, races a close on another.
        val workerPairs = 32
        val iterationsPerWorker = 200

        val closeFailures = AtomicInteger(0)
        val unexpectedEncryptFailures = AtomicInteger(0)
        // First successful cipher per worker, captured before close
        // lands — used to verify the in-flight encrypt produced
        // semantically correct bytes (not just non-crashing ones).
        val sampleCiphersByWorker = arrayOfNulls<ByteArray>(workerPairs)
        val keys = Array(workerPairs) { ToxCryptoImpl.passKeyDerive(passphrase) }
        val threads = mutableListOf<Thread>()

        for (n in 0 until workerPairs) {
            val key = keys[n]

            // Encryptor: pounds on the key until close lands.
            threads +=
                thread(name = "encryptor-$n") {
                    repeat(iterationsPerWorker) {
                        try {
                            val cipher = ToxCryptoImpl.encrypt(key, plaintext)
                            // Capture only the first successful cipher
                            // per worker — every subsequent encrypt is
                            // a redundant data point.
                            if (sampleCiphersByWorker[n] == null) {
                                sampleCiphersByWorker[n] = cipher
                            }
                        } catch (_: ToxEncryptionException) {
                            // The documented post-close exception is the
                            // ONLY one that's legitimate here. Any other
                            // exception is the UAF symptom.
                        } catch (_: Throwable) {
                            unexpectedEncryptFailures.incrementAndGet()
                        }
                    }
                }

            // Closer: lands after a small randomised stall.
            threads +=
                thread(name = "closer-$n") {
                    Thread.sleep((n % 5).toLong())
                    try {
                        key.close()
                    } catch (_: Throwable) {
                        closeFailures.incrementAndGet()
                    }
                }
        }

        threads.forEach { it.join() }

        // No raw exceptions from close — a freed-twice or
        // half-freed pool slot would surface here.
        assertEquals(
            0,
            closeFailures.get(),
            "close() must not throw under concurrent encrypt",
        )

        // No off-script exceptions from encrypt — the only legitimate
        // failure path is ToxEncryptionException (caught silently above).
        assertEquals(
            0,
            unexpectedEncryptFailures.get(),
            "encrypt() must throw only ToxEncryptionException under concurrent close",
        )

        // Round-trip each captured cipher with a fresh pass key derived
        // from the same passphrase + extracted salt; a corrupted
        // in-flight encrypt would produce bytes that fail decryption
        // or decrypt to something other than the plaintext. At least
        // one worker should have completed an encrypt before its
        // close landed (the closer sleeps up to 4 ms while the
        // encryptor pounds 200 iterations).
        val successfulCiphers = sampleCiphersByWorker.filterNotNull()
        assertTrue(
            successfulCiphers.isNotEmpty(),
            "expected at least one worker to complete encrypt before its close (saw 0/$workerPairs)",
        )
        for (cipher in successfulCiphers) {
            val salt = ToxCryptoImpl.getSalt(cipher)
            ToxCryptoImpl.passKeyDeriveWithSalt(passphrase, salt).use { freshKey ->
                assertContentEquals(
                    plaintext,
                    ToxCryptoImpl.decrypt(freshKey, cipher),
                    "concurrent encrypt produced content that doesn't round-trip — UAF symptom",
                )
            }
        }
    }
}
