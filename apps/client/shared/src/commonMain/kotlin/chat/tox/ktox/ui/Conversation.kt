package chat.tox.ktox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.ceil

/** A chat's list as it's drawn: day headings between runs of bubbles. */
private sealed interface ChatItem<out M> {
    val key: String

    data class Day(
        val date: LocalDate,
    ) : ChatItem<Nothing> {
        override val key get() = "day $date"
    }

    data class Bubble<M>(
        val message: M,
        override val key: String,
        /** Whether it starts a run from one sender, where groups show who it is. */
        val first: Boolean,
        /** Whether it ends the run, which says when it was sent. */
        val last: Boolean,
    ) : ChatItem<M>
}

/** What a chat list needs to know about its messages. */
internal class Messages<M>(
    val all: List<M>,
    val key: (M) -> String,
    val time: (M) -> Long,
    /** Who sent it: messages from the same sender close together form a run. */
    val sender: (M) -> String,
)

/** Newest first, for a list that starts at the bottom. */
private fun <M> Messages<M>.items(): List<ChatItem<M>> {
    val items = mutableListOf<ChatItem<M>>()
    all.forEachIndexed { i, message ->
        val date = day(time(message))
        val previous = all.getOrNull(i - 1)
        val next = all.getOrNull(i + 1)
        if (previous == null || day(time(previous)) != date) items += ChatItem.Day(date)
        val first = previous == null || !inRun(previous, message)
        val last = next == null || !inRun(message, next)
        items += ChatItem.Bubble(message, "message ${key(message)}", first, last)
    }
    return items.asReversed()
}

private fun <M> Messages<M>.inRun(
    above: M,
    below: M,
) = sender(above) == sender(below) && day(time(above)) == day(time(below)) && time(below) - time(above) < RUN_GAP_MILLIS

