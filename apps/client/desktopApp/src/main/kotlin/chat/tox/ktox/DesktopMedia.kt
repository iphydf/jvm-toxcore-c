package chat.tox.ktox

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread

/** 16-bit little-endian samples, as Java Sound wants them. */
private fun format(
    rate: Int,
    channels: Int,
) = AudioFormat(rate.toFloat(), 16, channels, true, false)

/** Plays what [playout] gives until [close], on a thread of its own. */
internal class Speaker(
    private val playout: Playout,
) : AutoCloseable {
    @Volatile private var running = true

    private val thread =
        thread(name = "speaker", isDaemon = true) {
            var line: SourceDataLine? = null
            var rate = 0
            var channels = 0
            while (running) {
                val frame = playout.next()
                if (frame.rate != rate || frame.channels != channels) {
                    line?.close()
                    rate = frame.rate
                    channels = frame.channels
                    line = open(format(rate, channels), frame.pcm.size * 2 * LINE_FRAMES)
                }
                val bytes = ByteArray(frame.pcm.size * 2)
                ByteBuffer
                    .wrap(bytes)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer()
                    .put(frame.pcm)
                // Without a line, the frame's time passes anyway.
                line?.write(bytes, 0, bytes.size) ?: Thread.sleep(frame.pcm.size * 1000L / channels / rate)
            }
            line?.drain()
            line?.close()
        }

    private fun open(
        format: AudioFormat,
        bytes: Int,
    ): SourceDataLine? =
        try {
            AudioSystem.getSourceDataLine(format).apply {
                open(format, bytes)
                start()
            }
        } catch (_: LineUnavailableException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    override fun close() {
        running = false
        thread.join()
    }

    private companion object {
        /** The line holds 60 ms: the playout keeps the rest. */
        const val LINE_FRAMES = 3
    }
}

/** A call's sound through Java Sound: the default microphone and speaker. */
internal class JavaSound private constructor(
    private val microphone: TargetDataLine?,
    recorded: (ShortArray) -> Unit,
    playout: Playout,
) : CallSound {
    @Volatile private var running = true

    private val speaker = Speaker(playout)

    private val recorder =
        microphone?.let { line ->
            thread(name = "microphone", isDaemon = true) {
                val bytes = ByteArray(AUDIO_FRAME * 2)
                while (running) {
                    var got = 0
                    while (got < bytes.size && running) {
                        val n = line.read(bytes, got, bytes.size - got)
                        if (n <= 0) break
                        got += n
                    }
                    if (got < bytes.size) continue
                    val pcm = ShortArray(AUDIO_FRAME)
                    ByteBuffer
                        .wrap(bytes)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()
                        .get(pcm)
                    recorded(pcm)
                }
            }
        }

    override fun close() {
        running = false
        // Stopping the line ends a read that waits.
        microphone?.stop()
        recorder?.join()
        microphone?.close()
        speaker.close()
    }

    companion object {
        fun open(
            recorded: (ShortArray) -> Unit,
            playout: Playout,
        ): JavaSound? {
            val format = format(AUDIO_RATE, 1)
            val microphone =
                try {
                    AudioSystem.getTargetDataLine(format).apply {
                        open(format, AUDIO_FRAME * 2 * 4)
                        start()
                    }
                } catch (_: LineUnavailableException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            val canPlay = AudioSystem.isLineSupported(DataLine.Info(SourceDataLine::class.java, format))
            return if (microphone == null && !canPlay) null else JavaSound(microphone, recorded, playout)
        }
    }
}

/** Video4Linux, through libktox-v4l2.so. */
internal object V4l2 {
    val loaded = runCatching { System.loadLibrary("ktox-v4l2") }.isSuccess

    /** The devices that capture pictures, in order. */
    fun devices(): List<String> =
        if (!loaded) {
            emptyList()
        } else {
            File("/dev")
                .listFiles { f -> f.name.startsWith("video") }
                .orEmpty()
                .sortedBy { it.name.removePrefix("video").toIntOrNull() ?: Int.MAX_VALUE }
                .map { it.path }
                .filter(::canCapture)
        }

    @JvmStatic external fun canCapture(path: String): Boolean

    /** Starts [path] in YUYV at about [width] × [height]; [size] gets the width, height and row stride. 0 if it can't. */
    @JvmStatic external fun open(
        path: String,
        width: Int,
        height: Int,
        size: IntArray,
    ): Long

    /** Waits up to [timeout] ms for a picture and copies it into [out]: its length, 0 if none came, or -1 if it broke. */
    @JvmStatic external fun read(
        handle: Long,
        out: ByteArray,
        timeout: Int,
    ): Int

    @JvmStatic external fun close(handle: Long)
}

/** A webcam filming on a thread of its own until [close]. */
internal class V4l2Camera private constructor(
    private val handle: Long,
    size: IntArray,
    frame: (VideoFrame) -> Unit,
) : AutoCloseable {
    @Volatile private var running = true

    private val thread =
        thread(name = "camera", isDaemon = true) {
            val (width, height, stride) = size
            val bytes = ByteArray(stride * height)
            val gate = FrameGate()
            val pool = FramePool()
            while (running) {
                val n = V4l2.read(handle, bytes, READ_TIMEOUT_MS)
                if (n < 0) break
                if (n >= stride * height && gate.open()) {
                    frame(yuyvToI420(bytes, stride, pool.take(width and 1.inv(), height and 1.inv())))
                }
            }
        }

    override fun close() {
        running = false
        thread.join()
        V4l2.close(handle)
    }

    companion object {
        private const val READ_TIMEOUT_MS = 200

        fun open(
            device: String,
            frame: (VideoFrame) -> Unit,
        ): V4l2Camera? {
            val size = IntArray(3)
            val handle = V4l2.open(device, VIDEO_WIDTH, VIDEO_HEIGHT, size)
            return if (handle == 0L) null else V4l2Camera(handle, size, frame)
        }
    }
}
