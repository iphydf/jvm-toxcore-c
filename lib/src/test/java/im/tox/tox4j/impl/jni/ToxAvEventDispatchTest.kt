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

/** Every field gets a distinct value, so a swapped argument fails. */
class ToxAvEventDispatchTest {
    // Recording infrastructure

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

    /** Little-endian, as in `AvEvents`. */
    private fun pcmBytes(samples: ShortArray): ByteString {
        val buf = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach(buf::putShort)
        return ByteString.copyFrom(buf.array())
    }

    // One test per AvEvents.Event case.

    @Test
    fun call_bindsFriendBoolsInOrder() {
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

    // Values a wrapper rejects must not drop the rest of the batch.

    @Test
    fun videoReceiveFrame_oversizedWidth_skipsBatchWithoutCrash() {
        val payload =
            AvEvents
                .newBuilder()
                .addEvents(
                    AvEvents.Event.newBuilder().setVideoReceiveFrame(
                        VideoReceiveFrame
                            .newBuilder()
                            .setFriendNumber(7)
                            .setWidth(70000) // > 65535
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
        val recorded = ToxAvEventDispatch.dispatch(Recorder(), payload, emptyList())
        assertEquals(
            listOf(Event.Call(ToxFriendNumber(1), true, true)),
            recorded.filterIsInstance<Event.Call>(),
        )
    }

    @Test
    fun audioReceiveFrame_negativeSampleCount_skipsBatchWithoutCrash() {
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

    // Aggregate behaviour

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
