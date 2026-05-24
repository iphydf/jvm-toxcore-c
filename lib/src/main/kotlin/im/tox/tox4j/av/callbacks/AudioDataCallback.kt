package im.tox.tox4j.av.callbacks

import im.tox.tox4j.av.data.AudioChannels
import im.tox.tox4j.av.data.SampleCount
import im.tox.tox4j.av.data.SamplingRate
import im.tox.tox4j.core.data.ToxConferenceNumber
import im.tox.tox4j.core.data.ToxConferencePeerNumber

interface AudioDataCallback<ToxCoreState> {
    /**
     * The AV conference audio callback. Its parameters have the same
     * types as `audioReceiveFrame`'s, under different C names.
     */
    fun audioData(
        conferenceNumber: ToxConferenceNumber,
        peerNumber: ToxConferencePeerNumber,
        pcm: ShortArray,
        samples: SampleCount,
        channels: AudioChannels,
        sampleRate: SamplingRate,
        state: ToxCoreState,
    ): ToxCoreState = state
}
