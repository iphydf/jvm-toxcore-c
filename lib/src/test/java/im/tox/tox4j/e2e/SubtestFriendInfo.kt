package im.tox.tox4j.e2e

import im.tox.tox4j.core.callbacks.ToxCoreEventListener
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.core.data.ToxName
import im.tox.tox4j.core.data.ToxStatusMessage
import im.tox.tox4j.core.enums.ToxUserStatus
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Friend-info propagation: Alice changes typing/status/statusMessage/name;
 * Bob's matching callbacks fire with the new value. Each property is tested
 * sequentially against the same Bob handler to keep the connection-setup
 * cost amortised.
 */
@OptIn(kotlin.ExperimentalStdlibApi::class)
object SubtestFriendInfo {
    fun run(harness: Tox4jHarness) {
        check(harness.toxes.size >= 2) { "SubtestFriendInfo needs at least 2 toxes" }
        val alice = harness.toxes[0]
        val bob = harness.toxes[1]
        val aliceFromBob = bob.friendByPublicKey(alice.publicKey)
        val bobFromAlice = alice.friendByPublicKey(bob.publicKey)

        // -- Typing --------------------------------------------------------
        alice.setTyping(bobFromAlice, true)
        val typingHandler =
            object : ToxCoreEventListener<Boolean> {
                override fun friendTyping(
                    friendNumber: ToxFriendNumber,
                    typing: Boolean,
                    state: Boolean,
                ): Boolean = state || (friendNumber.value == aliceFromBob.value && typing)
            }
        harness.waitFor(
            initial = false,
            handler = typingHandler,
            message = "Bob never saw Alice's typing status",
        ) { it }

        // -- Status --------------------------------------------------------
        alice.setStatus(ToxUserStatus.BUSY)
        val statusHandler =
            object : ToxCoreEventListener<ToxUserStatus?> {
                override fun friendStatus(
                    friendNumber: ToxFriendNumber,
                    status: ToxUserStatus,
                    state: ToxUserStatus?,
                ): ToxUserStatus? = state ?: status.takeIf { friendNumber.value == aliceFromBob.value }
            }
        val seenStatus =
            harness.waitFor(
                initial = null as ToxUserStatus?,
                handler = statusHandler,
                message = "Bob never saw Alice's status change",
            ) { it != null }
        assertEquals(ToxUserStatus.BUSY, seenStatus)

        // -- Status message ------------------------------------------------
        val statusMsg = "Busy coding".encodeToByteArray()
        alice.setStatusMessage(ToxStatusMessage(statusMsg))
        val statusMsgHandler =
            object : ToxCoreEventListener<ByteArray?> {
                override fun friendStatusMessage(
                    friendNumber: ToxFriendNumber,
                    message: ToxStatusMessage,
                    state: ByteArray?,
                ): ByteArray? = state ?: message.value.takeIf { friendNumber.value == aliceFromBob.value }
            }
        val seenMsg =
            harness.waitFor(
                initial = null as ByteArray?,
                handler = statusMsgHandler,
                message = "Bob never saw Alice's status message",
            ) { it != null }
        assertContentEquals(statusMsg, seenMsg)

        // -- Nickname ------------------------------------------------------
        val nick = "Alice".encodeToByteArray()
        alice.setName(ToxName(nick))
        val nameHandler =
            object : ToxCoreEventListener<ByteArray?> {
                override fun friendName(
                    friendNumber: ToxFriendNumber,
                    name: ToxName,
                    state: ByteArray?,
                ): ByteArray? = state ?: name.value.takeIf { friendNumber.value == aliceFromBob.value }
            }
        val seenName =
            harness.waitFor(
                initial = null as ByteArray?,
                handler = nameHandler,
                message = "Bob never saw Alice's name change",
            ) { it != null }
        assertContentEquals(nick, seenName)
    }
}
