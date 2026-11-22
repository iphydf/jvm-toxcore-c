package im.tox.tox4j.core

import im.tox.tox4j.core.options.ToxOptions
import im.tox.tox4j.exceptions.ToxKilledException
import im.tox.tox4j.impl.jni.ToxCoreImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ToxCoreIterateTest {
    private val options = ToxOptions(localDiscoveryEnabled = false)

    @Test
    fun iterate_manyTimes_reportsNoFriendEvents() {
        // Alone, only our own connection can change.
        ToxCoreImpl(options).use { tox ->
            val events = List(50) { tox.iterate() }.flatten()
            assertEquals(emptyList(), events.filterNot { it is ToxCoreEvent.SelfConnectionStatus })
        }
    }

    @Test
    fun iterate_afterClose_throws() {
        val tox = ToxCoreImpl(options)
        tox.close()
        assertFailsWith<ToxKilledException> { tox.iterate() }
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
            // Normally tens of ms. Far outside that means the wrong field was read.
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
    fun iterate_selfConnectionStatus_atMostOnce() {
        // When it's delivered isn't fixed, but with no peers there's at most one.
        ToxCoreImpl(options).use { tox ->
            val calls = tox.iterate().count { it is ToxCoreEvent.SelfConnectionStatus }
            assertTrue(calls <= 1, "selfConnectionStatus came $calls times in one iterate")
        }
    }
}
