package im.tox.tox4j.e2e

import im.tox.tox4j.core.callbacks.ToxCoreEventListener
import im.tox.tox4j.core.data.ToxConferenceMessage
import im.tox.tox4j.core.data.ToxConferenceNumber
import im.tox.tox4j.core.data.ToxConferencePeerNumber
import im.tox.tox4j.core.data.ToxConferenceTitle
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.core.enums.ToxConferenceType
import im.tox.tox4j.core.enums.ToxMessageType
import kotlin.test.assertContentEquals

/**
 * Conference invite → join → message → title-change. Mirrors
 * `rs-toxcore-c/toxcore/tests/suite/conference.rs::subtest_conference`.
 *
 * Alice (tox 0) creates a conference and invites Bob (tox 1); Bob accepts
 * via `conferenceJoin`; Alice sends "ConfHello" repeatedly until Bob
 * receives it; Alice then `conferenceSetTitle`s and Bob's `conferenceTitle`
 * callback must fire with the new title.
 */
@OptIn(kotlin.ExperimentalStdlibApi::class)
object SubtestConference {
    private val payload = "ConfHello".encodeToByteArray()
    private val newTitle = "JVM Conference".encodeToByteArray()

    fun run(harness: Tox4jHarness) {
        check(harness.toxes.size >= 2) { "SubtestConference needs at least 2 toxes" }
        val alice = harness.toxes[0]
        val bob = harness.toxes[1]
        val bobFromAlice = alice.friendByPublicKey(bob.publicKey)
        val aliceFromBob = bob.friendByPublicKey(alice.publicKey)

        // 1. Alice creates a conference and invites Bob.
        val aliceConf = alice.conferenceNew()
        alice.conferenceInvite(bobFromAlice, aliceConf)

        // 2. Wait for Bob to see the invite event; remember the cookie.
        data class Invite(
            val friend: ToxFriendNumber,
            val cookie: ByteArray,
        )
        val inviteHandler =
            object : ToxCoreEventListener<Invite?> {
                override fun conferenceInvite(
                    friendNumber: ToxFriendNumber,
                    type: ToxConferenceType,
                    cookie: ByteArray,
                    state: Invite?,
                ): Invite? = state ?: Invite(friendNumber, cookie)
            }
        val invite =
            harness.waitFor(
                initial = null as Invite?,
                handler = inviteHandler,
                timeoutMs = 30_000L,
                message = "Bob never received Alice's conference invite",
            ) { it != null }!!
        check(invite.friend.value == aliceFromBob.value) {
            "Conference invite came from an unexpected friend: ${invite.friend}"
        }

        // 3. Bob joins the conference using the cookie.
        val bobConf = bob.conferenceJoin(invite.friend, invite.cookie)

        // 4. Alice sends until Bob receives. The conference is not "connected"
        //    immediately after join — keep iterating and re-sending until the
        //    payload propagates. waitFor lets us retry inside the predicate.
        data class MsgState(
            val received: ByteArray? = null,
        )
        val messageHandler =
            object : ToxCoreEventListener<MsgState> {
                override fun conferenceMessage(
                    conferenceNumber: ToxConferenceNumber,
                    peerNumber: ToxConferencePeerNumber,
                    type: ToxMessageType,
                    message: ToxConferenceMessage,
                    state: MsgState,
                ): MsgState =
                    if (state.received != null) {
                        state
                    } else if (conferenceNumber.value == bobConf.value && message.value.contentEquals(payload)) {
                        state.copy(received = message.value)
                    } else {
                        state
                    }
            }
        val finalState =
            harness.waitFor(
                initial = MsgState(),
                handler = messageHandler,
                timeoutMs = 30_000L,
                message = "Bob never received Alice's conference message",
            ) { state ->
                // Re-send periodically inside the predicate. Tolerate transient
                // SENDQ errors before the conference is connected.
                if (state.received == null) {
                    try {
                        alice.conferenceSendMessage(
                            aliceConf,
                            ToxMessageType.NORMAL,
                            ToxConferenceMessage(payload),
                        )
                    } catch (_: Exception) {
                        // The conference isn't connected yet; iterate keeps the
                        // handshake progressing, and we'll try again next tick.
                    }
                }
                state.received != null
            }
        assertContentEquals(payload, finalState.received)

        // 5. Alice changes the title; Bob's conferenceTitle callback must fire.
        alice.conferenceSetTitle(aliceConf, ToxConferenceTitle(newTitle))
        val titleHandler =
            object : ToxCoreEventListener<ByteArray?> {
                override fun conferenceTitle(
                    conferenceNumber: ToxConferenceNumber,
                    peerNumber: ToxConferencePeerNumber,
                    title: ToxConferenceTitle,
                    state: ByteArray?,
                ): ByteArray? =
                    state ?: title.value.takeIf { conferenceNumber.value == bobConf.value && title.value.contentEquals(newTitle) }
            }
        val seenTitle =
            harness.waitFor(
                initial = null as ByteArray?,
                handler = titleHandler,
                timeoutMs = 30_000L,
                message = "Bob never saw Alice's conference title change",
            ) { it != null }
        assertContentEquals(newTitle, seenTitle)
    }
}
