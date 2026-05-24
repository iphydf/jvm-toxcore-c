package im.tox.tox4j.e2e

import im.tox.tox4j.core.callbacks.ToxCoreEventListener
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.core.data.ToxPublicKey
import im.tox.tox4j.core.enums.ToxConnection
import im.tox.tox4j.core.options.ToxOptions
import im.tox.tox4j.exceptions.ToxKilledException
import im.tox.tox4j.impl.jni.ToxCoreImpl
import java.lang.Thread.sleep

/**
 * Loopback test harness for multi-tox scenarios — the Kotlin port of
 * `rs-toxcore-c/toxcore/tests/suite/setup.rs`. Each instance is a real
 * `ToxCoreImpl` bound to a local UDP port; nodes connect to each other via
 * `bootstrap` + `addTcpRelay` using the other side's `getDhtId` and
 * `getUdpPort`.
 *
 * The harness owns the toxes' lifecycle. Construct with [use] so cleanup
 * runs even when an assertion blows up mid-scenario:
 *
 * ```
 * Tox4jHarness().use { h ->
 *     h.addTox(); h.addTox(); h.addTox()
 *     h.connect(0, 1); h.connect(0, 2); h.connect(1, 2)
 *     h.waitForFriendConnected(0, 1)
 *     Subtest.message.run(h)
 *     Subtest.file.run(h)
 * }
 * ```
 *
 * Convergence loops poll with `tox.iterationInterval` (capped at 50 ms)
 * and a wall-clock timeout — same shape as the Rust harness.
 */
class Tox4jHarness(
    private val baseOptions: ToxOptions = ToxOptions(localDiscoveryEnabled = false),
) : AutoCloseable {
    private val managed: MutableList<ToxCoreImpl> = mutableListOf()

    /** Read-only view of the managed Tox instances, in addition order. */
    val toxes: List<ToxCoreImpl> get() = managed

    private fun publicKey(i: Int): ToxPublicKey = managed[i].publicKey

    /** Add a Tox to the harness. The OS picks the loopback UDP port. */
    fun addTox() {
        // startPort = endPort = 0 lets the C library pick a free port itself.
        managed.add(ToxCoreImpl(baseOptions.copy(startPort = 0.toUShort(), endPort = 0.toUShort())))
    }

    /** Mutually befriend toxes `i` and `j`, then have `j` bootstrap off `i`. */
    fun connect(
        i: Int,
        j: Int,
    ) {
        val pkI = publicKey(i)
        val pkJ = publicKey(j)
        managed[i].friendAddNorequest(pkJ)
        managed[j].friendAddNorequest(pkI)

        val dhtKeyI = managed[i].dhtId
        val portI = managed[i].udpPort
        managed[j].addTcpRelay("127.0.0.1", portI, dhtKeyI)
        managed[j].bootstrap("127.0.0.1", portI, dhtKeyI)
    }

    /**
     * Drive one iterate-tick across every tox in the harness. Sleeps the
     * minimum of the per-tox iteration intervals (with a 5 ms floor) so the
     * busy loop doesn't pin the CPU on the slowest tox.
     */
    fun <S> iterate(
        handler: ToxCoreEventListener<S>,
        state: S,
    ): S {
        // Guard against an empty harness: with no toxes the loop
        // never runs and @minInterval@ stays at @Int.MAX_VALUE@,
        // which would then sleep for ~24 days. Returning early keeps
        // the convergence helpers responsive when a scenario
        // mistakenly iterates before adding any toxes.
        if (managed.isEmpty()) return state
        var s = state
        var minInterval = Int.MAX_VALUE
        for (tox in managed) {
            s = tox.iterate(handler, s)
            val interval = tox.iterationInterval
            if (interval < minInterval) minInterval = interval
        }
        sleep(minInterval.coerceAtLeast(5).toLong())
        return s
    }

    /**
     * Generic iterate-until-predicate helper. Drives [iterate] with the
     * provided [handler] and returns once the predicate over the
     * accumulated state returns true, or fails with the [message] on
     * timeout.
     */
    fun <S> waitFor(
        initial: S,
        handler: ToxCoreEventListener<S>,
        timeoutMs: Long = 30_000L,
        message: String = "waitFor timed out after ${timeoutMs}ms",
        predicate: (S) -> Boolean,
    ): S {
        val deadline = System.currentTimeMillis() + timeoutMs
        var state = initial
        while (System.currentTimeMillis() < deadline) {
            state = iterate(handler, state)
            if (predicate(state)) return state
        }
        throw AssertionError(message)
    }

    /**
     * Wait until tox `i` reports `friendConnectionStatus != NONE` for tox `j`.
     * 30s is the same wall-clock budget the Rust harness uses.
     */
    fun waitForFriendConnected(
        i: Int,
        j: Int,
        timeoutMs: Long = 30_000L,
    ) {
        val pkJ = publicKey(j)
        val friendNumberJ = managed[i].friendByPublicKey(pkJ)
        val handler =
            object : ToxCoreEventListener<Boolean> {
                override fun friendConnectionStatus(
                    friendNumber: ToxFriendNumber,
                    connectionStatus: ToxConnection,
                    state: Boolean,
                ): Boolean = state || (friendNumber.value == friendNumberJ.value && connectionStatus != ToxConnection.NONE)
            }
        waitFor(
            initial = false,
            handler = handler,
            timeoutMs = timeoutMs,
            message = "tox $i never saw tox $j as connected within ${timeoutMs}ms",
        ) { it }
    }

    /** Close every managed tox. Idempotent — safe to call from `use {}`. */
    override fun close() {
        for (tox in managed) {
            try {
                tox.close()
            } catch (_: ToxKilledException) {
                // Already closed by the scenario itself: tolerate.
                // Any other exception is a genuine cleanup bug and
                // should escape rather than be swallowed.
            }
        }
        managed.clear()
    }
}
