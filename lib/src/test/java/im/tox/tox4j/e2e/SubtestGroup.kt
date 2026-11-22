package im.tox.tox4j.e2e

import im.tox.tox4j.core.ToxCoreEvent
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.core.data.ToxGroupMessageId
import im.tox.tox4j.core.data.ToxGroupNumber
import im.tox.tox4j.core.data.ToxGroupPeerNumber
import im.tox.tox4j.core.enums.ToxGroupPrivacyState
import im.tox.tox4j.core.enums.ToxMessageType
import kotlin.test.assertContentEquals

@OptIn(kotlin.ExperimentalStdlibApi::class)
object SubtestGroup {
    private val payload = "NgcHello".encodeToByteArray()

    fun run(harness: Tox4jHarness) {
        check(harness.toxes.size >= 2) { "SubtestGroup needs at least 2 toxes" }
        val alice = harness.toxes[0]
        val bob = harness.toxes[1]
        val bobFromAlice = alice.friendByPublicKey(bob.publicKey)
        val aliceFromBob = bob.friendByPublicKey(alice.publicKey)

        val aliceGroup =
            alice.groupNew(
                ToxGroupPrivacyState.PRIVATE,
                "NgcTest".encodeToByteArray(),
                "Alice".encodeToByteArray(),
            )
        alice.groupInviteFriend(aliceGroup, bobFromAlice)

        val invite = harness.waitForEvent<ToxCoreEvent.GroupInvite>("Bob never received Alice's NGC invite")
        check(invite.friendNumber == aliceFromBob) {
            "NGC invite came from an unexpected friend: ${invite.friendNumber}"
        }

        val bobGroup =
            bob.groupInviteAccept(
                invite.friendNumber,
                invite.inviteData,
                "Bob".encodeToByteArray(),
                ByteArray(0),
            )

        // Messages only arrive once the group connects, so resend until one does.
        val received =
            harness.waitFor(
                initial = null as ToxCoreEvent.GroupMessage?,
                handler = { found, event ->
                    found
                        ?: (event as? ToxCoreEvent.GroupMessage)?.takeIf { it.groupNumber == bobGroup && it.message.contentEquals(payload) }
                },
                timeoutMs = 60_000L,
                message = "Bob never received Alice's NGC message",
            ) { found ->
                if (found == null) {
                    try {
                        alice.groupSendMessage(aliceGroup, ToxMessageType.NORMAL, payload)
                    } catch (_: Exception) {
                        // Not connected yet.
                    }
                }
                found != null
            }!!
        assertContentEquals(payload, received.message)

        val newTopic = "JVM NGC Topic".encodeToByteArray()
        alice.groupSetTopic(aliceGroup, newTopic)
        harness.waitForEvent<ToxCoreEvent.GroupTopic>("Bob never saw Alice's group topic change") {
            it.groupNumber == bobGroup && it.topic.contentEquals(newTopic)
        }

        val newName = "Alice2".encodeToByteArray()
        alice.groupSelfSetName(aliceGroup, newName)
        harness.waitForEvent<ToxCoreEvent.GroupPeerName>("Bob never saw Alice's NGC nickname change") {
            it.groupNumber == bobGroup && it.name.contentEquals(newName)
        }
    }
}
