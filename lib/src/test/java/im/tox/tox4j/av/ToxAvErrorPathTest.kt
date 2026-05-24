package im.tox.tox4j.av

import im.tox.tox4j.av.data.AudioChannels
import im.tox.tox4j.av.data.BitRate
import im.tox.tox4j.av.data.Height
import im.tox.tox4j.av.data.SampleCount
import im.tox.tox4j.av.data.SamplingRate
import im.tox.tox4j.av.data.Width
import im.tox.tox4j.av.enums.ToxavCallControl
import im.tox.tox4j.av.exceptions.ToxavAnswerException
import im.tox.tox4j.av.exceptions.ToxavBitRateSetException
import im.tox.tox4j.av.exceptions.ToxavCallControlException
import im.tox.tox4j.av.exceptions.ToxavCallException
import im.tox.tox4j.av.exceptions.ToxavSendFrameException
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.core.options.ToxOptions
import im.tox.tox4j.impl.jni.ToxAvImpl
import im.tox.tox4j.impl.jni.ToxCoreImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Error-path coverage for `ToxAvImpl` methods. Single-tox-with-one-friend
 * setup is enough — the friend is never connected so most calls fail with
 * `FRIEND_NOT_CONNECTED` / `FRIEND_NOT_IN_CALL`.
 *
 * Written TDD-style: each test asserts the C-API documented error code
 * for one specific misuse.
 */
class ToxAvErrorPathTest {
    private val options = ToxOptions(localDiscoveryEnabled = false)

    private inline fun withAvAndFriend(block: (ToxAvImpl, ToxFriendNumber) -> Unit) {
        ToxCoreImpl(options).use { peer ->
            ToxCoreImpl(options).use { tox ->
                val friend = tox.friendAddNorequest(peer.publicKey)
                ToxAvImpl(tox).use { av ->
                    block(av, friend)
                }
            }
        }
    }

    // ---- call -----------------------------------------------------------

    @Test
    fun call_unknownFriend_throws() {
        ToxCoreImpl(options).use { tox ->
            ToxAvImpl(tox).use { av ->
                val ex =
                    assertFailsWith<ToxavCallException> {
                        av.call(ToxFriendNumber(999), BitRate(48), BitRate.Disabled)
                    }
                assertEquals(ToxavCallException.Code.FRIEND_NOT_FOUND, ex.code)
            }
        }
    }

    @Test
    fun call_unconnectedFriend_throws() {
        withAvAndFriend { av, friend ->
            val ex =
                assertFailsWith<ToxavCallException> {
                    av.call(friend, BitRate(48), BitRate.Disabled)
                }
            assertEquals(ToxavCallException.Code.FRIEND_NOT_CONNECTED, ex.code)
        }
    }

    @Test
    fun call_invalidBitRate_throws() {
        withAvAndFriend { av, friend ->
            // @toxav_call@ takes @uint32_t@; @BitRate(-2)@ flows in as
            // @0xFFFFFFFE@, which is far outside the valid kbit/s range
            // c-toxcore accepts and trips @INVALID_BIT_RATE@. (The
            // @Unchanged = -1@ sentinel is meaningful only on the
            // *_set_bit_rate APIs, not on @call@ itself.)
            val ex =
                assertFailsWith<ToxavCallException> {
                    av.call(friend, BitRate(-2), BitRate.Disabled)
                }
            assertEquals(ToxavCallException.Code.INVALID_BIT_RATE, ex.code)
        }
    }

    // ---- answer ---------------------------------------------------------

    @Test
    fun answer_unknownFriend_throws() {
        ToxCoreImpl(options).use { tox ->
            ToxAvImpl(tox).use { av ->
                val ex =
                    assertFailsWith<ToxavAnswerException> {
                        av.answer(ToxFriendNumber(999), BitRate(48), BitRate.Disabled)
                    }
                assertEquals(ToxavAnswerException.Code.FRIEND_NOT_FOUND, ex.code)
            }
        }
    }

    @Test
    fun answer_notCalling_throws() {
        withAvAndFriend { av, friend ->
            // The friend exists but isn't calling us → FRIEND_NOT_CALLING.
            val ex =
                assertFailsWith<ToxavAnswerException> {
                    av.answer(friend, BitRate(48), BitRate.Disabled)
                }
            assertEquals(ToxavAnswerException.Code.FRIEND_NOT_CALLING, ex.code)
        }
    }

    // ---- callControl ----------------------------------------------------

    @Test
    fun callControl_unknownFriend_throws() {
        ToxCoreImpl(options).use { tox ->
            ToxAvImpl(tox).use { av ->
                val ex =
                    assertFailsWith<ToxavCallControlException> {
                        av.callControl(ToxFriendNumber(999), ToxavCallControl.CANCEL)
                    }
                assertEquals(ToxavCallControlException.Code.FRIEND_NOT_FOUND, ex.code)
            }
        }
    }

