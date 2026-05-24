package im.tox.tox4j.impl.jni

import com.google.protobuf.ByteString
import im.tox.tox4j.av.callbacks.ToxAvEventListener
import im.tox.tox4j.av.data.AudioChannels
import im.tox.tox4j.av.data.BitRate
import im.tox.tox4j.av.data.Height
import im.tox.tox4j.av.data.SampleCount
import im.tox.tox4j.av.data.SamplingRate
import im.tox.tox4j.av.data.Width
import im.tox.tox4j.av.proto.AudioBitRate
import im.tox.tox4j.av.proto.AudioData
import im.tox.tox4j.av.proto.AudioReceiveFrame
import im.tox.tox4j.av.proto.AvEvents
import im.tox.tox4j.av.proto.Call
import im.tox.tox4j.av.proto.CallState
import im.tox.tox4j.av.proto.VideoBitRate
import im.tox.tox4j.av.proto.VideoReceiveFrame
import im.tox.tox4j.core.data.ToxConferenceNumber
import im.tox.tox4j.core.data.ToxConferencePeerNumber
import im.tox.tox4j.core.data.ToxFriendNumber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Per-arm verification of `ToxAvEventDispatch`. Each test feeds a single
 * proto event with distinct sentinel values into the dispatcher and asserts
 * that the matching listener method receives each value in the correct
 * argument position with the correct wrapper class.
 *
 * Sentinel values are chosen distinct per field so that a swapped same-type
 * binding (two `Boolean` arguments, three adjacent `Int` strides, …) would
 * surface as a mismatched assertion, not a silent pass.
 */
class ToxAvEventDispatchTest {
    // ---------------------------------------------------------------
    // Recording infrastructure
    // ---------------------------------------------------------------

    private sealed interface Event {
        data class Call(
            val friendNumber: ToxFriendNumber,
            val audioEnabled: Boolean,
            val videoEnabled: Boolean,
        ) : Event

        data class CallState(
            val friendNumber: ToxFriendNumber,
            val callState: Int,
        ) : Event

        data class AudioBitRate(
            val friendNumber: ToxFriendNumber,
            val audioBitRate: BitRate,
        ) : Event

        data class VideoBitRate(
            val friendNumber: ToxFriendNumber,
            val videoBitRate: BitRate,
        ) : Event

        data class AudioReceiveFrame(
            val friendNumber: ToxFriendNumber,
            val pcm: List<Short>,
            val sampleCount: SampleCount,
            val channels: AudioChannels,
            val samplingRate: SamplingRate,
        ) : Event

        data class VideoReceiveFrame(
            val friendNumber: ToxFriendNumber,
            val width: Width,
            val height: Height,
            val y: List<Byte>,
            val u: List<Byte>,
            val v: List<Byte>,
            val ystride: Int,
            val ustride: Int,
            val vstride: Int,
        ) : Event

        data class AudioData(
            val conferenceNumber: ToxConferenceNumber,
            val peerNumber: ToxConferencePeerNumber,
            val pcm: List<Short>,
            val samples: SampleCount,
            val channels: AudioChannels,
            val sampleRate: SamplingRate,
        ) : Event
    }

    private class Recorder : ToxAvEventListener<List<Event>> {
        override fun call(
            friendNumber: ToxFriendNumber,
            audioEnabled: Boolean,
            videoEnabled: Boolean,
            state: List<Event>,
        ): List<Event> = state + Event.Call(friendNumber, audioEnabled, videoEnabled)

        override fun callState(
            friendNumber: ToxFriendNumber,
            callState: Int,
            state: List<Event>,
        ): List<Event> = state + Event.CallState(friendNumber, callState)

        override fun audioBitRate(
            friendNumber: ToxFriendNumber,
            audioBitRate: BitRate,
            state: List<Event>,
        ): List<Event> = state + Event.AudioBitRate(friendNumber, audioBitRate)

        override fun videoBitRate(
            friendNumber: ToxFriendNumber,
            videoBitRate: BitRate,
            state: List<Event>,
        ): List<Event> = state + Event.VideoBitRate(friendNumber, videoBitRate)

