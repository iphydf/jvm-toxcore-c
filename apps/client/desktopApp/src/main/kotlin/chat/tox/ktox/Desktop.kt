package chat.tox.ktox

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.roundToInt

object Desktop : Platform {
    override fun copy(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    override fun pickFile(
        images: Boolean,
        picked: (PickedFile) -> Unit,
    ) {
        val dialog = FileDialog(null as Frame?, if (images) "Choose a picture" else "Send a file", FileDialog.LOAD)
        if (images) dialog.setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in PICTURES }
        dialog.isVisible = true
        val file = dialog.files.firstOrNull() ?: return
        picked(PickedFile(file.name) { file.inputStream() })
    }

    override fun open(
        file: File,
        name: String,
    ) {
        // xdg-open picks the user's app on Linux; Desktop.open blocks on some desktops.
        Thread {
            if (System.getProperty("os.name").startsWith("Linux")) {
                ProcessBuilder("xdg-open", file.path).start()
            } else {
                java.awt.Desktop
                    .getDesktop()
                    .open(file)
            }
        }.start()
    }

    override fun saveToDownloads(
        file: File,
        name: String,
    ): String? {
        val downloads = File(System.getProperty("user.home"), "Downloads").apply { mkdirs() }
        val target =
            generateSequence(0) { it + 1 }
                .map {
                    if (it ==
                        0
                    ) {
                        File(downloads, name)
                    } else {
                        File(downloads, "${name.substringBeforeLast('.')} ($it).${name.substringAfterLast('.')}")
                    }
                }.first { !it.exists() }
        return runCatching { file.copyTo(target) }.getOrNull()?.let { "Downloads as ${it.name}" }
    }

    override fun decodeImage(
        file: File,
        size: Int,
    ): ImageBitmap? {
        val image = runCatching { Image.makeFromEncoded(file.readBytes()) }.getOrNull() ?: return null
        val scale = min(1f, size.toFloat() / maxOf(image.width, image.height))
        if (scale == 1f) return image.toComposeImageBitmap()
        val width = (image.width * scale).roundToInt().coerceAtLeast(1)
        val height = (image.height * scale).roundToInt().coerceAtLeast(1)
        return draw(image, Rect.makeWH(image.width.toFloat(), image.height.toFloat()), width, height).toComposeImageBitmap()
    }

    override fun avatarPng(
        image: ByteArray,
        size: Int,
    ): ByteArray? {
        val decoded = runCatching { Image.makeFromEncoded(image) }.getOrNull() ?: return null
        // The middle square, scaled down.
        val side = min(decoded.width, decoded.height)
        val from = Rect.makeXYWH((decoded.width - side) / 2f, (decoded.height - side) / 2f, side.toFloat(), side.toFloat())
        val out = min(side, size)
        return draw(decoded, from, out, out).encodeToData(EncodedImageFormat.PNG)?.bytes
    }

    private fun draw(
        image: Image,
        from: Rect,
        width: Int,
        height: Int,
    ): Image {
        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.drawImageRect(image, from, Rect.makeWH(width.toFloat(), height.toFloat()))
        return surface.makeImageSnapshot()
    }

    override fun videoImages(): VideoImages =
        object : VideoImages() {
            private var bytes = ByteArray(0)

            override fun draw(
                argb: IntArray,
                width: Int,
                height: Int,
            ): ImageBitmap {
                // ARGB ints in little-endian bytes are BGRA. Skia copies them into its image.
                if (bytes.size != argb.size * 4) bytes = ByteArray(argb.size * 4)
                ByteBuffer
                    .wrap(bytes)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asIntBuffer()
                    .put(argb)
                return Image
                    .makeRaster(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), bytes, width * 4)
                    .toComposeImageBitmap()
            }
        }

    override fun openSound(
        recorded: (ShortArray) -> Unit,
        playout: Playout,
    ): CallSound? = JavaSound.open(recorded, playout)

    /** The webcams, looked for again now and then: they come and go. */
    private var devices = emptyList<String>()
    private var looked = 0L

    @Synchronized
    private fun devices(): List<String> {
        if (System.currentTimeMillis() - looked > LOOK_AGAIN_MS) {
            devices = V4l2.devices()
            looked = System.currentTimeMillis()
        }
        return devices
    }

    override val cameras get() = devices().size

    /** "Front" is the first webcam, the other is the next. */
    override fun openCamera(
        front: Boolean,
        frame: (VideoFrame) -> Unit,
    ): AutoCloseable? {
        val all = devices()
        val first = if (front || all.size < 2) 0 else 1
        return (all.drop(first) + all.take(first)).firstNotNullOfOrNull { V4l2Camera.open(it, frame) }
    }

    private var ringer: Speaker? = null

    /** Rings while a call comes in. */
    @Synchronized
    override fun callChanged(
        profile: ProfileId,
        call: Call?,
        friend: Friend?,
    ) {
        val ring = call != null && !call.outgoing && call.ringing
        if (ring && ringer == null) {
            ringer = Speaker(Playout().apply { setTone(Tone.RING) })
        } else if (!ring) {
            ringer?.close()
            ringer = null
        }
    }

    private const val LOOK_AGAIN_MS = 5_000L

    private val PICTURES = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
}
