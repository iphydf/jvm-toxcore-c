package im.tox.tox4j.e2e

import im.tox.tox4j.core.callbacks.ToxCoreEventListener
import im.tox.tox4j.core.data.ToxFriendLosslessPacket
import im.tox.tox4j.core.data.ToxFriendLossyPacket
import im.tox.tox4j.core.data.ToxFriendNumber
import kotlin.test.assertContentEquals

/**
 * Friend lossy + lossless custom packets. Alice sends one of each; Bob's
 * handlers must observe both with byte-for-byte fidelity. Packet IDs
 * follow the Tox custom-packet ranges (lossy 200-254, lossless 160-191).
 */
object SubtestCustomPacket {
    fun run(harness: Tox4jHarness) {
        check(harness.toxes.size >= 2) { "SubtestCustomPacket needs at least 2 toxes" }
        val alice = harness.toxes[0]
        val bob = harness.toxes[1]
        val aliceFromBob = bob.friendByPublicKey(alice.publicKey)
        val bobFromAlice = alice.friendByPublicKey(bob.publicKey)

        val lossy = byteArrayOf(200.toByte(), 1, 2, 3)
        val lossless = byteArrayOf(160.toByte(), 4, 5, 6)

        alice.friendSendLossyPacket(bobFromAlice, ToxFriendLossyPacket(lossy))
        alice.friendSendLosslessPacket(bobFromAlice, ToxFriendLosslessPacket(lossless))

        data class Received(
            val lossy: ByteArray? = null,
            val lossless: ByteArray? = null,
        )
        val handler =
            object : ToxCoreEventListener<Received> {
                override fun friendLossyPacket(
                    friendNumber: ToxFriendNumber,
                    data: ToxFriendLossyPacket,
                    state: Received,
                ): Received =
                    if (friendNumber.value == aliceFromBob.value && data.value.isNotEmpty() && data.value[0] == 200.toByte()) {
                        state.copy(lossy = state.lossy ?: data.value)
                    } else {
                        state
                    }

                override fun friendLosslessPacket(
                    friendNumber: ToxFriendNumber,
                    data: ToxFriendLosslessPacket,
                    state: Received,
                ): Received =
                    if (friendNumber.value == aliceFromBob.value && data.value.isNotEmpty() && data.value[0] == 160.toByte()) {
                        state.copy(lossless = state.lossless ?: data.value)
                    } else {
                        state
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
