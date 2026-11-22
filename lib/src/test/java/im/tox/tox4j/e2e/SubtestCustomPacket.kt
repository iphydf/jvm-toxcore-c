package im.tox.tox4j.e2e

import im.tox.tox4j.core.ToxCoreEvent
import kotlin.test.assertContentEquals

/** Custom packet IDs: lossy 200-254, lossless 160-191. */
object SubtestCustomPacket {
    fun run(harness: Tox4jHarness) {
        check(harness.toxes.size >= 2) { "SubtestCustomPacket needs at least 2 toxes" }
        val alice = harness.toxes[0]
        val bob = harness.toxes[1]
        val aliceFromBob = bob.friendByPublicKey(alice.publicKey)
        val bobFromAlice = alice.friendByPublicKey(bob.publicKey)

        val lossy = byteArrayOf(200.toByte(), 1, 2, 3)
        val lossless = byteArrayOf(160.toByte(), 4, 5, 6)

        alice.friendSendLossyPacket(bobFromAlice, lossy)
        alice.friendSendLosslessPacket(bobFromAlice, lossless)

        data class Received(
            val lossy: ByteArray? = null,
            val lossless: ByteArray? = null,
        )

        fun ByteArray.startsWith(id: Int) = isNotEmpty() && this[0] == id.toByte()
        val handler = { state: Received, event: ToxCoreEvent ->
            when {
                event is ToxCoreEvent.FriendLossyPacket && event.friendNumber == aliceFromBob && event.data.startsWith(200) -> {
                    state.copy(lossy = state.lossy ?: event.data)
                }

                event is ToxCoreEvent.FriendLosslessPacket && event.friendNumber == aliceFromBob && event.data.startsWith(160) -> {
                    state.copy(lossless = state.lossless ?: event.data)
                }

                else -> {
                    state
                }
            }
        }
        val received =
            harness.waitFor(
                initial = Received(),
                handler = handler,
                message = "Bob never received Alice's custom packets",
            ) { it.lossy != null && it.lossless != null }

        assertContentEquals(lossy, received.lossy)
        assertContentEquals(lossless, received.lossless)
    }
}
