package chat.tox.ktox

import im.tox.tox4j.av.ToxAv
import im.tox.tox4j.av.ToxAvEvent
import im.tox.tox4j.av.data.ToxAvBitRate
import im.tox.tox4j.av.data.ToxAvChannels
import im.tox.tox4j.av.data.ToxAvHeight
import im.tox.tox4j.av.data.ToxAvSamplingRate
import im.tox.tox4j.av.data.ToxAvWidth
import im.tox.tox4j.av.enums.ToxAvCallControl
import im.tox.tox4j.av.enums.ToxAvFriendCallState
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.exceptions.ToxException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** The pictures of the call: the friend's newest, and ours as it goes out. */
class CallVideo internal constructor() {
    internal val friendFrames = MutableStateFlow<VideoFrame?>(null)
    internal val selfFrames = MutableStateFlow<VideoFrame?>(null)
    val friend: StateFlow<VideoFrame?> = friendFrames.asStateFlow()
    val self: StateFlow<VideoFrame?> = selfFrames.asStateFlow()
}

/**
 * Calls with friends over toxav, one at a time over all profiles: a profile takes [line] for its call. It belongs to
 * the Tox thread like the rest of [ToxClient]: the platform records and films on threads of its own and hands the
 * frames over with [post].
 */
internal class Calls(
    private val profile: ProfileId,
    private val line: CallLine,
    private val platform: Platform,
    /** Runs a block on the Tox thread. */
    private val post: (() -> Unit) -> Unit,
    private val number: (FriendKey) -> ToxFriendNumber?,
    private val key: (ToxFriendNumber) -> FriendKey?,
    private val friend: (FriendKey) -> Friend?,
    /** The call changed: the client shows it. */
    private val changed: () -> Unit,
    /** A call ended: the client notes it in the chat. */
    private val ended: (call: Call, note: CallNote) -> Unit,
) {
    private var av: ToxAv? = null

    var call: Call? = null
        private set

    val video = CallVideo()

    /** Counts calls, so frames recorded for one that ended don't go into the next. */
    private var session = 0L
    private var sound: CallSound? = null
    private var camera: AutoCloseable? = null
    private val playout = Playout()

    /** Frames recorded and not sent yet: when the Tox thread falls behind, newer ones are dropped. */
    private val queuedSound = AtomicInteger()

    /** The newest picture to send and when it was filmed, while the Tox thread hasn't sent it: older ones are dropped. */
    private val nextPicture = AtomicReference<Pair<VideoFrame, Long>?>()

    /** The video bit rate now, which follows what toxav says the network takes, and since when. */
    private var videoBitRate = START_VIDEO_BIT_RATE
    private var videoBitRateSince = 0L

    private val stats = CallStats()
    private var lastLog = 0L

    val iterationInterval: Int get() = av?.iterationInterval ?: Int.MAX_VALUE

    fun start(av: ToxAv) {
        this.av = av
    }

    /** Before Tox stops: the call can't go on without it. */
    fun stop() {
        hangUp()
        av?.close()
        av = null
    }

    fun iterate() {
        av?.iterate()?.forEach(::on)
        val call = call ?: return
        val now = now()
        if (call.outgoing && call.ringing && now - ringingSince > RING_TIMEOUT_MS) {
            cancel(call.friend)
            end(CallOutcome.MISSED)
            return
        }
        if (call.answered && call.video && videoBitRate < MAX_VIDEO_BIT_RATE && now - videoBitRateSince >= RAISE_EVERY_MS) {
            // Back up slowly once the network has kept up for a while.
            setVideoBitRate(minOf(MAX_VIDEO_BIT_RATE, videoBitRate * 11 / 10))
        }
        if (call.answered && now - lastLog >= LOG_EVERY_MS) {
            if (lastLog != 0L) platform.log(stats.take(videoBitRate))
            lastLog = now
        }
    }

    private var ringingSince = 0L

    fun call(
        friend: FriendKey,
        video: Boolean,
    ) {
        val current = call
        if (current != null) {
            // Calling back someone who is calling us answers them.
            if (current.friend == friend && !current.outgoing && current.ringing) answer(video)
            return
        }
        val number = number(friend) ?: return
        val av = av ?: return
        if (!line.take(profile)) return
        try {
            av.call(number, AUDIO_BIT_RATE, if (video) ToxAvBitRate(START_VIDEO_BIT_RATE) else NO_BIT_RATE)
        } catch (_: ToxException) {
            line.release(profile)
            return
        }
        begin(Call(friend, outgoing = true, videoCall = video, video = video, speaker = video))
        playout.setTone(Tone.RINGBACK)
        startSound()
        if (video) startCamera()
    }

    fun answer(video: Boolean) {
        val current = call?.takeIf { !it.outgoing && it.ringing } ?: return
        val number = number(current.friend) ?: return
        try {
            av?.answer(number, AUDIO_BIT_RATE, if (video) ToxAvBitRate(START_VIDEO_BIT_RATE) else NO_BIT_RATE)
        } catch (_: ToxException) {
            end(CallOutcome.MISSED)
            return
        }
        update(current.copy(answered = true, started = now(), video = video, speaker = video))
        startSound()
        if (video) startCamera()
    }

    /** Ends the call, or says no to one that rings. */
    fun hangUp() {
        val current = call ?: return
        cancel(current.friend)
        end(
            when {
                current.answered -> CallOutcome.ANSWERED
                current.outgoing -> CallOutcome.MISSED
                else -> CallOutcome.DECLINED
            },
        )
    }

    fun setMuted(muted: Boolean) {
        val current = call ?: return
        update(current.copy(muted = muted))
        if (current.answered) setBitRates()
    }

    fun setVideo(on: Boolean) {
        val current = call ?: return
        if (current.video == on) return
        update(current.copy(video = on, problem = null))
        if (on) {
            startCamera()
        } else {
            stopCamera()
        }
        if (current.answered) setBitRates()
    }

    fun switchCamera() {
        val current = call ?: return
        update(current.copy(frontCamera = !current.frontCamera))
        if (camera != null) {
            stopCamera()
            startCamera()
        }
    }

    fun setSpeaker(on: Boolean) {
        val current = call ?: return
        sound?.setSpeaker(on)
        update(current.copy(speaker = on))
    }

    /** The friend went offline: toxav may not say so for a while. */
    fun friendGone(friend: FriendKey) {
        val current = call?.takeIf { it.friend == friend } ?: return
        end(if (current.answered) CallOutcome.ANSWERED else CallOutcome.MISSED)
    }

    private fun begin(call: Call) {
        session++
        ringingSince = now()
        videoBitRate = START_VIDEO_BIT_RATE
        videoBitRateSince = ringingSince
        lastLog = 0
        stats.take(videoBitRate)
        platform.log("call ${if (call.outgoing) "to" else "from"} ${call.friend.hex.take(SHORT_KEY)}, video ${call.videoCall}")
        update(call)
    }

    private fun update(call: Call) {
        this.call = call
        platform.callChanged(profile, call, friend(call.friend))
        changed()
    }

    /** Tells toxav what we send now: none of what's muted or off. */
    private fun setBitRates() {
        val current = call ?: return
        val number = number(current.friend) ?: return
        val av = av ?: return
        try {
            av.audioSetBitRate(number, if (current.muted) NO_BIT_RATE else AUDIO_BIT_RATE)
            av.videoSetBitRate(number, ToxAvBitRate(if (current.video) videoBitRate else 0))
        } catch (_: ToxException) {
            // The call is ending: toxav says so next.
        }
    }

    private fun setVideoBitRate(rate: Int) {
        videoBitRate = rate
        videoBitRateSince = now()
        val current = call?.takeIf { it.answered && it.video } ?: return
        val number = number(current.friend) ?: return
        try {
            av?.videoSetBitRate(number, ToxAvBitRate(rate))
        } catch (_: ToxException) {
            // The call is ending: toxav says so next.
        }
    }

    private fun cancel(friend: FriendKey) {
        val number = number(friend) ?: return
        try {
            av?.callControl(number, ToxAvCallControl.CANCEL)
        } catch (_: ToxException) {
            // Gone already.
        }
    }

    private fun end(outcome: CallOutcome) {
        val current = call ?: return
        call = null
        line.release(profile)
        session++
        sound?.close()
        sound = null
        stopCamera()
        playout.setTone(null)
        video.friendFrames.value = null
        val seconds = if (current.answered) (now() - current.started) / 1000 else 0
        if (current.answered) platform.log(stats.take(videoBitRate))
        platform.log("call ended: $outcome after $seconds s")
        ended(current, CallNote(current.videoCall, outcome, seconds))
        platform.callChanged(profile, null, null)
        changed()
    }

    private fun startSound() {
        val current = session
        sound = platform.openSound({ recorded(current, it) }, playout)
        val call = call ?: return
        if (sound == null) {
            update(call.copy(problem = "There's no microphone or speaker the app can use."))
        } else {
            sound?.setSpeaker(call.speaker)
        }
    }

    private fun startCamera() {
        val call = call ?: return
        val current = session
        camera = platform.openCamera(call.frontCamera) { filmed(current, it, call.frontCamera) }
        if (camera == null) {
            update(call.copy(video = false, problem = "There's no camera the app can use."))
            if (call.answered) setBitRates()
        }
    }

    private fun stopCamera() {
        camera?.close()
        camera = null
        nextPicture.set(null)
        video.selfFrames.value = null
    }

    /** On the microphone's thread. */
    private fun recorded(
        session: Long,
        pcm: ShortArray,
    ) {
        if (queuedSound.get() >= MAX_QUEUED_SOUND) {
            stats.soundDropped.incrementAndGet()
            return
        }
        queuedSound.incrementAndGet()
        val recordedAt = System.nanoTime()
        post {
            queuedSound.decrementAndGet()
            val call = call?.takeIf { session == this.session && it.answered && !it.muted } ?: return@post
            val number = number(call.friend) ?: return@post
            stats.soundWaited(System.nanoTime() - recordedAt)
            try {
                av?.audioSendFrame(number, pcm, pcm.size.toLong(), MONO, ToxAvSamplingRate(AUDIO_RATE))
            } catch (_: ToxException) {
                // The friend doesn't take sound now.
            }
        }
    }

    /** On the camera's thread. */
    private fun filmed(
        session: Long,
        frame: VideoFrame,
        front: Boolean,
    ) {
        if (session != this.session) return
        video.selfFrames.value = if (front) frame.mirrored() else frame
        stats.filmed.incrementAndGet()
        if (nextPicture.getAndSet(frame to System.nanoTime()) == null) post { sendPicture(session) }
    }

    private fun sendPicture(session: Long) {
        val (frame, filmedAt) = nextPicture.getAndSet(null) ?: return
        val call = call?.takeIf { session == this.session && it.answered && it.video } ?: return
        val number = number(call.friend) ?: return
        val start = System.nanoTime()
        try {
            av?.videoSendFrame(number, ToxAvWidth(frame.width), ToxAvHeight(frame.height), frame.y, frame.u, frame.v)
        } catch (_: ToxException) {
            // The friend doesn't take video now.
        }
        stats.sent(start - filmedAt, System.nanoTime() - start)
    }

    private fun on(event: ToxAvEvent) {
        when (event) {
            is ToxAvEvent.Call -> event.handle()
            is ToxAvEvent.CallState -> event.handle()
            is ToxAvEvent.VideoBitRate -> event.handle()
            is ToxAvEvent.AudioReceiveFrame -> event.handle()
            is ToxAvEvent.VideoReceiveFrame -> event.handle()
            is ToxAvEvent.AudioBitRate -> Unit
        }
    }

    private fun ToxAvEvent.Call.handle() {
        val friend = key(friendNumber) ?: return
        if (call != null || !line.take(profile)) {
            // One call at a time, here or in another profile: they hear that it wasn't answered.
            cancel(friend)
            ended(Call(friend, outgoing = false, videoCall = videoEnabled), CallNote(videoEnabled, CallOutcome.MISSED, 0))
            return
        }
        begin(Call(friend, outgoing = false, videoCall = videoEnabled, friendAudio = audioEnabled, friendVideo = videoEnabled))
    }

    private fun ToxAvEvent.CallState.handle() {
        val current = call?.takeIf { it.friend == key(friendNumber) } ?: return
        if (state and (ToxAvFriendCallState.FINISHED or ToxAvFriendCallState.ERROR) != 0) {
            end(
                when {
                    current.answered -> CallOutcome.ANSWERED
                    current.outgoing && state and ToxAvFriendCallState.FINISHED != 0 -> CallOutcome.DECLINED
                    else -> CallOutcome.MISSED
                },
            )
            return
        }
        val sending =
            current.copy(
                friendAudio = state and ToxAvFriendCallState.SENDING_A != 0,
                friendVideo = state and ToxAvFriendCallState.SENDING_V != 0,
            )
        if (!sending.friendVideo) video.friendFrames.value = null
        if (current.answered) {
            update(sending)
            return
        }
        // They answered ours.
        playout.setTone(null)
        update(sending.copy(answered = true, started = now()))
        setBitRates()
    }

    /** The network can't keep up: toxav asks for less. */
    private fun ToxAvEvent.VideoBitRate.handle() {
        if (call?.friend != key(friendNumber)) return
        platform.log("toxav asks for ${videoBitRate.value} kbit/s of video")
        setVideoBitRate(videoBitRate.value.coerceIn(MIN_VIDEO_BIT_RATE, MAX_VIDEO_BIT_RATE))
    }

    private fun ToxAvEvent.AudioReceiveFrame.handle() {
        if (call?.friend != key(friendNumber)) return
        stats.soundReceived++
        playout.add(SoundFrame(pcm, channels.value.toInt(), samplingRate.value))
    }

    private fun ToxAvEvent.VideoReceiveFrame.handle() {
        if (call?.friend != key(friendNumber)) return
        stats.picturesReceived++
        // Each plane comes in arrays of its own, so the UI can keep the frame.
        video.friendFrames.value = VideoFrame(width.value, height.value, y, u, v, abs(ystride), abs(ustride), abs(vstride))
    }

    private companion object {
        /** In kbit/s: Opus speech is clear at this; VP8 starts at 1000 and follows toxav between the others. */
        val AUDIO_BIT_RATE = ToxAvBitRate(48)
        const val START_VIDEO_BIT_RATE = 1000
        const val MIN_VIDEO_BIT_RATE = 150
        const val MAX_VIDEO_BIT_RATE = 1500
        val NO_BIT_RATE = ToxAvBitRate(0)

        /** The video bit rate goes up a tenth each time it has gone this long without toxav asking for less. */
        const val RAISE_EVERY_MS = 5_000L
        const val LOG_EVERY_MS = 5_000L
        val MONO = ToxAvChannels(1)

        /** An unanswered call stops ringing after a minute. */
        const val RING_TIMEOUT_MS = 60_000L

        /** 200 ms of sound. */
        const val MAX_QUEUED_SOUND = 10

        fun now() = System.currentTimeMillis()
    }
}

