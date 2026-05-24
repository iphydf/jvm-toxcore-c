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

/** The friend is never connected. */
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
            // BitRate(-2) reaches C as 0xFFFFFFFE. (-1 means "unchanged" only to the
            // *_set_bit_rate calls.)
            val ex =
                assertFailsWith<ToxavCallException> {
                    av.call(friend, BitRate(-2), BitRate.Disabled)
                }
            assertEquals(ToxavCallException.Code.INVALID_BIT_RATE, ex.code)
        }
    }

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
            val ex =
                assertFailsWith<ToxavAnswerException> {
                    av.answer(friend, BitRate(48), BitRate.Disabled)
                }
            assertEquals(ToxavAnswerException.Code.FRIEND_NOT_CALLING, ex.code)
        }
    }

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
        // pcm must hold sample_count * channels samples.
        withAvAndFriend { av, friend ->
            val claimedSamples = 480
            // Half of what stereo needs.
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
        // The only rates C accepts. The enum can't hold any other.
        val allowed = setOf(8000, 12000, 16000, 24000, 48000)
        for (rate in SamplingRate.values()) {
            assert(rate.value in allowed) { "${rate.name} value ${rate.value} not in c-toxcore allowed set" }
        }
    }
}
