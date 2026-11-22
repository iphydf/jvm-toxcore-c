package chat.tox.ktox

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.display.DisplayManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.view.Display
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.Executor
import kotlin.concurrent.thread
import kotlin.math.abs

private const val TAG = "kTox"

/** A call's sound: the microphone with the phone's echo cancelling, and the earpiece or the loudspeaker. */
internal class AndroidSound(
    context: Context,
    recorded: (ShortArray) -> Unit,
    private val playout: Playout,
) : CallSound {
    private val audio = context.getSystemService(AudioManager::class.java)

    @Volatile private var running = true

    init {
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    private val record: AudioRecord? =
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            null
        } else {
            val min = AudioRecord.getMinBufferSize(AUDIO_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    AUDIO_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(min, AUDIO_FRAME * 2 * 4),
                ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
            } catch (e: SecurityException) {
                Log.w(TAG, "no microphone", e)
                null
            }
        }

    private val effects =
        record
            ?.let { r ->
                listOfNotNull(
                    if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(r.audioSessionId) else null,
                    if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(r.audioSessionId) else null,
                ).onEach { it.enabled = true }
            }.orEmpty()

    private val recorder =
        record?.let { r ->
            thread(name = "microphone") {
                r.startRecording()
                while (running) {
                    val pcm = ShortArray(AUDIO_FRAME)
                    var got = 0
                    while (got < pcm.size && running) {
                        val n = r.read(pcm, got, pcm.size - got, AudioRecord.READ_BLOCKING)
                        if (n <= 0) break
                        got += n
                    }
                    if (got == pcm.size) recorded(pcm)
                }
                r.stop()
            }
        }

    private val player =
        thread(name = "speaker") {
            var track: AudioTrack? = null
            var rate = 0
            var channels = 0
            while (running) {
                val frame = playout.next()
                if (frame.rate != rate || frame.channels != channels) {
                    track?.release()
                    rate = frame.rate
                    channels = frame.channels
                    track = track(rate, channels)
                }
                track?.write(frame.pcm, 0, frame.pcm.size, AudioTrack.WRITE_BLOCKING)
                    ?: Thread.sleep(frame.pcm.size * 1000L / channels / rate)
            }
            track?.release()
        }

    private fun track(
        rate: Int,
        channels: Int,
    ): AudioTrack? {
        val mask = if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val min = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT)
        return try {
            AudioTrack
                .Builder()
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                ).setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setSampleRate(rate)
                        .setChannelMask(mask)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                ).setBufferSizeInBytes(maxOf(min, rate / 50 * channels * 2 * 3))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .also { it.play() }
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "no speaker for $rate Hz, $channels channels", e)
            null
        }
    }

    override fun setSpeaker(on: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker = audio.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (on && speaker != null) audio.setCommunicationDevice(speaker) else audio.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = on
        }
    }

    override fun close() {
        running = false
        recorder?.join()
        player.join()
        effects.forEach { it.release() }
        record?.release()
        setSpeaker(false)
        audio.mode = AudioManager.MODE_NORMAL
    }
}

