package chat.tox.ktox

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.PI
import kotlin.math.sin

/** What calls record: 20 ms frames of 48 kHz mono. */
const val AUDIO_RATE = 48000
const val AUDIO_FRAME = AUDIO_RATE / 50

/** What cameras are asked for: phones turn it to 480 × 640. */
const val VIDEO_WIDTH = 640
const val VIDEO_HEIGHT = 480

/** At most this many pictures a second go out: each one takes the Tox thread a while to encode. */
const val VIDEO_FPS = 24

/**
 * A picture in I420: [y] has [width] × [height] samples, [u] and [v] half as many each way, in rows of the
 * given strides. The pictures we send have even sizes.
 */
class VideoFrame(
    val width: Int,
    val height: Int,
    val y: ByteArray,
    val u: ByteArray,
    val v: ByteArray,
    val yStride: Int = width,
    val uStride: Int = width / 2,
    val vStride: Int = width / 2,
    /** Shown mirrored: the user's own front camera, so it moves the way a mirror does. */
    val mirrored: Boolean = false,
) {
    fun mirrored() = VideoFrame(width, height, y, u, v, yStride, uStride, vStride, mirrored = true)

    /** The picture as ARGB pixels in [out], every [step]th pixel each way, for showing. */
    fun argb(
        step: Int,
        out: IntArray,
    ) {
        val w = width / step
        val h = height / step
        for (j in 0 until h) {
            val sy = j * step
            val yRow = sy * yStride
            val uRow = sy / 2 * uStride
            val vRow = sy / 2 * vStride
            for (i in 0 until w) {
                val sx = i * step
                val c = 298 * ((y[yRow + sx].toInt() and 0xFF) - 16)
                val d = (u[uRow + sx / 2].toInt() and 0xFF) - 128
                val e = (v[vRow + sx / 2].toInt() and 0xFF) - 128
                val r = clamp((c + 409 * e + 128) shr 8)
                val g = clamp((c - 100 * d - 208 * e + 128) shr 8)
                val b = clamp((c + 516 * d + 128) shr 8)
                out[j * w + if (mirrored) w - 1 - i else i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    private fun clamp(x: Int) =
        if (x < 0) {
            0
        } else if (x > 255) {
            255
        } else {
            x
        }
}

/** Turns a plane of [width] × [height] samples (in rows of [stride]) by [degrees] clockwise, into [out]. */
fun rotatePlane(
    plane: ByteArray,
    width: Int,
    height: Int,
    stride: Int,
    degrees: Int,
    out: ByteArray,
) {
    when (degrees) {
        90 -> {
            for (j in 0 until height) {
                for (i in 0 until width) out[i * height + (height - 1 - j)] = plane[j * stride + i]
            }
        }

        180 -> {
            for (j in 0 until height) {
                for (i in 0 until width) out[(height - 1 - j) * width + (width - 1 - i)] = plane[j * stride + i]
            }
        }

        270 -> {
            for (j in 0 until height) {
                for (i in 0 until width) out[(width - 1 - i) * height + j] = plane[j * stride + i]
            }
        }

        else -> {
            for (j in 0 until height) System.arraycopy(plane, j * stride, out, j * width, width)
        }
    }
}

/**
 * A YUYV picture (Y0 U Y1 V for each two pixels, [stride] bytes a row) in [out], an I420 frame of its size
 * without an odd last column or row. Each chroma sample is the average of the two rows it covers.
 */
fun yuyvToI420(
    src: ByteArray,
    stride: Int,
    out: VideoFrame,
): VideoFrame {
    val w = out.width
    val h = out.height
    val y = out.y
    val u = out.u
    val v = out.v
    for (j in 0 until h) {
        val row = j * stride
        for (i in 0 until w) y[j * w + i] = src[row + 2 * i]
    }
    for (j in 0 until h / 2) {
        val top = 2 * j * stride
        val bottom = top + stride
        for (i in 0 until w / 2) {
            u[j * w / 2 + i] = (((src[top + 4 * i + 1].toInt() and 0xFF) + (src[bottom + 4 * i + 1].toInt() and 0xFF) + 1) / 2).toByte()
            v[j * w / 2 + i] = (((src[top + 4 * i + 3].toInt() and 0xFF) + (src[bottom + 4 * i + 3].toInt() and 0xFF) + 1) / 2).toByte()
        }
    }
    return out
}

/** Lets through at most [fps] pictures a second, evenly: a 30 a second camera gives 24, not every other one. */
class FrameGate(
    fps: Int = VIDEO_FPS,
) {
    private val interval = 1_000_000_000L / fps
    private var next = 0L

    fun open(now: Long = System.nanoTime()): Boolean {
        if (now < next) return false
        next = maxOf(next + interval, now)
        return true
    }
}

/**
 * Packed I420 frames whose memory goes round again: a frame is good until [size] more have been taken. A camera
 * films into these, so a picture doesn't cost new memory each time.
 */
class FramePool(
    private val size: Int = 4,
) {
    private val frames = arrayOfNulls<VideoFrame>(size)
    private var next = 0

    fun take(
        width: Int,
        height: Int,
    ): VideoFrame {
        val old = frames[next]
        val frame =
            if (old != null && old.width == width && old.height == height) {
                old
            } else {
                VideoFrame(width, height, ByteArray(width * height), ByteArray(width * height / 4), ByteArray(width * height / 4))
            }
        frames[next] = frame
        next = (next + 1) % size
        return frame
    }
}

/**
 * Shows video frames as images, using the same memory again: an image stays good while the next two are made.
 * One for each picture on screen, used from one thread at a time.
 */
abstract class VideoImages {
    private var pixels = IntArray(0)

    fun image(
        frame: VideoFrame,
        step: Int,
    ): ImageBitmap {
        val width = frame.width / step
        val height = frame.height / step
        if (pixels.size != width * height) pixels = IntArray(width * height)
        frame.argb(step, pixels)
        return draw(pixels, width, height)
    }

    /** [argb] pixels as an image [width] wide. */
    protected abstract fun draw(
        argb: IntArray,
        width: Int,
        height: Int,
    ): ImageBitmap
}

/** The sound side of a call, which the platform runs: the microphone, and the speaker playing a [Playout]. */
interface CallSound : AutoCloseable {
    /** On phones: the loudspeaker, or the earpiece held to the ear. */
    fun setSpeaker(on: Boolean) {}
}

/** One frame of sound to play. */
class SoundFrame(
    val pcm: ShortArray,
    val channels: Int,
    val rate: Int,
)

/**
 * The friend's sound on its way to the speaker. Frames come in bursts (the Tox thread also encodes video), so
 * it keeps a few in hand and plays silence while it fills up again. The speaker's thread calls [next] each time
 * it has room for a frame; its device's buffer sets the pace.
 */
class Playout {
    private val frames = ArrayDeque<SoundFrame>()
    private var filling = true
    private var tone: Tone? = null
    private var toneSample = 0L

    /** The tone to play instead, while a call rings. */
    @Synchronized
    fun setTone(tone: Tone?) {
        this.tone = tone
        toneSample = 0
    }

    @Synchronized
    fun add(frame: SoundFrame) {
        frames.addLast(frame)
        // Too far behind: drop the oldest, so the delay doesn't grow.
        if (frames.size > MAX_FRAMES) repeat(frames.size - TARGET_FRAMES) { frames.removeFirst() }
    }

    /** The next frame to play: the friend's sound, a ringing tone, or silence. */
    @Synchronized
    fun next(): SoundFrame {
        tone?.let { t ->
            return SoundFrame(ShortArray(AUDIO_FRAME) { t.sample(toneSample + it) }, 1, AUDIO_RATE).also { toneSample += AUDIO_FRAME }
        }
        if (filling && frames.size >= START_FRAMES) filling = false
        if (filling || frames.isEmpty()) {
            filling = true
            val like = frames.firstOrNull()
            return SoundFrame(ShortArray((like?.pcm?.size ?: AUDIO_FRAME)), like?.channels ?: 1, like?.rate ?: AUDIO_RATE)
        }
        return frames.removeFirst()
    }

    private companion object {
        /** In frames of 20 ms: start once 60 ms are here, and keep at most 200. */
        const val START_FRAMES = 3
        const val TARGET_FRAMES = 4
        const val MAX_FRAMES = 10
    }
}

/** The tones while a call rings, at [AUDIO_RATE]. */
enum class Tone(
    private val hertz: List<Double>,
    /** Milliseconds on and off, over and over. */
    private val cadence: List<Int>,
) {
    /** The caller hears this until the friend answers: Europe's 425 Hz, a second on and four off. */
    RINGBACK(listOf(425.0), listOf(1000, 4000)),

    /** The desktop rings with this: two short chimes, then a pause. */
    RING(listOf(660.0, 880.0), listOf(300, 150, 300, 2000)),
    ;

    fun sample(n: Long): Short {
        val ms = (n * 1000 / AUDIO_RATE % cadence.sum()).toInt()
        var at = 0
        for ((i, length) in cadence.withIndex()) {
            if (ms < at + length) {
                if (i % 2 == 1) return 0
                val hz = hertz[i / 2 % hertz.size]
                return (sin(2 * PI * hz * n / AUDIO_RATE) * VOLUME).toInt().toShort()
            }
            at += length
        }
        return 0
    }

    private companion object {
        const val VOLUME = 6000.0
    }
}
