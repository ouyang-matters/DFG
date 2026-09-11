package io.github.ouyangmatters.rounds.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.ouyangmatters.rounds.R
import io.github.ouyangmatters.rounds.data.CheckWindow
import io.github.ouyangmatters.rounds.schedule.ItemStatus
import io.github.ouyangmatters.rounds.ui.theme.AppTheme
import io.github.ouyangmatters.rounds.util.Format
import java.util.Locale

/** Small letter-spaced caps label used as a section heading. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        text.uppercase(Locale.ENGLISH),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
    )
}

/** Rounded status chip, coloured by the semantic status palette. */
@Composable
fun StatusPill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Text(
            text.uppercase(Locale.ENGLISH),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/**
 * The signature element of the app: a single check window drawn as a track.
 *
 * The track spans exactly the window, elapsed time is filled in, a marker shows
 * where "now" sits, and each logged check inside the window is a notch. It makes
 * the core rule — at least one check between these two times — visible at a glance.
 */
@Composable
fun WindowTrack(
    window: CheckWindow?,
    now: Long,
    eventTimes: List<Long>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (window == null) return
    val track = MaterialTheme.colorScheme.outlineVariant
    val markerColor = MaterialTheme.colorScheme.onSurface
    val span = (window.endMillis - window.startMillis).coerceAtLeast(1L).toFloat()
    val progress = ((now - window.startMillis) / span).coerceIn(0f, 1f)
    val notches = eventTimes
        .filter { it in window }
        .map { ((it - window.startMillis) / span).coerceIn(0f, 1f) }

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val h = 8.dp.toPx()
            val y = (size.height - h) / 2f
            val radius = CornerRadius(h / 2f, h / 2f)

            drawRoundRect(
                color = track,
                topLeft = Offset(0f, y),
                size = Size(size.width, h),
                cornerRadius = radius,
            )
            if (progress > 0f) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(0f, y),
                    size = Size(size.width * progress, h),
                    cornerRadius = radius,
                )
            }
            // Each logged check inside the window.
            notches.forEach { fraction ->
                drawCircle(
                    color = markerColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(size.width * fraction, size.height / 2f),
                )
            }
            // "Now" marker, only while the window is actually running.
            if (progress > 0f && progress < 1f) {
                drawRoundRect(
                    color = markerColor,
                    topLeft = Offset(size.width * progress - 1.dp.toPx(), y - 3.dp.toPx()),
                    size = Size(2.dp.toPx(), h + 6.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val res = LocalContext.current.resources
            val (startLabel, endLabel) = Format.windowEndpoints(res, window)
            Text(
                startLabel,
                style = AppTheme.timeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                endLabel,
                style = AppTheme.timeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Left-hand accent rule used to colour-code cards by status. */
@Composable
fun AccentBar(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 4.dp, height = 40.dp)
            .padding(vertical = 2.dp),
    ) {
        Surface(color = color, shape = RoundedCornerShape(50)) {
            Box(Modifier.size(width = 4.dp, height = 36.dp))
        }
    }
}

/** Which visual state an item is in. */
enum class StatusKind { NO_SCHEDULE, DONE, OPEN, URGENT, UPCOMING, FINISHED }

data class StatusTone(
    val kind: StatusKind,
    /** Short form for the pill on the list. */
    val label: String,
    /** Sentence form for the detail header. */
    val headline: String,
    val container: Color,
    val content: Color,
    val accent: Color,
)

/**
 * A running window only turns red near its deadline. Colouring the whole window red
 * would cry wolf: a check with a 24-hour window is not urgent the moment it opens.
 */
private const val URGENT_FLOOR_MILLIS = 60L * 60 * 1000
private const val URGENT_FRACTION = 0.2

@Composable
fun statusTone(status: ItemStatus?, now: Long): StatusTone {
    val palette = AppTheme.status
    val outline = MaterialTheme.colorScheme.outline
    val res = LocalContext.current.resources
    val active = status?.activeWindow

    if (status == null || !status.hasEnabledRule) {
        return StatusTone(
            StatusKind.NO_SCHEDULE,
            stringResource(R.string.status_log_only),
            stringResource(R.string.headline_log_only),
            palette.idle,
            palette.onIdle,
            outline,
        )
    }
    if (active != null && status.activeSatisfied) {
        return StatusTone(
            StatusKind.DONE,
            stringResource(R.string.status_done),
            stringResource(R.string.headline_done),
            palette.done,
            palette.onDone,
            MaterialTheme.colorScheme.primary,
        )
    }
    if (active != null) {
        val remaining = active.endMillis - now
        val span = (active.endMillis - active.startMillis).coerceAtLeast(1L)
        val threshold = maxOf(URGENT_FLOOR_MILLIS, (span * URGENT_FRACTION).toLong())
        return if (remaining <= threshold) {
            val left = Format.countdown(res, now, active.endMillis)
            StatusTone(
                StatusKind.URGENT,
                stringResource(R.string.status_due_in, left),
                stringResource(R.string.headline_due, left),
                palette.overdue,
                palette.onOverdue,
                MaterialTheme.colorScheme.error,
            )
        } else {
            StatusTone(
                StatusKind.OPEN,
                stringResource(R.string.status_open),
                stringResource(R.string.headline_open, Format.time(active.endMillis)),
                palette.due,
                palette.onDue,
                MaterialTheme.colorScheme.tertiary,
            )
        }
    }
    val next = status.nextWindow
    return if (next != null) {
        StatusTone(
            StatusKind.UPCOMING,
            stringResource(R.string.status_upcoming),
            stringResource(R.string.headline_next, Format.window(res, next)),
            palette.idle,
            palette.onIdle,
            outline,
        )
    } else {
        StatusTone(
            StatusKind.FINISHED,
            stringResource(R.string.status_finished),
            stringResource(R.string.headline_none),
            palette.idle,
            palette.onIdle,
            outline,
        )
    }
}
