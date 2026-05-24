package im.tox.tox4j.e2e

import im.tox.tox4j.av.callbacks.ToxAvEventListener
import im.tox.tox4j.av.data.AudioChannels
import im.tox.tox4j.av.data.BitRate
import im.tox.tox4j.av.data.Height
import im.tox.tox4j.av.data.SampleCount
import im.tox.tox4j.av.data.SamplingRate
import im.tox.tox4j.av.data.Width
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.impl.jni.ToxAvImpl
import java.lang.Thread.sleep
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ToxAV call set-up plus audio and video frame round-trip. Mirrors
 * `rs-toxcore-c/toxcore/tests/suite/av.rs::subtest_toxav_call`.
 *
 * The audio and video data is the most JNI-marshaling-sensitive surface
 * we have: `audioSendFrame` takes a `ShortArray` (16-bit PCM samples),
 * `videoSendFrame` takes three `ByteArray`s (Y/U/V planes) plus width and
 * height. Both encodings cross the JNI boundary in their entirety and a
 * one-byte truncation, off-by-one in the size calculation, or wrong
 * jbyteArray vs. jshortArray would all surface here.
 *
 * The on-wire codec (Opus for audio, VP8 for video) is lossy, so we can't
 * byte-compare the received frame against the sent one — but we *can*
 * compare structural metadata (sample count, channel count, sampling
 * rate, frame width/height, plane sizes) and that's enough to catch
 * marshaling bugs.
 */