/** A chat's messages from the bottom up, loading older ones as it scrolls to the top. */
@Composable
internal fun <M> MessageList(
    messages: Messages<M>?,
    hasOlder: Boolean,
    loadOlder: () -> Unit,
    empty: String,
    modifier: Modifier = Modifier,
    bubble: @Composable (message: M, first: Boolean, last: Boolean) -> Unit,
) {
    val items = remember(messages?.all) { messages?.items().orEmpty() }
    val list = rememberLazyListState()
    // New messages keep the view at the bottom if it was there.
    LaunchedEffect(items.firstOrNull()?.key) { if (list.firstVisibleItemIndex <= 1) list.animateScrollToItem(0) }
    LaunchedEffect(hasOlder, items.size) {
        snapshotFlow {
            list.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: 0
        }.collect { if (hasOlder && it >= items.size - OLDER_MARGIN) loadOlder() }
    }
    Box(modifier) {
        if (messages != null && items.isEmpty()) {
            Text(empty, Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SelectionContainer {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(items, key = { it.key }) { item ->
                    when (item) {
                        is ChatItem.Day -> DayHeading(item.date)
                        is ChatItem.Bubble -> bubble(item.message, item.first, item.last)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeading(date: LocalDate) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                dayHeading(date),
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A bubble on its side of the chat. Its corners on that side are small where it joins the bubble above
 * or below in its run. The content gets the text colour, and whether to say when it was sent: the
 * last of a run does, as does one tapped.
 */
@Composable
internal fun BubbleFrame(
    outgoing: Boolean,
    first: Boolean,
    last: Boolean,
    tight: Boolean = false,
    content: @Composable ColumnScope.(foreground: Color, saysWhen: Boolean) -> Unit,
) {
    var tapped by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val above = if (first) BUBBLE_CORNER else JOINED_CORNER
    val below = if (last) BUBBLE_CORNER else JOINED_CORNER
    val shape =
        if (outgoing) {
            RoundedCornerShape(BUBBLE_CORNER, above, below, BUBBLE_CORNER)
        } else {
            RoundedCornerShape(above, BUBBLE_CORNER, BUBBLE_CORNER, below)
        }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = if (first) RUN_SPACING else 0.dp)
            .padding(start = if (outgoing) OTHER_SIDE else 0.dp, end = if (outgoing) 0.dp else OTHER_SIDE),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = BUBBLE_MAX_WIDTH)
                .clip(shape)
                .background(if (outgoing) colors.primaryContainer else colors.surfaceContainerHighest)
                .clickable(interactionSource = null, indication = null) { tapped = !tapped }
                .padding(if (tight) PaddingValues(6.dp) else PaddingValues(horizontal = 12.dp, vertical = 6.dp)),
        ) { content(if (outgoing) colors.onPrimaryContainer else colors.onSurface, last || tapped) }
    }
}

/** How far one of our messages got. */
internal enum class Delivery(
    val icon: ImageVector,
    val description: String,
) {
    /** Not confirmed yet: the friend may be offline, or its receipt not back. */
    PENDING(MessageIcons.Schedule, "Not delivered yet"),

    /** Taken by toxcore, where nothing confirms more: groups have no receipts. */
    SENT(Icons.Default.Check, "Sent"),

    /** The friend's client confirmed it. */
    DELIVERED(MessageIcons.DoneAll, "Delivered"),
}

/**
 * What a bubble says at its end: when it was sent where it says that, and how far one of ours got.
 * Ours on its way shows that even where it doesn't say when. Null where there's nothing to show.
 */
internal fun stamp(
    time: Long,
    foreground: Color,
    saysWhen: Boolean,
    delivery: Delivery? = null,
): (@Composable () -> Unit)? =
    if (saysWhen || delivery == Delivery.PENDING) {
        { Stamp(time.takeIf { saysWhen }, foreground, delivery) }
    } else {
        null
    }

@Composable
private fun Stamp(
    time: Long?,
    foreground: Color,
    delivery: Delivery?,
) {
    val color = foreground.copy(alpha = 0.7f)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (time != null) Text(clock(time), style = MaterialTheme.typography.labelSmall, color = color)
        if (delivery != null) Icon(delivery.icon, delivery.description, Modifier.size(14.dp), tint = color)
    }
}

/** A [stamp] in the bubble's bottom corner, under what came before it. */
@Composable
internal fun ColumnScope.StampUnder(
    stamp: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (stamp != null) Box(modifier.align(Alignment.End)) { stamp() }
}

/** A message's text, with its [stamp] at the end of the last line where there's room, else under it. */
@Composable
internal fun StampedText(
    text: String,
    foreground: Color,
    stamp: (@Composable () -> Unit)?,
    fontStyle: FontStyle = FontStyle.Normal,
) {
    val style = MaterialTheme.typography.bodyLarge
    if (stamp == null) {
        Text(text, color = foreground, fontStyle = fontStyle, style = style)
        return
    }
    // Set while the text is measured, which happens before the stamp is placed.
    val lines = remember { LastTextLayout() }
    Layout({
        Text(text, color = foreground, fontStyle = fontStyle, style = style, onTextLayout = { lines.result = it })
        stamp()
    }) { measurables, constraints ->
        val words = measurables[0].measure(constraints.copy(minWidth = 0))
        val mark = measurables[1].measure(Constraints())
        // Where the stamp ends if it goes on the last line, if there's room there.
        val besideEnd =
            lines.result
                ?.lastLineEnd()
                ?.let { it + STAMP_GAP.roundToPx() + mark.width }
                ?.takeIf { it <= constraints.maxWidth }
        val width = maxOf(words.width, besideEnd ?: mark.width, constraints.minWidth)
        val height = words.height + if (besideEnd != null) 0 else mark.height
        layout(width, height) {
            words.place(0, 0)
            mark.place(width - mark.width, height - mark.height)
        }
    }
}

private class LastTextLayout {
    var result: TextLayoutResult? = null
}

/**
 * Where the last line ends on the right, or null where it ends on the left: text that runs right to
 * left leaves no room on the right of its last line.
 */
private fun TextLayoutResult.lastLineEnd(): Int? {
    val line = lineCount - 1
    val direction = getParagraphDirection(maxOf(0, getLineEnd(line) - 1))
    return if (direction == ResolvedTextDirection.Ltr) ceil(getLineRight(line)).toInt() else null
}

/**
 * Where messages are written. Enter sends, and "/me " at the start makes it an action. [draft] keeps
 * the text per chat; [onTyping] and [onAttach] are for chats that have them.
 */
@Composable
internal fun MessageField(
    draft: String,
    onSend: (text: String, action: Boolean) -> Unit,
    onTyping: ((Boolean) -> Unit)? = null,
    onAttach: (() -> Unit)? = null,
) {
    var text by rememberSaveable(draft) { mutableStateOf("") }
    var typing by remember(draft) { mutableStateOf(false) }

    fun setTyping(now: Boolean) {
        if (onTyping != null && now != typing) {
            typing = now
            onTyping(now)
        }
    }

    fun send() {
        if (text.isBlank()) return
        val action = text.startsWith("/me ")
        onSend(if (action) text.removePrefix("/me ") else text, action)
        text = ""
        setTyping(false)
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
        if (onAttach != null) {
            IconButton(onClick = onAttach, modifier = Modifier.size(52.dp)) { Icon(Icons.Default.Add, "Send a file") }
        }
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                setTyping(it.isNotEmpty())
            },
            modifier =
                Modifier.weight(1f).onPreviewKeyEvent {
                    // Enter sends and Shift+Enter starts a new line, on keyboards that have them.
                    if (it.key == Key.Enter && !it.isShiftPressed) {
                        if (it.type == KeyEventType.KeyDown) send()
                        true
                    } else {
                        false
                    }
                },
            placeholder = { Text("Message") },
            shape = RoundedCornerShape(24.dp),
            maxLines = 6,
            colors =
                OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = ::send, enabled = text.isNotBlank(), modifier = Modifier.size(52.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send")
        }
    }
}

/** Loads older messages when the list scrolls this close to the top. */
private const val OLDER_MARGIN = 10

/** Messages from one sender this close together form a run. */
private const val RUN_GAP_MILLIS = 3 * 60 * 1000L

/** A bubble's corners, and those where it joins the next in its run. */
private val BUBBLE_CORNER = 20.dp
private val JOINED_CORNER = 4.dp

/** Above each run, beyond the gap between bubbles. */
private val RUN_SPACING = 6.dp

/** Kept free beside a bubble, on the other side, so it's clear whose it is. */
private val OTHER_SIDE = 48.dp

/** The widest a bubble gets, in wide desktop windows. */
private val BUBBLE_MAX_WIDTH = 560.dp

/** Between a message's text and the stamp on its last line. */
private val STAMP_GAP = 8.dp
