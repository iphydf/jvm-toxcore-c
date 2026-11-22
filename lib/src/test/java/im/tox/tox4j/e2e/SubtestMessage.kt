package im.tox.tox4j.e2e

import im.tox.tox4j.core.ToxCoreEvent
import im.tox.tox4j.core.enums.ToxMessageType
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

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
            payload,
        )

        val received =
            harness.waitForEvent<ToxCoreEvent.FriendMessage>("Bob never received Alice's friend message") {
                it.friendNumber == bobFriendOfAlice
            }

        assertEquals(ToxMessageType.NORMAL, received.type)
        assertContentEquals(payload, received.message)
    }
}
