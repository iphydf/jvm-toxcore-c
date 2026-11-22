package chat.tox.ktox.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** A 24 dp Material icon from its SVG path, for those the core icon set lacks. */
internal fun materialIcon(
    name: String,
    path: String,
) = ImageVector
    .Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
    .build()

/** Material icons for messages, which the core icon set lacks (Apache 2.0, from Google's Material Icons). */
object MessageIcons {
    val Schedule =
        materialIcon(
            "Schedule",
            "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8" +
                "s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z",
        )

    val DoneAll =
        materialIcon(
            "DoneAll",
            "M18 7l-1.41-1.41-6.34 6.34 1.41 1.41L18 7zm4.24-1.41L11.66 16.17 7.48 12l-1.41 1.41L11.66 19l12-12-1.42-1.41z" +
                "M.41 13.41L6 19l1.41-1.41L1.83 12 .41 13.41z",
        )
}
