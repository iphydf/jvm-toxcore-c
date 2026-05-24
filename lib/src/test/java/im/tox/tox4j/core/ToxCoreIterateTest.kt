package im.tox.tox4j.core

import im.tox.tox4j.core.callbacks.ToxCoreEventListener
import im.tox.tox4j.core.options.ToxOptions
import im.tox.tox4j.exceptions.ToxKilledException
import im.tox.tox4j.impl.jni.ToxCoreImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `iterate()` is the heartbeat method every Tox client calls in a loop. With
 * no peers and no pending events, it must be a no-op (state passes through
 * unchanged, no exceptions), and after `close()` it must throw
 * `ToxKilledException`. Also pins basic constraints on `iterationInterval`.
 */
class ToxCoreIterateTest {
    private val options = ToxOptions(localDiscoveryEnabled = false)

    /** Empty handler: every callback inherits the default that returns the state unchanged. */
    private object Noop : ToxCoreEventListener<Int>

    @Test
    fun iterate_returnsStateUnchanged() {
        ToxCoreImpl(options).use { tox ->
            val result = tox.iterate(Noop, 42)
            assertEquals(42, result)
        }
    }

    @Test
    fun iterate_manyTimes_stable() {
        ToxCoreImpl(options).use { tox ->
            var state = 0
            repeat(50) { state = tox.iterate(Noop, state) }
            assertEquals(0, state)
        }
    }

    @Test
    fun iterate_afterClose_throws() {
        val tox = ToxCoreImpl(options)
        tox.close()
        assertFailsWith<ToxKilledException> { tox.iterate(Noop, 0) }
    }

    @Test
    fun iterationInterval_isPositive() {
        ToxCoreImpl(options).use { tox ->
            assertTrue(tox.iterationInterval > 0)
        }
    }

    @Test
    fun iterationInterval_isBounded() {
        ToxCoreImpl(options).use { tox ->
            // Empirically the iteration interval sits in the tens of
            // milliseconds. Anything outside [1, 1000) ms suggests the
            // marshaling is reading the wrong field.
            val interval = tox.iterationInterval
            assertTrue(interval in 1..999, "iterationInterval=$interval out of range")
        }
    }

    @Test
    fun iterationInterval_afterClose_throws() {
        val tox = ToxCoreImpl(options)
        tox.close()
        assertFailsWith<ToxKilledException> { tox.iterationInterval }
    }

    @Test
    fun iterate_withSelfConnectionStatusListener_isInvokedAtMostOnce() {
        // We can't assert *when* the dispatcher delivers selfConnectionStatus,
        // only that the listener type-checks and isn't crashed by an empty
        // iterate cycle. The handler tracks invocations; the test asserts
        // invocations remain <= 1 after a single iterate (since there are
        // zero peers, typically zero).
        var calls = 0
        val handler =
            object : ToxCoreEventListener<Int> {
                override fun selfConnectionStatus(
                    connectionStatus: im.tox.tox4j.core.enums.ToxConnection,
                    state: Int,
                ): Int {
                    calls += 1
                    return state
                }
            }
        ToxCoreImpl(options).use { tox ->
            tox.iterate(handler, 0)
            assertTrue(calls <= 1, "selfConnectionStatus fired $calls times in one iterate")
        }
    }
}
