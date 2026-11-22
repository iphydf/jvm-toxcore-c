package chat.tox.ktox

import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import java.io.InputStream

/** A file the user picked to send. */
class PickedFile(
    val name: String,
    val open: () -> InputStream,
)

/**
 * What Android and desktops do their own way. The notification calls come from the Tox thread of the profile
 * they're for.
 */
interface Platform {
    fun copy(text: String)

    fun notifyMessage(
        profile: ProfileId,
        friend: Friend,
        message: Message,
    ) {}

    fun notifyRequest(
        profile: ProfileId,
        request: FriendRequest,
    ) {}

    fun notifyInvite(
        profile: ProfileId,
        friend: Friend,
        invite: GroupInvite,
    ) {}

    /** A group message that mentions us or is for us alone. */
    fun notifyGroupMessage(
        profile: ProfileId,
        group: Group,
        message: GroupMessage,
    ) {}

    fun clearNotifications(
        profile: ProfileId,
        friend: FriendKey,
    ) {}

    fun clearGroupNotifications(
        profile: ProfileId,
        group: GroupId,
    ) {}

    /** [profile] was deleted: its notifications go too. */
    fun clearProfile(profile: ProfileId) {}

    /** Asks the user for a file, or for a picture if [images], and passes it on unless they cancel. */
    fun pickFile(
        images: Boolean,
        picked: (PickedFile) -> Unit,
    )

    /** Shows [file] in the app for its type. */
    fun open(
        file: File,
        name: String,
    )

    /** Copies [file] to the user's downloads as [name]. Returns where it went, for the user, or null if it couldn't. */
    fun saveToDownloads(
        file: File,
        name: String,
    ): String?

    /** [file] as an image at most [size] pixels wide and high, or null if it isn't one. Slow: not for the UI thread. */
    fun decodeImage(
        file: File,
        size: Int,
    ): ImageBitmap?

    /** [image] cut to a square of at most [size] pixels as a PNG, or null if it isn't an image. */
    fun avatarPng(
        image: ByteArray,
        size: Int,
    ): ByteArray?

    /** Something to make the images of one video picture on screen with. */
    fun videoImages(): VideoImages

    /** Notes how things go, for whoever reads the logs. */
    fun log(message: String) = println("kTox: $message")

    /**
     * Starts the microphone, giving [recorded] frames of [AUDIO_FRAME] samples, mono at [AUDIO_RATE], on a thread
     * of its own, and the speaker, playing what [playout] gives. Null if there's no sound here.
     */
    fun openSound(
        recorded: (ShortArray) -> Unit,
        playout: Playout,
    ): CallSound? = null

    /** How many cameras there are: phones have a front and a back one. */
    val cameras: Int get() = 0

    /** Starts the front or the back camera, giving [frame] upright pictures on a thread of its own. Null if it can't. */
    fun openCamera(
        front: Boolean,
        frame: (VideoFrame) -> Unit,
    ): AutoCloseable? = null

    /** Whether there's an earpiece as well as a loudspeaker. */
    val hasEarpiece: Boolean get() = false

    /** Asks for the microphone, and the camera if [video], then goes on: a call does without what the user refuses. */
    fun askForCall(
        video: Boolean,
        then: () -> Unit,
    ) = then()

    /**
     * [profile]'s call rang, started, changed or ended (null): phones show it outside the app and keep it going.
     * Only one profile has a call at a time.
     */
    fun callChanged(
        profile: ProfileId,
        call: Call?,
        friend: Friend?,
    ) {}

    /** Whether Tox can stay connected while the app is closed, so the setting means something. */
    val canStayConnected: Boolean get() = false

    /** Whether every profile stays connected while the app is closed. The platform keeps it. */
    var stayConnected: Boolean
        get() = false
        set(_) {}
}
