package im.tox.tox4j.e2e

import im.tox.tox4j.core.callbacks.ToxCoreEventListener
import im.tox.tox4j.core.data.ToxFriendMessage
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.core.enums.ToxMessageType
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Friend-message round-trip subtest. Alice (tox 0) sends a NORMAL message
 * to Bob (tox 1); the subtest succeeds when Bob's `friendMessage` callback
 * fires with the matching payload and type. The harness must already have
 * `waitForFriendConnected(0, 1)` resolved before this subtest runs.
 */
@OptIn(kotlin.ExperimentalStdlibApi::class)
object SubtestMessage {
    fun run(harness: Tox4jHarness) {
        check(harness.toxes.size >= 2) { "SubtestMessage needs at least 2 toxes" }
        val alice = harness.toxes[0]
        val bob = harness.toxes[1]

        val bobFriendOfAlice = bob.friendByPublicKey(alice.publicKey)
        val payload = "hello, bob".encodeToByteArray()

        alice.friendSendMessage(
            alice.friendByPublicKey(bob.publicKey),
            ToxMessageType.NORMAL,
            ToxFriendMessage(payload),
        )

        data class Received(
            val type: ToxMessageType,
            val message: ByteArray,
        )
        val handler =
            object : ToxCoreEventListener<Received?> {
                override fun friendMessage(
                    friendNumber: ToxFriendNumber,
                    type: ToxMessageType,
                    message: ToxFriendMessage,
                    state: Received?,
                ): Received? = state ?: Received(type, message.value).takeIf { friendNumber.value == bobFriendOfAlice.value }
            }

        val received =
            harness.waitFor(
                initial = null as Received?,
                handler = handler,
                timeoutMs = 30_000L,
                message = "Bob never received Alice's friend message",
            ) { it != null }!!

        assertEquals(ToxMessageType.NORMAL, received.type)
        assertContentEquals(payload, received.message)
    }
}
