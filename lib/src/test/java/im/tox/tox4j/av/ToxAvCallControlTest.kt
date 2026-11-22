package im.tox.tox4j.av

import im.tox.tox4j.av.data.ToxAvBitRate
import im.tox.tox4j.av.enums.ToxAvCallControl
import im.tox.tox4j.av.exceptions.ToxAvCallControlException
import im.tox.tox4j.e2e.Tox4jHarness
import im.tox.tox4j.impl.jni.ToxAvImpl
import kotlin.test.Test

/** Every call control toxav takes reaches the friend; one it can't send yet, it says so. */
class ToxAvCallControlTest {
    @Test
    fun muteAndUnmute_eachReachesTheFriend() {
        Tox4jHarness().use { harness ->
            harness.addTox()
            harness.addTox()
            harness.connect(0, 1)
            harness.waitForFriendConnected(0, 1)
            harness.waitForFriendConnected(1, 0)
            val (alice, bob) = harness.toxes
            val bobFromAlice = alice.friendByPublicKey(bob.publicKey)
            val aliceFromBob = bob.friendByPublicKey(alice.publicKey)

            ToxAvImpl(alice).use { aliceAv ->
                ToxAvImpl(bob).use { bobAv ->
                    aliceAv.call(bobFromAlice, AUDIO_BIT_RATE, NO_VIDEO)
                    until("Bob never got Alice's call") {
                        harness.iterate()
                        bobAv.iterate().any { it is ToxAvEvent.Call }
                    }
                    bobAv.answer(aliceFromBob, AUDIO_BIT_RATE, NO_VIDEO)
                    until("Alice never saw Bob answer") {
                        harness.iterate()
                        aliceAv.iterate().any { it is ToxAvEvent.CallState }
                    }

                    // One at a time, so each arrives: a lost one never does.
                    repeat(TOGGLES) { i ->
                        val control = if (i % 2 == 0) ToxAvCallControl.MUTE_AUDIO else ToxAvCallControl.UNMUTE_AUDIO
                        // Past toxcore's send budget, toxav can't tell the friend yet.
                        until("Alice could never make change ${i + 1}") {
                            alice.iterate()
                            bob.iterate()
                            try {
                                aliceAv.callControl(bobFromAlice, control)
                                true
                            } catch (e: ToxAvCallControlException) {
                                if (e.code != ToxAvCallControlException.Code.SYNC) throw e
                                false
                            }
                        }
                        until("Bob never saw Alice's change ${i + 1}") {
                            alice.iterate()
                            bob.iterate()
                            bobAv.iterate().any { it is ToxAvEvent.CallState && it.friendNumber == aliceFromBob }
                        }
                    }
                }
            }
        }
    }

    private fun until(
        message: String,
        done: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!done()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError(message)
        }
    }

    private companion object {
        const val TOGGLES = 200
        val AUDIO_BIT_RATE = ToxAvBitRate(48)
        val NO_VIDEO = ToxAvBitRate(0)
    }
}
