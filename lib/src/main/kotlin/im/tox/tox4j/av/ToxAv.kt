package im.tox.tox4j.av

import im.tox.tox4j.av.callbacks.ToxAvEventListener
import im.tox.tox4j.av.data.AudioChannels
import im.tox.tox4j.av.data.BitRate
import im.tox.tox4j.av.data.SampleCount
import im.tox.tox4j.av.data.SamplingRate
import im.tox.tox4j.av.enums.ToxavCallControl
import im.tox.tox4j.core.ToxCore
import im.tox.tox4j.core.data.ToxFriendNumber

/**
 * Public audio/video API for Tox clients.
 *
 * This API can handle multiple calls. Each call has its state, in very rare occasions the library
 * can change the state of the call without apps knowledge.
 *
 * Like the Core API, this API is fully thread-safe. The library will ensure the proper
 * synchronisation of parallel calls.
 *
 * A common way to run ToxAv (multiple or single instance) is to have a thread, separate from tox
 * instance thread, running a simple [[ToxAv#iterate]] loop, sleeping for
 * [[ToxAv#iterationInterval]] * milliseconds on each iteration.
 *
 * Each ToxAv instance can be bound to only one Tox instance, and Tox instance can have only one
 * ToxAv instance. One must make sure to close ToxAv instance prior to closing the Tox instance
 * otherwise undefined behaviour occurs. Upon closing of ToxAv instance, all active calls will be
 * forcibly terminated without notifying peers.
 */
@kotlin.ExperimentalStdlibApi
interface ToxAv : AutoCloseable {
    /**
     * Start new A/V session. There can only be only one session per Tox instance.
     *
     * @param tox A compatible ToxCore implementation.
     * @return the new A/V session.
     * @throws ToxavNewException
     */
    fun create(tox: ToxCore): ToxAv

    /**
     * Releases all resources associated with the A/V session.
     *
     * If any calls were ongoing, these will be forcibly terminated without notifying peers. After
     * calling this function, no other functions may be called and the av pointer becomes invalid.
     */
    override fun close(): Unit

    /** Returns the interval in milliseconds when the next [[iterate]] call should be. */
    val iterationInterval: Int

    /**
     * Main loop for the session. This function needs to be called in intervals of
     * [[iterationInterval]] milliseconds. It is best called in the separate thread from
     * [[ToxCore.iterate]].
     */
    fun <S> iterate(
        handler: ToxAvEventListener<S>,
        state: S,
    ): S

    /**
     * Call a friend. This will start ringing the friend.
     *
     * It is the client's responsibility to stop ringing after a certain timeout, if such behaviour
     * is desired. If the client does not stop ringing, the library will not stop until the friend
     * is disconnected.
     *
     * @param friendNumber The friend number of the friend that should be called.
     * @param audioBitRate Audio bit rate in Kb/sec. Set this to 0 to disable audio sending.
     * @param videoBitRate Video bit rate in Kb/sec. Set this to 0 to disable video sending.
     * @throws ToxavCallException
     */
    fun call(
        friendNumber: ToxFriendNumber,
        audioBitRate: BitRate,
        videoBitRate: BitRate,
    ): Unit

    /**
     * Accept an incoming call.
     *
     * If answering fails for any reason, the call will still be pending and it is possible to try
     * and answer it later.
     *
     * @param friendNumber The friend number of the friend that is calling.
     * @param audioBitRate Audio bit rate in Kb/sec. Set this to 0 to disable audio sending.
     * @param videoBitRate Video bit rate in Kb/sec. Set this to 0 to disable video sending.
     * @throws ToxavAnswerException
     */
    fun answer(
        friendNumber: ToxFriendNumber,
        audioBitRate: BitRate,
        videoBitRate: BitRate,
    ): Unit

    /**
     * Sends a call control command to a friend.
     *
     * @param friendNumber The friend number of the friend to send the call control to.
     * @param control The control command to send.
     * @throws ToxavCallControlException
     */
    fun callControl(
        friendNumber: ToxFriendNumber,
        control: ToxavCallControl,
    ): Unit

    /**
     * Set the audio bit rate to be used in subsequent audio frames.
     *
     * @param friendNumber The friend number of the friend for which to set the audio bit rate.
     * @param audioBitRate The new audio bit rate in Kb/sec. Set to 0 to disable audio sending. Pass
     *   -1 to leave unchanged.
     * @throws ToxavBitRateSetException
     */
    fun setAudioBitRate(
        friendNumber: ToxFriendNumber,
        audioBitRate: BitRate,
    ): Unit

    /**
     * Set the video bit rate to be used in subsequent audio frames.
     *
     * @param friendNumber The friend number of the friend for which to set the audio bit rate.
     * @param videoBitRate The new video bit rate in Kb/sec. Set to 0 to disable video sending. Pass
     *   -1 to leave unchanged.
     * @throws ToxavBitRateSetException
     */
    fun setVideoBitRate(
        friendNumber: ToxFriendNumber,
        videoBitRate: BitRate,
    ): Unit

    /**
     * Send an audio frame to a friend.
     *
     * The expected format of the PCM data is: [s1c1][s1c2][...][s2c1][s2c2][...]... Meaning: sample
     * 1 for channel 1, sample 1 for channel 2, ... For mono audio, this has no meaning, every
     * sample is subsequent. For stereo, this means the expected format is LRLRLR... with samples
     * for left and right alternating.
     *
     * @param friendNumber The friend number of the friend to which to send an audio frame.
     * @param pcm An array of audio samples. The size of this array must be sample_count * channels.
     * @param sampleCount Number of samples in this frame in milliseconds. Valid numbers here are
     *   ((sample rate) * (audio length) / 1000), where audio length can be 2.5, 5, 10, 20, 40 or 60
     *   milliseconds.
     * @param channels Number of audio channels. Supported values are 1 and 2.
     * @param samplingRate Audio sampling rate used in this frame in Hz. Valid sampling rates are
     *   8000, 12000, 16000, 24000, or 48000.
     * @throws ToxavSendFrameException
     */
    fun audioSendFrame(
        friendNumber: ToxFriendNumber,
        pcm: ShortArray,
        sampleCount: SampleCount,
        channels: AudioChannels,
        samplingRate: SamplingRate,
    ): Unit

    /**
     * Send a video frame to a friend.
     *
     * Y - plane should be of size: height * width U - plane should be of size: (height/2) *
     * (width/2) V - plane should be of size: (height/2) * (width/2)
     *
     * @param friendNumber The friend number of the friend to which to send a video frame.
     * @param width Width of the frame in pixels.
     * @param height Height of the frame in pixels.
     * @param y Y (Luminance) plane data.
     * @param u U (Chroma) plane data.
     * @param v V (Chroma) plane data.
     * @throws ToxavSendFrameException
     */
    fun videoSendFrame(
        friendNumber: ToxFriendNumber,
        width: Int,
        height: Int,
        y: ByteArray,
        u: ByteArray,
        v: ByteArray,
    ): Unit
}