/**
 * How a call goes, for the log: what went out and came in, how long encoding took, and how long the Tox thread
 * kept frames waiting. The counters with atomics are counted on the platform's threads, the rest on the Tox thread.
 */
private class CallStats {
    val filmed = AtomicInteger()
    val soundDropped = AtomicInteger()
    var soundReceived = 0
    var picturesReceived = 0
    private var picturesSent = 0
    private var soundSent = 0
    private var encoding = 0L
    private var longestEncoding = 0L
    private var longestPictureWait = 0L
    private var longestSoundWait = 0L
    private var since = System.nanoTime()

    fun sent(
        waited: Long,
        encoded: Long,
    ) {
        picturesSent++
        encoding += encoded
        longestEncoding = maxOf(longestEncoding, encoded)
        longestPictureWait = maxOf(longestPictureWait, waited)
    }

    fun soundWaited(waited: Long) {
        soundSent++
        longestSoundWait = maxOf(longestSoundWait, waited)
    }

    /** The line for the time since the last one, and starts counting again. */
    fun take(videoBitRate: Int): String {
        val seconds = (System.nanoTime() - since) / 1e9
        val line =
            "call %.0f s: sent %d of %d pictures (%d kbit/s), encoding %.0f ms each, at most %.0f, waited at most %.0f ms; ".format(
                seconds,
                picturesSent,
                filmed.get(),
                videoBitRate,
                if (picturesSent == 0) 0.0 else encoding / 1e6 / picturesSent,
                longestEncoding / 1e6,
                longestPictureWait / 1e6,
            ) +
                "sent %d sound frames, dropped %d, waited at most %.0f ms; got %d pictures, %d sound frames".format(
                    soundSent,
                    soundDropped.get(),
                    longestSoundWait / 1e6,
                    picturesReceived,
                    soundReceived,
                )
        filmed.set(0)
        soundDropped.set(0)
        soundReceived = 0
        picturesReceived = 0
        picturesSent = 0
        soundSent = 0
        encoding = 0
        longestEncoding = 0
        longestPictureWait = 0
        longestSoundWait = 0
        since = System.nanoTime()
        return line
    }
}