/** A Camera2 camera, filming upright pictures in I420 for calls. */
internal class AndroidCamera private constructor(
    private val context: Context,
    private val front: Boolean,
    private val sensor: Int,
    private val frame: (VideoFrame) -> Unit,
) : AutoCloseable {
    private val thread = HandlerThread("camera").apply { start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { handler.post(it) }
    private var reader: ImageReader? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null

    @Volatile private var closed = false

    /** The camera thread's copies of the planes, used again for each picture. */
    private val planes = arrayOfNulls<ByteArray>(3)
    private val packed = arrayOfNulls<ByteArray>(3)
    private val gate = FrameGate()
    private val pool = FramePool()

    private fun start(
        manager: CameraManager,
        id: String,
        width: Int,
        height: Int,
        fps: Range<Int>?,
    ) {
        val reader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 3)
        this.reader = reader
        reader.setOnImageAvailableListener({ r -> r.acquireLatestImage()?.use { if (!closed && gate.open()) frame(convert(it)) } }, handler)
        try {
            manager.openCamera(
                id,
                executor,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        if (closed) {
                            device.close()
                            return
                        }
                        camera = device
                        capture(device, reader, fps)
                    }

                    override fun onDisconnected(device: CameraDevice) = device.close()

                    override fun onError(
                        device: CameraDevice,
                        error: Int,
                    ) {
                        Log.w(TAG, "camera error $error")
                        device.close()
                    }
                },
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "no camera", e)
        }
    }

    private fun capture(
        device: CameraDevice,
        reader: ImageReader,
        fps: Range<Int>?,
    ) {
        val request =
            device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(reader.surface)
                if (fps != null) set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps)
            }
        device.createCaptureSession(
            SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                listOf(OutputConfiguration(reader.surface)),
                executor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        if (closed) return
                        session = s
                        s.setRepeatingRequest(request.build(), null, handler)
                    }

                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        Log.w(TAG, "camera session failed")
                    }
                },
            ),
        )
    }

    /** How far to turn the camera's pictures clockwise to make them upright, as the phone is held now. */
    private fun rotation(): Int {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val screen = (display?.rotation ?: 0) * 90
        return if (front) (sensor + screen) % 360 else (sensor - screen + 360) % 360
    }

    /** The camera's picture, upright, in a frame from the pool. */
    private fun convert(image: Image): VideoFrame {
        val width = image.width and 1.inv()
        val height = image.height and 1.inv()
        val degrees = rotation()
        val turned = degrees == 90 || degrees == 270
        val out = pool.take(if (turned) height else width, if (turned) width else height)
        image.planes.forEachIndexed { i, plane ->
            val w = if (i == 0) width else width / 2
            val h = if (i == 0) height else height / 2
            val buffer = plane.buffer
            val row = plane.rowStride
            val data = planes[i]?.takeIf { it.size >= row * h } ?: ByteArray(row * h).also { planes[i] = it }
            buffer.get(data, 0, minOf(buffer.remaining(), data.size))
            val target =
                when (i) {
                    0 -> out.y
                    1 -> out.u
                    else -> out.v
                }
            if (plane.pixelStride == 1) {
                rotatePlane(data, w, h, row, degrees, target)
            } else {
                // Chroma samples of interleaved planes are every pixelStride bytes: pack them first.
                val step = plane.pixelStride
                val tight = packed[i]?.takeIf { it.size == w * h } ?: ByteArray(w * h).also { packed[i] = it }
                for (j in 0 until h) {
                    for (k in 0 until w) tight[j * w + k] = data[j * row + k * step]
                }
                rotatePlane(tight, w, h, w, degrees, target)
            }
        }
        return out
    }

    override fun close() {
        closed = true
        // Closing waits for the camera thread's last picture.
        handler.post {
            session?.close()
            camera?.close()
            reader?.close()
        }
        thread.quitSafely()
        thread.join()
    }

    companion object {
        /** The cameras facing the user and away: the front one, the back one, or both. */
        fun facings(context: Context): List<Int> {
            val manager = context.getSystemService(CameraManager::class.java)
            return manager.cameraIdList
                .mapNotNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) }
                .filter { it == CameraCharacteristics.LENS_FACING_FRONT || it == CameraCharacteristics.LENS_FACING_BACK }
                .distinct()
        }

        fun open(
            context: Context,
            front: Boolean,
            frame: (VideoFrame) -> Unit,
        ): AndroidCamera? {
            if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return null
            val manager = context.getSystemService(CameraManager::class.java)
            val wanted = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            val ids = manager.cameraIdList.toList()
            val id =
                ids.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == wanted }
                    ?: ids.firstOrNull()
                    ?: return null
            val characteristics = manager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            val sizes =
                characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(ImageFormat.YUV_420_888)
                    .orEmpty()
            // The size nearest to 640 × 480, 4:3 if the camera has one.
            val size =
                sizes.minByOrNull { s ->
                    abs(s.width * s.height - VIDEO_WIDTH * VIDEO_HEIGHT) +
                        if (s.width * 3 == s.height * 4) 0 else VIDEO_WIDTH * VIDEO_HEIGHT
                } ?: return null
            // Up to 30 a second, fewer where it's dark.
            val fps =
                characteristics
                    .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                    ?.filter { it.upper <= MAX_FPS }
                    ?.maxWithOrNull(compareBy<Range<Int>> { it.upper }.thenBy { -it.lower })
            val camera =
                AndroidCamera(
                    context,
                    facing == CameraCharacteristics.LENS_FACING_FRONT,
                    characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
                    frame,
                )
            camera.start(manager, id, size.width, size.height, fps)
            return camera
        }

        private const val MAX_FPS = 30
    }
}

/**
 * Bitmaps for one video picture on screen, three in turn: one shown, one maybe still on its way to the screen,
 * and the one being drawn.
 */
internal class AndroidVideoImages : VideoImages() {
    private val bitmaps = arrayOfNulls<Bitmap>(3)
    private var next = 0

    override fun draw(
        argb: IntArray,
        width: Int,
        height: Int,
    ): ImageBitmap {
        val bitmap =
            bitmaps[next]?.takeIf { it.width == width && it.height == height }
                ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmaps[next] = it }
        next = (next + 1) % bitmaps.size
        bitmap.setPixels(argb, 0, width, 0, 0, width, height)
        return bitmap.asImageBitmap()
    }
}
