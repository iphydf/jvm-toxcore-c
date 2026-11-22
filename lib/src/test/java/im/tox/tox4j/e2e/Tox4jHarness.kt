package im.tox.tox4j.e2e

import im.tox.tox4j.core.ToxCoreEvent
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.core.data.ToxPort
import im.tox.tox4j.core.data.ToxPublicKey
import im.tox.tox4j.core.enums.ToxConnection
import im.tox.tox4j.core.options.ToxOptions
import im.tox.tox4j.exceptions.ToxKilledException
import im.tox.tox4j.impl.jni.ToxCoreImpl
import java.lang.Thread.sleep

/** Loopback toxes for multi-tox scenarios, a port of `rs-toxcore-c/toxcore/tests/suite/setup.rs`. */
class Tox4jHarness(
    private val baseOptions: ToxOptions = ToxOptions(localDiscoveryEnabled = false),
) : AutoCloseable {
    private val managed: MutableList<ToxCoreImpl> = mutableListOf()

    /** Each tox's friends that are online, as their events said. */
    private val online = mutableMapOf<ToxCoreImpl, MutableSet<ToxFriendNumber>>()

    val toxes: List<ToxCoreImpl> get() = managed

    private fun publicKey(i: Int): ToxPublicKey = managed[i].publicKey

    fun addTox() {
        // Port 0: the OS picks a free one.
        managed.add(ToxCoreImpl(baseOptions.copy(startPort = ToxPort(0), endPort = ToxPort(0))))
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

    /** Every tox's events from one iteration, then a sleep. */
    fun iterate(): List<ToxCoreEvent> {
        // minOf needs a tox.
        if (managed.isEmpty()) return emptyList()
        val events = managed.flatMap { tox -> tox.iterate().onEach { remember(tox, it) } }
        sleep(managed.minOf { it.iterationInterval }.coerceAtLeast(5).toLong())
        return events
    }

    /** Folds every tox's events into a state until it's what [predicate] wants. */
    fun <S> waitFor(
        initial: S,
        handler: (S, ToxCoreEvent) -> S,
        timeoutMs: Long = 30_000L,
        message: String = "waitFor timed out after ${timeoutMs}ms",
        predicate: (S) -> Boolean,
    ): S {
        val deadline = System.currentTimeMillis() + timeoutMs
        var state = initial
        while (System.currentTimeMillis() < deadline) {
            state = iterate().fold(state, handler)
            if (predicate(state)) return state
        }
        throw AssertionError(message)
    }

    private fun remember(
        tox: ToxCoreImpl,
        event: ToxCoreEvent,
    ) {
        if (event !is ToxCoreEvent.FriendConnectionStatus) return
        val friends = online.getOrPut(tox) { mutableSetOf() }
        if (event.connectionStatus == ToxConnection.NONE) friends -= event.friendNumber else friends += event.friendNumber
    }

    /** The first [E] from any tox that [accept] takes. */
    inline fun <reified E : ToxCoreEvent> waitForEvent(
        message: String,
        timeoutMs: Long = 30_000L,
        crossinline accept: (E) -> Boolean = { true },
    ): E =
        waitFor(
            initial = null as E?,
            handler = { found, event -> found ?: (event as? E)?.takeIf { accept(it) } },
            timeoutMs = timeoutMs,
            message = message,
        ) { it != null }!!

    fun waitForFriendConnected(
        i: Int,
        j: Int,
        timeoutMs: Long = 30_000L,
    ) {
        val friendNumberJ = managed[i].friendByPublicKey(publicKey(j))
        waitFor(Unit, { state, _ -> state }, timeoutMs, "tox $i never saw tox $j as connected within ${timeoutMs}ms") {
            online[managed[i]]?.contains(friendNumberJ) == true
        }
    }

    override fun close() {
        for (tox in managed) {
            try {
                tox.close()
            } catch (_: ToxKilledException) {
                // The scenario closed it already.
            }
        }
        managed.clear()
    }
}