        override fun audioReceiveFrame(
            friendNumber: ToxFriendNumber,
            pcm: ShortArray,
            sampleCount: SampleCount,
            channels: AudioChannels,
            samplingRate: SamplingRate,
            state: List<Event>,
        ): List<Event> = state + Event.AudioReceiveFrame(friendNumber, pcm.toList(), sampleCount, channels, samplingRate)

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
            state: List<Event>,
        ): List<Event> =
            state +
                Event.VideoReceiveFrame(
                    friendNumber,
                    width,
                    height,
                    y.toList(),
                    u.toList(),
                    v.toList(),
                    ystride,
                    ustride,
                    vstride,
                )

        override fun audioData(
            conferenceNumber: ToxConferenceNumber,
            peerNumber: ToxConferencePeerNumber,
            pcm: ShortArray,
            samples: SampleCount,
            channels: AudioChannels,
            sampleRate: SamplingRate,
            state: List<Event>,
        ): List<Event> = state + Event.AudioData(conferenceNumber, peerNumber, pcm.toList(), samples, channels, sampleRate)
    }

    private fun dispatch(event: AvEvents.Event.Builder): List<Event> {
        val payload =
            AvEvents
                .newBuilder()
                .addEvents(event)
                .build()
                .toByteArray()
        return ToxAvEventDispatch.dispatch(Recorder(), payload, emptyList())
    }

    /** Little-endian byte view of a `ShortArray` — matches `AvEvents` wire encoding. */
    private fun pcmBytes(samples: ShortArray): ByteString {
        val buf = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach(buf::putShort)
        return ByteString.copyFrom(buf.array())
    }

    // ---------------------------------------------------------------
    // Per-arm tests. One per oneof case in AvEvents.Event.event_type.
    // ---------------------------------------------------------------

    @Test
    fun call_bindsFriendBoolsInOrder() {
        // audioEnabled=true, videoEnabled=false distinguishes a swap.
        val recorded =
            dispatch(
                AvEvents.Event.newBuilder().setCall(
                    Call
                        .newBuilder()
                        .setFriendNumber(7)
                        .setAudioEnabled(true)
                        .setVideoEnabled(false),
                ),
            )
        assertEquals(listOf(Event.Call(ToxFriendNumber(7), true, false)), recorded)
    }

    @Test
    fun callState_bindsFriendAndBitmask() {
        // 0b101 — clearly a bitmask, not a single flag.
        val recorded =
            dispatch(
                AvEvents.Event.newBuilder().setCallState(
                    CallState.newBuilder().setFriendNumber(7).setState(0b101),
                ),
            )
        assertEquals(listOf(Event.CallState(ToxFriendNumber(7), 0b101)), recorded)
    }

    @Test
    fun audioBitRate_wrapsBitRate() {
        val recorded =
            dispatch(
                AvEvents.Event.newBuilder().setAudioBitRate(
                    AudioBitRate.newBuilder().setFriendNumber(7).setAudioBitRate(64),
                ),
            )
        assertEquals(listOf(Event.AudioBitRate(ToxFriendNumber(7), BitRate(64))), recorded)
    }

    @Test
    fun videoBitRate_wrapsBitRate() {
        // Use 128 (≠ audio's 64) to expose a method-name swap between the
        // two adjacent BitRate dispatches.
        val recorded =
            dispatch(
                AvEvents.Event.newBuilder().setVideoBitRate(
                    VideoBitRate.newBuilder().setFriendNumber(7).setVideoBitRate(128),
                ),
            )
        assertEquals(listOf(Event.VideoBitRate(ToxFriendNumber(7), BitRate(128))), recorded)
    }

    @Test
    fun audioReceiveFrame_wrapsPcmCountChannelsRate() {
        // sampleCount 480 = 10 ms @ 48 kHz; pick distinct sentinel sizes
        // so a swapped Int↔Long would mismatch.
        val recorded =
            dispatch(
                AvEvents.Event.newBuilder().setAudioReceiveFrame(
                    AudioReceiveFrame
                        .newBuilder()
                        .setFriendNumber(7)
                        .setPcm(pcmBytes(shortArrayOf(1, 2, 3)))
                        .setSampleCount(480L)
                        .setChannels(2)
                        .setSamplingRate(48000),
                ),
            )
        assertEquals(
            listOf(
                Event.AudioReceiveFrame(
                    ToxFriendNumber(7),
                    listOf<Short>(1, 2, 3),
                    SampleCount(480),
                    AudioChannels.Stereo,
                    SamplingRate.Rate48k,
                ),
            ),
            recorded,
        )
    }

    @Test
    fun videoReceiveFrame_planesAndStridesInOrder() {
        // Y / U / V planes get distinct byte sentinels so a swap among
        // the three ByteArrays would mismatch. Likewise strides 11/13/17
        // distinguish the three adjacent Ints.
        val recorded =
            dispatch(
                AvEvents.Event.newBuilder().setVideoReceiveFrame(
                    VideoReceiveFrame
                        .newBuilder()
                        .setFriendNumber(7)
                        .setWidth(320)
                        .setHeight(240)
                        .setY(ByteString.copyFrom(byteArrayOf(0x59)))
                        .setU(ByteString.copyFrom(byteArrayOf(0x55)))
                        .setV(ByteString.copyFrom(byteArrayOf(0x56)))
                        .setYstride(11)
                        .setUstride(13)
                        .setVstride(17),
                ),
            )
        assertEquals(
            listOf(
                Event.VideoReceiveFrame(
                    ToxFriendNumber(7),
                    Width(320),
                    Height(240),
                    listOf<Byte>(0x59),
                    listOf<Byte>(0x55),
                    listOf<Byte>(0x56),
                    11,
                    13,
                    17,
                ),
            ),
            recorded,
        )
    }

    @Test
    fun audioData_conferenceAndPeerInOrder() {
        // conferenceNumber 11 vs peerNumber 13 — distinct uint32s wrapped
        // into the two different value classes.
        val recorded =
            dispatch(
                AvEvents.Event.newBuilder().setAudioData(
                    AudioData
                        .newBuilder()
                        .setConferenceNumber(11)
                        .setPeerNumber(13)
                        .setPcm(pcmBytes(shortArrayOf(4, 5)))
                        .setSamples(24)
                        .setChannels(1)
                        .setSampleRate(16000),
                ),
            )
        assertEquals(
            listOf(
                Event.AudioData(
                    ToxConferenceNumber(11),
                    ToxConferencePeerNumber(13),
                    listOf<Short>(4, 5),
                    SampleCount(24),
                    AudioChannels.Mono,
                    SamplingRate.Rate16k,
                ),
            ),
            recorded,
        )
    }

    // ---------------------------------------------------------------
    // Defensive fallback behaviour: a future c-toxcore channel layout,
    // sampling rate, or maliciously crafted proto could deliver a
    // value the Kotlin wrapper rejects. The dispatcher must substitute
    // a safe default or skip the event rather than crash the iterate
    // loop and drop unprocessed events in the same batch.
    // ---------------------------------------------------------------

    @Test
    fun videoReceiveFrame_oversizedWidth_skipsBatchWithoutCrash() {
        // The Width value class rejects values > 65535 in its init
        // block; without a dispatcher-side guard, an oversized proto
        // value would throw IllegalArgumentException from the for-loop
        // and drop any unprocessed events in the same batch. The
        // dispatcher must skip the bad event but continue processing
        // the rest.
        val payload =
            AvEvents
                .newBuilder()
                .addEvents(
                    AvEvents.Event.newBuilder().setVideoReceiveFrame(
                        VideoReceiveFrame
                            .newBuilder()
                            .setFriendNumber(7)
                            .setWidth(70000) // > uint16 max — would fail Width init
                            .setHeight(240)
                            .setY(ByteString.copyFrom(byteArrayOf(0x59)))
                            .setU(ByteString.copyFrom(byteArrayOf(0x55)))
                            .setV(ByteString.copyFrom(byteArrayOf(0x56)))
                            .setYstride(11)
                            .setUstride(13)
                            .setVstride(17),
                    ),
                ).addEvents(
                    AvEvents.Event.newBuilder().setCall(
                        Call
                            .newBuilder()
                            .setFriendNumber(1)
                            .setAudioEnabled(true)
                            .setVideoEnabled(true),
                    ),
                ).build()
                .toByteArray()
        // The second event must still fire (any crash in the first
        // arm would bail the loop and drop this call event).
        val recorded = ToxAvEventDispatch.dispatch(Recorder(), payload, emptyList())
        assertEquals(
            listOf(Event.Call(ToxFriendNumber(1), true, true)),
            recorded.filterIsInstance<Event.Call>(),
        )
    }

    @Test
    fun audioReceiveFrame_negativeSampleCount_skipsBatchWithoutCrash() {
        // SampleCount rejects negatives. The proto field is uint64;
        // .toInt() can produce a negative for very large or
        // maliciously crafted values. Same skip-or-coerce contract as
        // the oversized-width case.
        val payload =
            AvEvents
                .newBuilder()
                .addEvents(
                    AvEvents.Event.newBuilder().setAudioReceiveFrame(
                        AudioReceiveFrame
                            .newBuilder()
                            .setFriendNumber(7)
                            .setPcm(pcmBytes(shortArrayOf(1)))
                            .setSampleCount(0xFFFFFFFFL) // .toInt() -> -1
                            .setChannels(2)
                            .setSamplingRate(48000),
                    ),
                ).addEvents(
                    AvEvents.Event.newBuilder().setCall(
                        Call
                            .newBuilder()
                            .setFriendNumber(1)
                            .setAudioEnabled(true)
                            .setVideoEnabled(true),
                    ),
                ).build()
                .toByteArray()
        val recorded = ToxAvEventDispatch.dispatch(Recorder(), payload, emptyList())
        assertEquals(
            listOf(Event.Call(ToxFriendNumber(1), true, true)),
            recorded.filterIsInstance<Event.Call>(),
        )
    }

    @Test
    fun audioReceiveFrame_unknownChannels_fallsBackToFirstEntry() {
        val recorded =
            dispatch(
                AvEvents.Event.newBuilder().setAudioReceiveFrame(
                    AudioReceiveFrame
                        .newBuilder()
                        .setFriendNumber(7)
                        .setPcm(pcmBytes(shortArrayOf(1)))
                        .setSampleCount(1L)
                        .setChannels(99)
                        .setSamplingRate(48000),
                ),
            )
        // No assertion failure; recorder got one event with the
        // default AudioChannels.values()[0].
        assertEquals(1, recorded.size)
        assertEquals(AudioChannels.values()[0], (recorded[0] as Event.AudioReceiveFrame).channels)
    }

    @Test
    fun audioReceiveFrame_unknownSamplingRate_fallsBackToFirstEntry() {
        val recorded =
            dispatch(
                AvEvents.Event.newBuilder().setAudioReceiveFrame(
                    AudioReceiveFrame
                        .newBuilder()
                        .setFriendNumber(7)
                        .setPcm(pcmBytes(shortArrayOf(1)))
                        .setSampleCount(1L)
                        .setChannels(2)
                        .setSamplingRate(99999),
                ),
            )
        assertEquals(1, recorded.size)
        assertEquals(SamplingRate.values()[0], (recorded[0] as Event.AudioReceiveFrame).samplingRate)
    }

    // ---------------------------------------------------------------
    // Aggregate behaviour
    // ---------------------------------------------------------------

    @Test
    fun emptyPayload_yieldsNoEvents() {
        assertEquals(emptyList(), ToxAvEventDispatch.dispatch(Recorder(), ByteArray(0), emptyList()))
    }

    @Test
    fun nullPayload_yieldsNoEvents() {
        assertEquals(emptyList(), ToxAvEventDispatch.dispatch(Recorder(), null, emptyList()))
    }

    @Test
    fun emptyEventBuilder_isDispatchedAsNoOp() {
        // A protobuf @oneof@ with no field set surfaces as
        // @EVENTTYPE_NOT_SET@. The dispatcher's @when@ closes over
        // this case explicitly (passing the accumulator through),
        // so an empty event in a batch should leave the state
        // unchanged rather than throw.
        assertEquals(emptyList(), dispatch(AvEvents.Event.newBuilder()))
    }

    @Test
    fun multipleEvents_fireInOrder() {
        val payload =
            AvEvents
                .newBuilder()
                .addEvents(
                    AvEvents.Event.newBuilder().setCall(
                        Call
                            .newBuilder()
                            .setFriendNumber(1)
                            .setAudioEnabled(true)
                            .setVideoEnabled(true),
                    ),
                ).addEvents(
                    AvEvents.Event.newBuilder().setCallState(
                        CallState.newBuilder().setFriendNumber(1).setState(0b10),
                    ),
                ).build()
                .toByteArray()
        assertEquals(
            listOf(
                Event.Call(ToxFriendNumber(1), true, true),
                Event.CallState(ToxFriendNumber(1), 0b10),
            ),
            ToxAvEventDispatch.dispatch(Recorder(), payload, emptyList()),
        )
    }
}