    @Test
    fun callControl_notInCall_throws() {
        withAvAndFriend { av, friend ->
            val ex =
                assertFailsWith<ToxavCallControlException> {
                    av.callControl(friend, ToxavCallControl.PAUSE)
                }
            assertEquals(ToxavCallControlException.Code.FRIEND_NOT_IN_CALL, ex.code)
        }
    }

    // ---- audioSetBitRate / videoSetBitRate ------------------------------

    @Test
    fun audioSetBitRate_unknownFriend_throws() {
        ToxCoreImpl(options).use { tox ->
            ToxAvImpl(tox).use { av ->
                val ex =
                    assertFailsWith<ToxavBitRateSetException> {
                        av.audioSetBitRate(ToxFriendNumber(999), BitRate(48))
                    }
                assertEquals(ToxavBitRateSetException.Code.FRIEND_NOT_FOUND, ex.code)
            }
        }
    }

    @Test
    fun audioSetBitRate_notInCall_throws() {
        withAvAndFriend { av, friend ->
            val ex =
                assertFailsWith<ToxavBitRateSetException> {
                    av.audioSetBitRate(friend, BitRate(48))
                }
            assertEquals(ToxavBitRateSetException.Code.FRIEND_NOT_IN_CALL, ex.code)
        }
    }

    @Test
    fun videoSetBitRate_unknownFriend_throws() {
        ToxCoreImpl(options).use { tox ->
            ToxAvImpl(tox).use { av ->
                val ex =
                    assertFailsWith<ToxavBitRateSetException> {
                        av.videoSetBitRate(ToxFriendNumber(999), BitRate(500))
                    }
                assertEquals(ToxavBitRateSetException.Code.FRIEND_NOT_FOUND, ex.code)
            }
        }
    }

    // ---- audioSendFrame / videoSendFrame --------------------------------

    @Test
    fun audioSendFrame_unknownFriend_throws() {
        ToxCoreImpl(options).use { tox ->
            ToxAvImpl(tox).use { av ->
                val pcm = ShortArray(960)
                val ex =
                    assertFailsWith<ToxavSendFrameException> {
                        av.audioSendFrame(
                            ToxFriendNumber(999),
                            pcm,
                            SampleCount(960),
                            AudioChannels.Mono,
                            SamplingRate.Rate48k,
                        )
                    }
                assertEquals(ToxavSendFrameException.Code.FRIEND_NOT_FOUND, ex.code)
            }
        }
    }

    @Test
    fun videoSendFrame_unknownFriend_throws() {
        ToxCoreImpl(options).use { tox ->
            ToxAvImpl(tox).use { av ->
                val plane = ByteArray(100 * 100)
                val chroma = ByteArray(50 * 50)
                val ex =
                    assertFailsWith<ToxavSendFrameException> {
                        av.videoSendFrame(ToxFriendNumber(999), Width(100), Height(100), plane, chroma, chroma)
                    }
                assertEquals(ToxavSendFrameException.Code.FRIEND_NOT_FOUND, ex.code)
            }
        }
    }

    @Test
    fun audioSendFrame_pcmSizeMismatch_throws() {
        // tox_av documents that pcm.length must equal
        // sample_count * channels (16-bit samples interleaved).
        // The C side surfaces a mismatch via
        // TOXAV_ERR_SEND_FRAME_INVALID; verify the JNI layer
        // forwards it as ToxavSendFrameException rather than e.g.
        // an out-of-bounds memory access on the C side.
        withAvAndFriend { av, friend ->
            val claimedSamples = 480
            // pcm should be 480*2 = 960 for stereo; give half.
            val undersizedPcm = ShortArray(claimedSamples)
            val ex =
                assertFailsWith<ToxavSendFrameException> {
                    av.audioSendFrame(
                        friend,
                        undersizedPcm,
                        SampleCount(claimedSamples),
                        AudioChannels.Stereo,
                        SamplingRate.Rate48k,
                    )
                }
            assertEquals(ToxavSendFrameException.Code.INVALID, ex.code)
        }
    }

    @Test
    fun samplingRate_enumValuesMatchCToxcoreAllowedSet() {
        // The C side accepts only 8/12/16/24/48 kHz; the Kotlin
        // SamplingRate enum guards the user against picking
        // anything else, so reaching INVALID via the wrapper isn't
        // possible — verify the wrapper enforces it by enumerating
        // the allowed values.
        val allowed = setOf(8000, 12000, 16000, 24000, 48000)
        for (rate in SamplingRate.values()) {
            assert(rate.value in allowed) { "${rate.name} value ${rate.value} not in c-toxcore allowed set" }
        }
    }
}
