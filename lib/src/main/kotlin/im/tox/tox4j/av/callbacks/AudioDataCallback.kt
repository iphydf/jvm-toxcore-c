package im.tox.tox4j.av.callbacks

import im.tox.tox4j.av.data.AudioChannels
import im.tox.tox4j.av.data.SampleCount
import im.tox.tox4j.av.data.SamplingRate
import im.tox.tox4j.core.data.ToxConferenceNumber
import im.tox.tox4j.core.data.ToxConferencePeerNumber

interface AudioDataCallback<ToxCoreState> {
    /**
     * The legacy AV-groupchat audio callback. The Kotlin parameter
     * types are the same as @audioReceiveFrame@ (`SampleCount`,
     * `SamplingRate`); the C names diverge (`samples`/`sample_rate`
     * here vs `sample_count`/`sampling_rate` there) because the
     * two C callback typedefs were introduced separately.
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
