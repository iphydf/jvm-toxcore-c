package im.tox.tox4j.e2e

import im.tox.tox4j.core.ToxCoreEvent
import im.tox.tox4j.core.enums.ToxUserStatus
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@OptIn(kotlin.ExperimentalStdlibApi::class)
object SubtestFriendInfo {
    fun run(harness: Tox4jHarness) {
        check(harness.toxes.size >= 2) { "SubtestFriendInfo needs at least 2 toxes" }
        val alice = harness.toxes[0]
        val bob = harness.toxes[1]
        val aliceFromBob = bob.friendByPublicKey(alice.publicKey)
        val bobFromAlice = alice.friendByPublicKey(bob.publicKey)

        alice.setTyping(bobFromAlice, true)
        harness.waitForEvent<ToxCoreEvent.FriendTyping>("Bob never saw Alice's typing status") {
            it.friendNumber == aliceFromBob && it.typing
        }

        alice.setStatus(ToxUserStatus.BUSY)
        val status =
            harness.waitForEvent<ToxCoreEvent.FriendStatus>("Bob never saw Alice's status change") {
                it.friendNumber == aliceFromBob
            }
        assertEquals(ToxUserStatus.BUSY, status.status)

        val statusMessage = "Busy coding".encodeToByteArray()
        alice.setStatusMessage(statusMessage)
        val seenStatusMessage =
            harness.waitForEvent<ToxCoreEvent.FriendStatusMessage>("Bob never saw Alice's status message") {
                it.friendNumber == aliceFromBob
            }
        assertContentEquals(statusMessage, seenStatusMessage.message)

        val name = "Alice".encodeToByteArray()
        alice.setName(name)
        val seenName =
            harness.waitForEvent<ToxCoreEvent.FriendName>("Bob never saw Alice's name change") {
                it.friendNumber == aliceFromBob
            }
        assertContentEquals(name, seenName.name)
    }
}
