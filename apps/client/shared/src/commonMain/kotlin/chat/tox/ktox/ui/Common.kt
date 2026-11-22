package chat.tox.ktox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.tox.ktox.Friend
import chat.tox.ktox.Group
import chat.tox.ktox.Picture
import chat.tox.ktox.Platform
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import im.tox.tox4j.core.enums.ToxConnection
import im.tox.tox4j.core.enums.ToxUserStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

val LocalPlatform = staticCompositionLocalOf<Platform> { error("no Platform") }

/** Decoded pictures, so lists don't decode them again as they scroll. */
private object Images {
    private const val COUNT = 100

    private val cache =
        object : LinkedHashMap<String, ImageBitmap>(COUNT, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) = size > COUNT
        }

    @Synchronized
    operator fun get(key: String) = cache[key]

    @Synchronized
    operator fun set(
        key: String,
        image: ImageBitmap,
    ) {
        cache[key] = image
    }
}

/** [file] as a picture at most [size] pixels across, once it's decoded; [version] changes when the file does. */
@Composable
fun rememberImage(
    file: File,
    version: String,
    size: Int,
): ImageBitmap? {
    val platform = LocalPlatform.current
    val key = "${file.path} $version $size"
    return produceState(Images[key], key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { platform.decodeImage(file, size) }?.also { Images[key] = it }
        }
    }.value
}

/** The picture if there is one, else a circle in a colour of its own for each key with the name's first letter. */
@Composable
fun Avatar(
    key: String,
    name: String,
    size: Dp = 44.dp,
    connection: ToxConnection? = null,
    status: ToxUserStatus = ToxUserStatus.NONE,
    picture: Picture? = null,
) {
    val hue = (key.take(6).toIntOrNull(16) ?: 0) % 360
    val image = picture?.let { rememberImage(it.file, it.hash, AVATAR_PIXELS) }
    Box {
        if (image != null) {
            Image(image, null, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Box(
                Modifier.size(size).clip(CircleShape).background(Color.hsl(hue.toFloat(), 0.45f, 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * 0.42f).sp,
                )
            }
        }
        if (connection != null) {
            PresenceDot(
                connection,
                status,
                Modifier.align(Alignment.BottomEnd).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                size = size * 0.3f,
            )
        }
    }
}

@Composable
fun PresenceDot(
    connection: ToxConnection,
    status: ToxUserStatus,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    val color =
        when {
            connection == ToxConnection.NONE -> MaterialTheme.colorScheme.outline
            status == ToxUserStatus.AWAY -> Away
            status == ToxUserStatus.BUSY -> Busy
            else -> Online
        }
    Box(modifier.size(size).clip(CircleShape).background(color))
}

fun presence(
    connection: ToxConnection,
    status: ToxUserStatus,
): String =
    when {
        connection == ToxConnection.NONE -> "offline"
        status == ToxUserStatus.AWAY -> "away"
        status == ToxUserStatus.BUSY -> "busy"
        else -> "online"
    }

fun Friend.subtitle(): String =
    when {
        typing -> "typing…"
        statusMessage.isNotBlank() -> statusMessage
        else -> presence(connection, status)
    }

private val zone: ZoneId get() = ZoneId.systemDefault()

/** How a profile is connected to the Tox network, in a word or two. */
fun connectionLabel(connection: ToxConnection): String =
    when (connection) {
        ToxConnection.NONE -> "Connecting…"
        ToxConnection.TCP -> "Online (TCP)"
        ToxConnection.UDP -> "Online"
    }

fun day(time: Long): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

fun clock(time: Long): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(time).atZone(zone))

/** The time today, the day this week, the date before that. */
fun shortTime(time: Long): String {
    val date = day(time)
    val today = LocalDate.now(zone)
    return when {
        date == today -> clock(time)
        date.isAfter(today.minusDays(7)) -> DateTimeFormatter.ofPattern("EEE").format(date)
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).format(date)
    }
}

fun dayHeading(date: LocalDate): String {
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(date)
    }
}

/** A Tox ID in groups of 4, so people can read it out and compare it. */
fun groupedId(id: String): String = id.chunked(4).joinToString(" ")

/** A file size people read: 512 B, 1.4 MB. */
fun fileSize(bytes: Long): String {
    var value = bytes.toDouble()
    for (unit in listOf("B", "KB", "MB", "GB")) {
        if (value < KILO || unit == "GB") return if (unit == "B") "$bytes B" else "%.1f %s".format(value, unit)
        value /= KILO
    }
    error("unreachable")
}

private const val KILO = 1024.0

/** Avatars are decoded at this size, enough for the biggest place they're shown. */
private const val AVATAR_PIXELS = 256

/** [text] as a QR code, black on white with a quiet border, so phones can scan it from a screen. */
@Composable
fun QrCode(
    text: String,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(text) { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2)) }
    Canvas(modifier.background(Color.White)) {
        val cell = size.minDimension / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell, cell))
            }
        }
    }
}

/** A group's picture: its first letter on a rounded square, so groups and friends look apart. */
@Composable
fun GroupAvatar(
    group: Group,
    size: Dp = 44.dp,
) {
    val hue =
        (
            group.id.hex
                .take(6)
                .toIntOrNull(16) ?: 0
        ) % 360
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Color.hsl(hue.toFloat(), 0.4f, 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            group.displayName.firstOrNull()?.uppercase() ?: "#",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize =
                (
                    size.value *
                        0.42f
                ).sp,
        )
    }
}

fun Group.subtitle(): String =
    when {
        problem != null -> problem

        !connected -> "Connecting…"

        // Joined by chat ID and nobody found yet: the name comes from the others.
        name.isEmpty() && peers.size <= 1 -> "Looking for the group…"

        else -> "${peers.size} ${if (peers.size == 1) "person" else "people"}${if (topic.isNotBlank()) " · $topic" else ""}"
    }
