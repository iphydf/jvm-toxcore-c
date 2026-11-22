package im.tox.tox4j.e2e

import im.tox.tox4j.core.ToxCoreEvent
import im.tox.tox4j.core.data.ToxConferenceNumber
import im.tox.tox4j.core.data.ToxConferencePeerNumber
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.core.enums.ToxConferenceType
import im.tox.tox4j.core.enums.ToxMessageType
import kotlin.test.assertContentEquals

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

        val aliceConf = alice.conferenceNew()
        alice.conferenceInvite(bobFromAlice, aliceConf)

        val invite = harness.waitForEvent<ToxCoreEvent.ConferenceInvite>("Bob never received Alice's conference invite")
        check(invite.friendNumber == aliceFromBob) {
            "Conference invite came from an unexpected friend: ${invite.friendNumber}"
        }

        val bobConf = bob.conferenceJoin(invite.friendNumber, invite.cookie)

        // Messages only arrive once the conference connects, so resend until one does.
        val received =
            harness.waitFor(
                initial = null as ToxCoreEvent.ConferenceMessage?,
                handler = { found, event ->
                    found
                        ?: (event as? ToxCoreEvent.ConferenceMessage)?.takeIf {
                            it.conferenceNumber == bobConf &&
                                it.message.contentEquals(payload)
                        }
                },
                message = "Bob never received Alice's conference message",
            ) { found ->
                if (found == null) {
                    try {
                        alice.conferenceSendMessage(aliceConf, ToxMessageType.NORMAL, payload)
                    } catch (_: Exception) {
                        // Not connected yet.
                    }
                }
                found != null
            }!!
        assertContentEquals(payload, received.message)

        alice.conferenceSetTitle(aliceConf, newTitle)
        harness.waitForEvent<ToxCoreEvent.ConferenceTitle>("Bob never saw Alice's conference title change") {
            it.conferenceNumber == bobConf && it.title.contentEquals(newTitle)
        }
    }
}
