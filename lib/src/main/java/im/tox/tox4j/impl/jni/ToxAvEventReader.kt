package im.tox.tox4j.impl.jni

import im.tox.tox4j.av.ToxAvEvent
import im.tox.tox4j.av.data.ToxAvBitRate
import im.tox.tox4j.av.data.ToxAvChannels
import im.tox.tox4j.av.data.ToxAvHeight
import im.tox.tox4j.av.data.ToxAvSamplingRate
import im.tox.tox4j.av.data.ToxAvWidth
import im.tox.tox4j.core.data.ToxFriendNumber
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads what the C++ callbacks wrote: each event's tag, then its fields. */
internal object ToxAvEventReader {
    fun read(bytes: ByteArray?): List<ToxAvEvent> {
        if (bytes == null) return emptyList()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        return buildList {
            while (buf.hasRemaining()) add(event(buf))
        }
    }

    private fun event(buf: ByteBuffer): ToxAvEvent =
        when (val tag = buf.get().toInt()) {
            0 -> {
                ToxAvEvent.AudioBitRate(ToxFriendNumber(buf.int), ToxAvBitRate(buf.int))
            }

            1 -> {
                ToxAvEvent.AudioReceiveFrame(
                    ToxFriendNumber(buf.int),
                    buf.shorts(),
                    buf.long,
                    ToxAvChannels(buf.int.toByte()),
                    ToxAvSamplingRate(buf.int),
                )
            }

            2 -> {
                ToxAvEvent.Call(ToxFriendNumber(buf.int), buf.bool(), buf.bool())
            }

            3 -> {
                ToxAvEvent.CallState(ToxFriendNumber(buf.int), buf.int)
            }

            4 -> {
                ToxAvEvent.VideoBitRate(ToxFriendNumber(buf.int), ToxAvBitRate(buf.int))
            }

            5 -> {
                ToxAvEvent.VideoReceiveFrame(
                    ToxFriendNumber(buf.int),
                    ToxAvWidth(buf.int),
                    ToxAvHeight(buf.int),
                    buf.bytes(),
                    buf.bytes(),
                    buf.bytes(),
                    buf.int,
                    buf.int,
                    buf.int,
                )
            }

            else -> {
                error("Unknown event $tag")
            }
        }
}

private fun ByteBuffer.bool(): Boolean = get() != 0.toByte()

private fun ByteBuffer.bytes(): ByteArray = ByteArray(int).also(this::get)

private fun ByteBuffer.shorts(): ShortArray = ShortArray(int) { short }
