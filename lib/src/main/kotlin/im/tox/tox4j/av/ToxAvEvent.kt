package im.tox.tox4j.av

import im.tox.tox4j.av.data.ToxAvBitRate
import im.tox.tox4j.av.data.ToxAvChannels
import im.tox.tox4j.av.data.ToxAvHeight
import im.tox.tox4j.av.data.ToxAvSamplingRate
import im.tox.tox4j.av.data.ToxAvWidth
import im.tox.tox4j.core.data.ToxFriendNumber

/** What [ToxAv.iterate] reports. */
sealed interface ToxAvEvent {
    /**
     * The function type for the audio_bit_rate callback. The event is triggered when the network becomes too saturated for current bit rates at which point ToxAV suggests new bit rates.
     *
     * @property friendNumber The friend number of the friend for which to set the bit rate.
     * @property audioBitRate Suggested maximum audio bit rate in kbit/sec.
     */
    class AudioBitRate(
        val friendNumber: ToxFriendNumber,
        val audioBitRate: ToxAvBitRate,
    ) : ToxAvEvent

    /**
     * The function type for the audio_receive_frame callback. The callback can be called multiple times per single iteration depending on the amount of queued frames in the buffer. The received format is the same as in send function.
     *
     * @property friendNumber The friend number of the friend who sent an audio frame.
     * @property pcm An array of audio samples (`sample_count * channels` elements).
     * @property sampleCount The number of audio samples per channel in the PCM array.
     * @property channels Number of audio channels.
     * @property samplingRate Sampling rate used in this frame.
     */
    class AudioReceiveFrame(
        val friendNumber: ToxFriendNumber,
        val pcm: ShortArray,
        val sampleCount: Long,
        val channels: ToxAvChannels,
        val samplingRate: ToxAvSamplingRate,
    ) : ToxAvEvent

    /**
     * The function type for the call callback.
     *
     * @property friendNumber The friend number from which the call is incoming.
     * @property audioEnabled True if friend is sending audio.
     * @property videoEnabled True if friend is sending video.
     */
    class Call(
        val friendNumber: ToxFriendNumber,
        val audioEnabled: Boolean,
        val videoEnabled: Boolean,
    ) : ToxAvEvent

    /**
     * The function type for the call_state callback.
     *
     * @property friendNumber The friend number for which the call state changed.
     * @property state The bitmask of the new call state which is guaranteed to be different than the previous state. The state is set to 0 when the call is paused. The bitmask represents all the activities currently performed by the friend.
     */
    class CallState(
        val friendNumber: ToxFriendNumber,
        val state: Int,
    ) : ToxAvEvent

    /**
     * The function type for the video_bit_rate callback. The event is triggered when the network becomes too saturated for current bit rates at which point ToxAV suggests new bit rates.
     *
     * @property friendNumber The friend number of the friend for which to set the bit rate.
     * @property videoBitRate Suggested maximum video bit rate in kbit/sec.
     */
    class VideoBitRate(
        val friendNumber: ToxFriendNumber,
        val videoBitRate: ToxAvBitRate,
    ) : ToxAvEvent

    /**
     * The function type for the video_receive_frame callback.
     *
     * The size of plane data is derived from width and height as documented below.
     *
     * Strides represent padding for each plane that may or may not be present. You must handle strides in your image processing code. Strides are negative if the image is bottom-up hence why you MUST `abs()` it when calculating plane buffer size.
     *
     * @property friendNumber The friend number of the friend who sent a video frame.
     * @property width Width of the frame in pixels.
     * @property height Height of the frame in pixels.
     * @property y Luminosity plane. `Size = MAX(width, abs(ystride)) * height`.
     * @property u U chroma plane. `Size = MAX((width+1)/2, abs(ustride)) * ((height+1)/2)`.
     * @property v V chroma plane. `Size = MAX((width+1)/2, abs(vstride)) * ((height+1)/2)`.
     * @property ystride Luminosity plane stride.
     * @property ustride U chroma plane stride.
     * @property vstride V chroma plane stride.
     */
    class VideoReceiveFrame(
        val friendNumber: ToxFriendNumber,
        val width: ToxAvWidth,
        val height: ToxAvHeight,
        val y: ByteArray,
        val u: ByteArray,
        val v: ByteArray,
        val ystride: Int,
        val ustride: Int,
        val vstride: Int,
    ) : ToxAvEvent
}
