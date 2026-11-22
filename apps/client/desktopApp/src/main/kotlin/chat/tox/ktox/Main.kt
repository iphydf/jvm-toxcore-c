package chat.tox.ktox

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import chat.tox.ktox.ui.App
import chat.tox.ktox.ui.Navigator
import org.jetbrains.skia.Image
import java.awt.Dimension
import java.io.File

/** Where the profiles are: --profile DIR, or ktox in the user's data directory. */
private fun profileDir(args: Array<String>): File {
    val i = args.indexOf("--profile")
    if (i >= 0 && i + 1 < args.size) return File(args[i + 1])
    val data = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotEmpty() } ?: "${System.getProperty("user.home")}/.local/share"
    return File(data, "ktox")
}

private fun icon(): ByteArray = Desktop::class.java.getResourceAsStream("/ktox.png")!!.use { it.readAllBytes() }

fun main(args: Array<String>) {
    val profiles = Profiles(profileDir(args), Desktop)
    profiles.start()
    application {
        val navigator = remember { Navigator() }
        Window(
            onCloseRequest = {
                profiles.close()
                exitApplication()
            },
            state = rememberWindowState(width = 1000.dp, height = 700.dp),
            title = "kTox",
            icon = remember { BitmapPainter(Image.makeFromEncoded(icon()).toComposeImageBitmap()) },
            onPreviewKeyEvent = { it.key == Key.Escape && it.type == KeyEventType.KeyDown && navigator.back() },
        ) {
            window.minimumSize = Dimension(360, 480)
            App(profiles, Desktop, navigator)
        }
    }
}
