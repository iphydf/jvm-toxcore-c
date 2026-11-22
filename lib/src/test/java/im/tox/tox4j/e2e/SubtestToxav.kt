package im.tox.tox4j.e2e

import im.tox.tox4j.av.ToxAvEvent
import im.tox.tox4j.av.data.ToxAvBitRate
import im.tox.tox4j.av.data.ToxAvChannels
import im.tox.tox4j.av.data.ToxAvHeight
import im.tox.tox4j.av.data.ToxAvSamplingRate
import im.tox.tox4j.av.data.ToxAvWidth
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.impl.jni.ToxAvImpl
import java.lang.Thread.sleep
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A call with an audio and a video frame. The codecs are lossy, so this
 * compares the frames' shape, not their bytes.
 */
object SubtestToxav {
    // 20 ms of a 1 kHz sine at 48 kHz mono.
    private val audioFrame: ShortArray =
        ShortArray(960) { i ->
            (Short.MAX_VALUE.toInt() * kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / 48000.0)).toInt().toShort()
        }
    private const val AUDIO_SAMPLE_COUNT = 960L
    private val audioChannels = ToxAvChannels(1)
    private val audioSr = ToxAvSamplingRate(48000)

    private const val VIDEO_W = 64
    private const val VIDEO_H = 64
    private const val CHROMA_W = VIDEO_W / 2
    private const val CHROMA_H = VIDEO_H / 2
    private val yPlane: ByteArray = ByteArray(VIDEO_W * VIDEO_H) { i -> (i % 256).toByte() }
    private val uPlane: ByteArray = ByteArray(CHROMA_W * CHROMA_H) { 128.toByte() }
    private val vPlane: ByteArray = ByteArray(CHROMA_W * CHROMA_H) { 128.toByte() }

    fun run(harness: Tox4jHarness) {
        check(harness.toxes.size >= 2) { "SubtestToxav needs at least 2 toxes" }
        val alice = harness.toxes[0]
        val bob = harness.toxes[1]
        val bobFromAlice = alice.friendByPublicKey(bob.publicKey)
        val aliceFromBob = bob.friendByPublicKey(alice.publicKey)

        ToxAvImpl(alice).use { aliceAv ->
            ToxAvImpl(bob).use { bobAv ->
                // Everyone iterates; Bob's AV events go to handle.
                fun iterate(handle: (ToxAvEvent) -> Unit) {
                    harness.iterate()
                    aliceAv.iterate()
                    bobAv.iterate().forEach(handle)
                }

                aliceAv.call(bobFromAlice, ToxAvBitRate(48), ToxAvBitRate(500))

                var callSeen = false
                run {
                    val deadline = System.currentTimeMillis() + 30_000L
                    while (!callSeen && System.currentTimeMillis() < deadline) {
                        iterate { event ->
                            if (event is ToxAvEvent.Call && event.friendNumber == aliceFromBob && event.audioEnabled &&
                                event.videoEnabled
                            ) {
                                callSeen = true
                            }
                        }
                    }
                }
                assertTrue(callSeen, "Bob never received Alice's audio+video call within 30s")

                bobAv.answer(aliceFromBob, ToxAvBitRate(48), ToxAvBitRate(500))

                var audio: ToxAvEvent.AudioReceiveFrame? = null
                var video: ToxAvEvent.VideoReceiveFrame? = null
                run {
                    val deadline = System.currentTimeMillis() + 30_000L
                    while ((audio == null || video == null) && System.currentTimeMillis() < deadline) {
                        iterate { event ->
                            if (event is ToxAvEvent.AudioReceiveFrame && event.friendNumber == aliceFromBob) audio = audio ?: event
                            if (event is ToxAvEvent.VideoReceiveFrame && event.friendNumber == aliceFromBob) video = video ?: event
                        }
                        try {
                            aliceAv.audioSendFrame(bobFromAlice, audioFrame, AUDIO_SAMPLE_COUNT, audioChannels, audioSr)
                        } catch (_: Exception) {
                            // Call state may not yet be SENDING_A.
                        }
                        try {
                            aliceAv.videoSendFrame(bobFromAlice, ToxAvWidth(VIDEO_W), ToxAvHeight(VIDEO_H), yPlane, uPlane, vPlane)
                        } catch (_: Exception) {
                            // Call state may not yet be SENDING_V.
                        }
                        sleep(20)
                    }
                }

                val a = assertNotNull(audio, "Bob never received any audio frame within 30s")
                val v = assertNotNull(video, "Bob never received any video frame within 30s")

                assertEquals(AUDIO_SAMPLE_COUNT, a.sampleCount, "audio sample count mismatch")
                assertEquals(audioChannels, a.channels, "audio channel count mismatch")
                assertEquals(audioSr, a.samplingRate, "audio sampling rate mismatch")
                assertEquals(a.sampleCount * a.channels.value, a.pcm.size.toLong(), "PCM length mismatch")

                // Planes can be padded to the stride.
                assertEquals(VIDEO_W, v.width.value, "video width mismatch")
                assertEquals(VIDEO_H, v.height.value, "video height mismatch")
                assertTrue(v.y.size >= VIDEO_W * VIDEO_H, "Y plane too small: ${v.y.size}")
                assertTrue(v.u.size >= CHROMA_W * CHROMA_H, "U plane too small: ${v.u.size}")
                assertTrue(v.v.size >= CHROMA_W * CHROMA_H, "V plane too small: ${v.v.size}")
            }
        }
    }
}
