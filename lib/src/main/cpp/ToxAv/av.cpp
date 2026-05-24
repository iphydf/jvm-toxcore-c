#include "ToxAv.h"

using namespace av;

/*
 * Class:     im_tox_tox4j_impl_ToxAvJni
 * Method:    toxavAudioSendFrame
 * Signature: (II[SJBI)V
 */
TOX_METHOD (void, AudioSendFrame,
  jint instanceNumber, jint friendNumber, jshortArray pcm, jlong sampleCount, jbyte channels, jint samplingRate)
{
  tox4j_assert (sampleCount >= 0);
  tox4j_assert (channels >= 0);
  tox4j_assert (samplingRate >= 0);

  // Compute the expected pcm length in @uint64_t@ so a @jlong@
  // @sampleCount@ multiplied by a small @channels@ can't silently
  // truncate on 32-bit JNI: e.g. @sampleCount = 0x1_0000_0000,
  // channels = 1@ truncates to 0 inside @size_t@ on 32-bit and
  // the equality check below would then accept an empty pcm.
  // Reject if the product overflows @SIZE_MAX@; otherwise the
  // narrowed @size_t@ matches the c-toxcore argument width.
  uint64_t expected = static_cast<uint64_t> (sampleCount)
                    * static_cast<uint64_t> (channels);
  if (expected > SIZE_MAX)
    return throw_tox_exception<ToxAV> (env, TOXAV_ERR_SEND_FRAME_INVALID);

  auto pcmData = fromJavaArray (env, pcm);
  if (pcmData.size () != static_cast<size_t> (expected))
    return throw_tox_exception<ToxAV> (env, TOXAV_ERR_SEND_FRAME_INVALID);

  return instances.with_instance_ign (env, instanceNumber,
    toxav_audio_send_frame, friendNumber, pcmData, sampleCount, channels, samplingRate
  );
}

/*
 * Class:     im_tox_tox4j_impl_ToxAvJni
 * Method:    toxavVideoSendFrame
 * Signature: (IIII[B[B[B[B)V
 */
TOX_METHOD (void, VideoSendFrame,
  jint instanceNumber, jint friendNumber, jint width, jint height, jbyteArray y, jbyteArray u, jbyteArray v)
{
  // Reject negatives before the cast — @static_cast<size_t>@ of a
  // negative @jint@ wraps to ~4 billion, and the size-equality
  // check below would then accept a small buffer for what the C
  // side reads as a huge frame. The Kotlin Width/Height value
  // classes enforce @[0, 0xFFFF]@, but the JNI shim is reachable
  // from any direct Java caller, so the guard belongs here.
  if (width < 0 || height < 0)
    return throw_tox_exception<ToxAV> (env, TOXAV_ERR_SEND_FRAME_INVALID);

  // Promote to size_t before multiplying — Width/Height admit
  // values up to uint16_t max (65535), and 65535 * 65535 overflows
  // signed int. The C signature is uint16_t anyway, so size_t is
  // the right type for the byte-count arithmetic.
  size_t ySize = static_cast<size_t> (width) * static_cast<size_t> (height);
  size_t uvSize = static_cast<size_t> (width / 2) * static_cast<size_t> (height / 2);

  auto yData = fromJavaArray (env, y);
  auto uData = fromJavaArray (env, u);
  auto vData = fromJavaArray (env, v);
  if (yData.size () != ySize ||
      uData.size () != uvSize ||
      vData.size () != uvSize)
    return throw_tox_exception<ToxAV> (env, TOXAV_ERR_SEND_FRAME_INVALID);

  return instances.with_instance_ign (env, instanceNumber,
    toxav_video_send_frame, friendNumber, width, height, yData, uData, vData
  );
}