object SubtestToxav {
    // Distinctive audio: 20 ms of a 1 kHz sine at 48 kHz mono = 960 samples.
    private val audioFrame: ShortArray =
        ShortArray(960) { i ->
            (Short.MAX_VALUE.toInt() * kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / 48000.0)).toInt().toShort()
        }
    private val audioSampleCount = SampleCount(960)
    private val audioChannels = AudioChannels.Mono
    private val audioSr = SamplingRate.Rate48k

    // 64x64 YUV420p frame with a Y gradient. Y plane = W*H, U/V planes = (W/2)*(H/2).
    private const val VIDEO_W = 64
    private const val VIDEO_H = 64
    private val yPlane: ByteArray = ByteArray(VIDEO_W * VIDEO_H) { i -> (i % 256).toByte() }
    private val uPlane: ByteArray = ByteArray((VIDEO_W / 2) * (VIDEO_H / 2)) { 128.toByte() }
    private val vPlane: ByteArray = ByteArray((VIDEO_W / 2) * (VIDEO_H / 2)) { 128.toByte() }

    fun run(harness: Tox4jHarness) {
        check(harness.toxes.size >= 2) { "SubtestToxav needs at least 2 toxes" }
        val alice = harness.toxes[0]
        val bob = harness.toxes[1]
        val bobFromAlice = alice.friendByPublicKey(bob.publicKey)
        val aliceFromBob = bob.friendByPublicKey(alice.publicKey)

        val coreIdle = object : im.tox.tox4j.core.callbacks.ToxCoreEventListener<Unit> { }
        val aliceAvIdle = object : ToxAvEventListener<Unit> { }

        ToxAvImpl(alice).use { aliceAv ->
            ToxAvImpl(bob).use { bobAv ->
                // 1. Alice calls with both audio and video enabled.
                aliceAv.call(bobFromAlice, BitRate(48), BitRate(500))

                // 2. Drive iterate loops until Bob's `call` callback fires.
                val callSeen = BooleanArray(1)
                val bobAvCallHandler =
                    object : ToxAvEventListener<Unit> {
                        override fun call(
                            friendNumber: ToxFriendNumber,
                            audioEnabled: Boolean,
                            videoEnabled: Boolean,
                            state: Unit,
                        ) {
                            if (friendNumber.value == aliceFromBob.value &&
                                audioEnabled &&
                                videoEnabled
                            ) {
                                callSeen[0] = true
                            }
                        }
                    }
                run {
                    val deadline = System.currentTimeMillis() + 30_000L
                    while (!callSeen[0] && System.currentTimeMillis() < deadline) {
                        harness.iterate(coreIdle, Unit)
                        aliceAv.iterate(aliceAvIdle, Unit)
                        bobAv.iterate(bobAvCallHandler, Unit)
                    }
                }
                assertTrue(callSeen[0], "Bob never received Alice's audio+video call within 30s")

                // 3. Bob answers with audio and video.
                bobAv.answer(aliceFromBob, BitRate(48), BitRate(500))

                // 4. Audio + video frame loop. We accumulate the first received
                //    frame of each kind and assert their metadata, then move on.
                data class Received(
                    val audioSampleCount: Int = -1,
                    val audioChannels: AudioChannels? = null,
                    val audioSamplingRate: SamplingRate? = null,
                    val videoWidth: Int = -1,
                    val videoHeight: Int = -1,
                    val ySize: Int = -1,
                    val uSize: Int = -1,
                    val vSize: Int = -1,
                ) {
                    val audioSeen get() = audioChannels != null
                    val videoSeen get() = videoWidth > 0
                }

                val received = arrayOf(Received())
                val bobAvFrameHandler =
                    object : ToxAvEventListener<Unit> {
                        override fun audioReceiveFrame(
                            friendNumber: ToxFriendNumber,
                            pcm: ShortArray,
                            sampleCount: SampleCount,
                            channels: AudioChannels,
                            samplingRate: SamplingRate,
                            state: Unit,
                        ) {
                            if (friendNumber.value != aliceFromBob.value) return
                            if (received[0].audioSeen) return
                            received[0] =
                                received[0].copy(
                                    audioSampleCount = sampleCount.value,
                                    audioChannels = channels,
                                    audioSamplingRate = samplingRate,
                                )
                        }

                        override fun videoReceiveFrame(
                            friendNumber: ToxFriendNumber,
                            width: Width,
                            height: Height,
                            y: ByteArray,
                            u: ByteArray,
                            v: ByteArray,
                            ystride: Int,
                            ustride: Int,
                            vstride: Int,
                            state: Unit,
                        ) {
                            if (friendNumber.value != aliceFromBob.value) return
                            if (received[0].videoSeen) return
                            received[0] =
                                received[0].copy(
                                    videoWidth = width.value,
                                    videoHeight = height.value,
                                    ySize = y.size,
                                    uSize = u.size,
                                    vSize = v.size,
                                )
                        }
                    }

                run {
                    val deadline = System.currentTimeMillis() + 30_000L
                    while ((!received[0].audioSeen || !received[0].videoSeen) &&
                        System.currentTimeMillis() < deadline
                    ) {
                        harness.iterate(coreIdle, Unit)
                        aliceAv.iterate(aliceAvIdle, Unit)
                        bobAv.iterate(bobAvFrameHandler, Unit)
                        try {
                            aliceAv.audioSendFrame(bobFromAlice, audioFrame, audioSampleCount, audioChannels, audioSr)
                        } catch (_: Exception) {
                            // Call state may not yet be SENDING_A.
                        }
                        try {
                            aliceAv.videoSendFrame(bobFromAlice, Width(VIDEO_W), Height(VIDEO_H), yPlane, uPlane, vPlane)
                        } catch (_: Exception) {
                            // Call state may not yet be SENDING_V.
                        }
                        sleep(20)
                    }
                }

                val r = received[0]
                assertTrue(r.audioSeen, "Bob never received any audio frame within 30s")
                assertTrue(r.videoSeen, "Bob never received any video frame within 30s")

                // Audio: the codec preserves sample-count + channels + sampling
                // rate exactly even though the PCM bytes are lossy.
                assertEquals(audioSampleCount.value, r.audioSampleCount, "audio sample count mismatch")
                assertEquals(audioChannels, r.audioChannels, "audio channel count mismatch")
                assertEquals(audioSr, r.audioSamplingRate, "audio sampling rate mismatch")

                // Video: width/height must round-trip exactly, and each plane
                // must be at least its expected unpadded size (the codec may
                // pad with stride > width).
                assertEquals(VIDEO_W, r.videoWidth, "video width mismatch")
                assertEquals(VIDEO_H, r.videoHeight, "video height mismatch")
                assertTrue(r.ySize >= VIDEO_W * VIDEO_H, "Y plane too small: ${r.ySize}")
                assertTrue(r.uSize >= (VIDEO_W / 2) * (VIDEO_H / 2), "U plane too small: ${r.uSize}")
                assertTrue(r.vSize >= (VIDEO_W / 2) * (VIDEO_H / 2), "V plane too small: ${r.vSize}")
            }
        }
    }
}
