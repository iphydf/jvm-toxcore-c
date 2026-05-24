package im.tox.tox4j.e2e

import im.tox.tox4j.core.callbacks.ToxCoreEventListener
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.core.data.ToxGroupMessage
import im.tox.tox4j.core.data.ToxGroupMessageId
import im.tox.tox4j.core.data.ToxGroupName
import im.tox.tox4j.core.data.ToxGroupNumber
import im.tox.tox4j.core.data.ToxGroupPassword
import im.tox.tox4j.core.data.ToxGroupPeerNumber
import im.tox.tox4j.core.data.ToxGroupTopic
import im.tox.tox4j.core.enums.ToxGroupPrivacyState
import im.tox.tox4j.core.enums.ToxMessageType
import kotlin.test.assertContentEquals

/**
 * New Groupchat (NGC) invite → accept → message round-trip. Mirrors
 * `rs-toxcore-c/toxcore/tests/suite/group.rs::subtest_groups` (the basic
 * subset).
 *
 * Alice (tox 0) creates a private NGC, invites Bob (tox 1); Bob accepts;
 * Alice sends "NgcHello" until Bob receives it (NGC takes a few iterate
 * cycles to handshake before messages flow).
 */
@OptIn(kotlin.ExperimentalStdlibApi::class)
object SubtestGroup {
    private val payload = "NgcHello".encodeToByteArray()

    fun run(harness: Tox4jHarness) {
        check(harness.toxes.size >= 2) { "SubtestGroup needs at least 2 toxes" }
        val alice = harness.toxes[0]
        val bob = harness.toxes[1]
        val bobFromAlice = alice.friendByPublicKey(bob.publicKey)
        val aliceFromBob = bob.friendByPublicKey(alice.publicKey)

        // 1. Alice creates a private NGC group and invites Bob.
        val aliceGroup =
            alice.groupNew(
                ToxGroupPrivacyState.PRIVATE,
                ToxGroupName("NgcTest".encodeToByteArray()),
                ToxGroupName("Alice".encodeToByteArray()),
            )
        alice.groupInviteFriend(aliceGroup, bobFromAlice)

        // 2. Wait for Bob to see the invite.
        data class Invite(
            val friend: ToxFriendNumber,
            val inviteData: ByteArray,
        )
        val inviteHandler =
            object : ToxCoreEventListener<Invite?> {
                override fun groupInvite(
                    friendNumber: ToxFriendNumber,
                    inviteData: ByteArray,
                    groupName: ToxGroupName,
                    state: Invite?,
                ): Invite? = state ?: Invite(friendNumber, inviteData)
            }
        val invite =
            harness.waitFor(
                initial = null as Invite?,
                handler = inviteHandler,
                timeoutMs = 30_000L,
                message = "Bob never received Alice's NGC invite",
            ) { it != null }!!
        check(invite.friend.value == aliceFromBob.value) {
            "NGC invite came from an unexpected friend: ${invite.friend}"
        }

        // 3. Bob joins the group.
        val bobGroup =
            bob.groupInviteAccept(
                invite.friend,
                invite.inviteData,
                ToxGroupName("Bob".encodeToByteArray()),
                ToxGroupPassword(ByteArray(0)),
            )

        // 4. Alice sends until Bob receives — same retry-inside-predicate
        //    shape as the conference subtest.
        data class MsgState(
            val received: ByteArray? = null,
        )
        val messageHandler =
            object : ToxCoreEventListener<MsgState> {
                override fun groupMessage(
                    groupNumber: ToxGroupNumber,
                    peerId: ToxGroupPeerNumber,
                    messageType: ToxMessageType,
                    message: ToxGroupMessage,
                    messageId: ToxGroupMessageId,
                    state: MsgState,
                ): MsgState =
                    if (state.received != null) {
                        state
                    } else if (groupNumber.value == bobGroup.value && message.value.contentEquals(payload)) {
                        state.copy(received = message.value)
                    } else {
                        state
                    }
            }
        val finalState =
            harness.waitFor(
                initial = MsgState(),
                handler = messageHandler,
                timeoutMs = 60_000L,
                message = "Bob never received Alice's NGC message",
            ) { state ->
                if (state.received == null) {
                    try {
                        alice.groupSendMessage(
                            aliceGroup,
                            ToxMessageType.NORMAL,
                            ToxGroupMessage(payload),
                        )
                    } catch (_: Exception) {
                        // The peer-to-peer handshake isn't complete yet; iterate
                        // keeps it progressing.
                    }
                }
                state.received != null
            }
        assertContentEquals(payload, finalState.received)

        // 5. Topic propagation: Alice sets the topic, Bob's groupTopic
        //    callback must fire with the new value.
        val newTopic = "JVM NGC Topic".encodeToByteArray()
        alice.groupSetTopic(aliceGroup, ToxGroupTopic(newTopic))
        val topicHandler =
            object : ToxCoreEventListener<ByteArray?> {
                override fun groupTopic(
                    groupNumber: ToxGroupNumber,
                    peerId: ToxGroupPeerNumber,
                    topic: ToxGroupTopic,
                    state: ByteArray?,
                ): ByteArray? =
                    state ?: topic.value.takeIf {
                        groupNumber.value == bobGroup.value && topic.value.contentEquals(newTopic)
                    }
            }
        val seenTopic =
            harness.waitFor(
                initial = null as ByteArray?,
                handler = topicHandler,
                timeoutMs = 30_000L,
                message = "Bob never saw Alice's group topic change",
            ) { it != null }
        assertContentEquals(newTopic, seenTopic)

        // 6. Self-name propagation: Alice changes her group nickname, Bob
        //    sees a groupPeerName callback with the new value.
        val newName = "Alice2".encodeToByteArray()
        alice.groupSelfSetName(aliceGroup, ToxGroupName(newName))
        val peerNameHandler =
            object : ToxCoreEventListener<ByteArray?> {
                override fun groupPeerName(
                    groupNumber: ToxGroupNumber,
                    peerId: ToxGroupPeerNumber,
                    name: ToxGroupName,
                    state: ByteArray?,
                ): ByteArray? = state ?: name.value.takeIf { groupNumber.value == bobGroup.value && name.value.contentEquals(newName) }
            }
        val seenName =
            harness.waitFor(
                initial = null as ByteArray?,
                handler = peerNameHandler,
                timeoutMs = 30_000L,
                message = "Bob never saw Alice's NGC nickname change",
            ) { it != null }
        assertContentEquals(newName, seenName)
    }
}
