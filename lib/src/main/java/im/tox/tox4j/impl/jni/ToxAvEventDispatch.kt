package im.tox.tox4j.impl.jni

import im.tox.tox4j.av.callbacks.ToxAvEventListener
import im.tox.tox4j.av.data.AudioChannels
import im.tox.tox4j.av.data.BitRate
import im.tox.tox4j.av.data.Height
import im.tox.tox4j.av.data.SampleCount
import im.tox.tox4j.av.data.SamplingRate
import im.tox.tox4j.av.data.Width
import im.tox.tox4j.av.proto.AvEvents
import im.tox.tox4j.core.data.ToxConferenceNumber
import im.tox.tox4j.core.data.ToxConferencePeerNumber
import im.tox.tox4j.core.data.ToxFriendNumber

object ToxAvEventDispatch {
    private val audioChannelsValues = AudioChannels.values()
    private val samplingRateValues = SamplingRate.values()

    private fun toShortArray(bytes: com.google.protobuf.ByteString): ShortArray {
        val sb =
            bytes
                .asReadOnlyByteBuffer()
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
        return ShortArray(sb.remaining()).also(sb::get)
    }

    fun <S> dispatch(
        handler: ToxAvEventListener<S>,
        eventData: ByteArray?,
        state: S,
    ): S {
        if (eventData == null || eventData.isEmpty()) return state
        val events = AvEvents.parseFrom(eventData)
        var next = state
        for (event in events.eventsList) {
            next =
                when (event.eventTypeCase) {
                    AvEvents.Event.EventTypeCase.AUDIO_BIT_RATE ->
                        handler.audioBitRate(
                            ToxFriendNumber(event.audioBitRate.friendNumber),
                            BitRate(event.audioBitRate.audioBitRate),
                            next,
                        )
                    AvEvents.Event.EventTypeCase.AUDIO_DATA ->
                        handler.audioData(
                            ToxConferenceNumber(event.audioData.conferenceNumber),
                            ToxConferencePeerNumber(event.audioData.peerNumber),
                            toShortArray(event.audioData.pcm),
                            SampleCount.fromInt(event.audioData.samples),
                            audioChannelsValues.firstOrNull { it.value == event.audioData.channels } ?: audioChannelsValues[0],
                            samplingRateValues.firstOrNull { it.value == event.audioData.sampleRate } ?: samplingRateValues[0],
                            next,
                        )
                    AvEvents.Event.EventTypeCase.AUDIO_RECEIVE_FRAME ->
                        handler.audioReceiveFrame(
                            ToxFriendNumber(event.audioReceiveFrame.friendNumber),
                            toShortArray(event.audioReceiveFrame.pcm),
                            SampleCount.fromInt(event.audioReceiveFrame.sampleCount.toInt()),
                            audioChannelsValues.firstOrNull { it.value == event.audioReceiveFrame.channels } ?: audioChannelsValues[0],
                            samplingRateValues.firstOrNull { it.value == event.audioReceiveFrame.samplingRate } ?: samplingRateValues[0],
                            next,
                        )
                    AvEvents.Event.EventTypeCase.CALL ->
                        handler.call(
                            ToxFriendNumber(event.call.friendNumber),
                            event.call.audioEnabled,
                            event.call.videoEnabled,
                            next,
                        )
                    AvEvents.Event.EventTypeCase.CALL_STATE ->
                        handler.callState(
                            ToxFriendNumber(event.callState.friendNumber),
                            event.callState.state,
                            next,
                        )
                    AvEvents.Event.EventTypeCase.VIDEO_BIT_RATE ->
                        handler.videoBitRate(
                            ToxFriendNumber(event.videoBitRate.friendNumber),
                            BitRate(event.videoBitRate.videoBitRate),
                            next,
                        )
                    AvEvents.Event.EventTypeCase.VIDEO_RECEIVE_FRAME ->
                        handler.videoReceiveFrame(
                            ToxFriendNumber(event.videoReceiveFrame.friendNumber),
                            Width.fromInt(event.videoReceiveFrame.width),
                            Height.fromInt(event.videoReceiveFrame.height),
                            event.videoReceiveFrame.y.toByteArray(),
                            event.videoReceiveFrame.u.toByteArray(),
                            event.videoReceiveFrame.v.toByteArray(),
                            event.videoReceiveFrame.ystride,
                            event.videoReceiveFrame.ustride,
                            event.videoReceiveFrame.vstride,
                            next,
                        )
                    AvEvents.Event.EventTypeCase.EVENTTYPE_NOT_SET -> next
                }
        }
        return next
    }
}
