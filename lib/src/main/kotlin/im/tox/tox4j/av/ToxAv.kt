package im.tox.tox4j.av

import im.tox.tox4j.av.data.AudioChannels
import im.tox.tox4j.av.data.BitRate
import im.tox.tox4j.av.data.Height
import im.tox.tox4j.av.data.SampleCount
import im.tox.tox4j.av.data.SamplingRate
import im.tox.tox4j.av.data.Width
import im.tox.tox4j.av.enums.ToxavCallControl
import im.tox.tox4j.core.data.ToxFriendNumber

interface ToxAv : AutoCloseable {
    val iterationInterval: Int

    fun <ToxCoreState> iterate(
        handler: im.tox.tox4j.av.callbacks.ToxAvEventListener<ToxCoreState>,
        state: ToxCoreState,
    ): ToxCoreState

    /**
     * Call a friend. This will start ringing the friend.
     *
     * It is the client's responsibility to stop ringing after a certain timeout, if such behaviour is desired. If the client does not stop ringing, the library will not stop until the friend is disconnected. Audio and video receiving are both enabled by default.
     *
     * @param friendNumber The friend number of the friend that should be called.
     * @param audioBitRate Audio bit rate in kbit/sec. Set this to 0 to disable audio sending.
     * @param videoBitRate Video bit rate in kbit/sec. Set this to 0 to disable video sending.
     */
    fun call(
        friendNumber: ToxFriendNumber,
        audioBitRate: BitRate,
        videoBitRate: BitRate,
    )

    /**
     * Accept an incoming call.
     *
     * If answering fails for any reason, the call will still be pending and it is possible to try and answer it later. Audio and video receiving are both enabled by default.
     *
     * @param friendNumber The friend number of the friend that is calling.
     * @param audioBitRate Audio bit rate in kbit/sec. Set this to 0 to disable audio sending.
     * @param videoBitRate Video bit rate in kbit/sec. Set this to 0 to disable video sending.
     */
    fun answer(
        friendNumber: ToxFriendNumber,
        audioBitRate: BitRate,
        videoBitRate: BitRate,
    )

    /**
     * Sends a call control command to a friend.
     *
     * @param friendNumber The friend number of the friend this client is in a call with.
     * @param control The control command to send.
     *
     * @return true on success.
     */
    fun callControl(
        friendNumber: ToxFriendNumber,
        control: ToxavCallControl,
    )

    /**
     * Send an audio frame to a friend.
     *
     * The expected format of the PCM data is: `[s1c1][s1c2][...][s2c1][s2c2][...]...` Meaning: sample 1 for channel 1, sample 1 for channel 2, ... For mono audio, this has no meaning, every sample is subsequent. For stereo, this means the expected format is LRLRLR... with samples for left and right alternating.
     *
     * @param friendNumber The friend number of the friend to which to send an audio frame.
     * @param pcm An array of audio samples. The size of this array must be `sample_count * channels`.
     * @param sampleCount Number of samples in this frame. Valid numbers here are `((sample rate) * (audio length) / 1000)`, where audio length can be 2.5, 5, 10, 20, 40 or 60 milliseconds.
     * @param channels Number of audio channels. Supported values are 1 and 2.
     * @param samplingRate Audio sampling rate used in this frame. Valid sampling rates are 8000, 12000, 16000, 24000, or 48000.
     */
    fun audioSendFrame(
        friendNumber: ToxFriendNumber,
        pcm: ShortArray,
        sampleCount: SampleCount,
        channels: AudioChannels,
        samplingRate: SamplingRate,
    )

    /**
     * Set the bit rate to be used in subsequent audio frames.
     *
     * @param friendNumber The friend number of the friend for which to set the bit rate.
     * @param bitRate The new audio bit rate in kbit/sec. Set to 0 to disable.
     *
     * @return true on success.
     */
    fun audioSetBitRate(
        friendNumber: ToxFriendNumber,
        bitRate: BitRate,
    )

    /**
     * Send a video frame to a friend.
     *
     * The video frame needs to be planar YUV420. Y - plane should be of size: `width * height` U - plane should be of size: `(width/2) * (height/2)` V - plane should be of size: `(width/2) * (height/2)`
     *
     * @param friendNumber The friend number of the friend to which to send a video frame.
     * @param width Width of the frame in pixels.
     * @param height Height of the frame in pixels.
     * @param y Y (Luminance) plane data.
     * @param u U (Chroma) plane data.
     * @param v V (Chroma) plane data.
     */
    fun videoSendFrame(
        friendNumber: ToxFriendNumber,
        width: Width,
        height: Height,
        y: ByteArray,
        u: ByteArray,
        v: ByteArray,
    )

    /**
     * Set the bit rate to be used in subsequent video frames.
     *
     * @param friendNumber The friend number of the friend for which to set the bit rate.
     * @param bitRate The new video bit rate in kbit/sec. Set to 0 to disable.
     *
     * @return true on success.
     */
    fun videoSetBitRate(
        friendNumber: ToxFriendNumber,
        bitRate: BitRate,
    )
}
