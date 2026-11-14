package io.github.devjonny.moa

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.PagerScaffoldDefaults
import androidx.wear.compose.material3.Text
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val CLICK_VALUES = listOf(0.125 to "⅛", 0.25 to "¼", 0.5 to "½", 1.0 to "1")
private const val DISTANCE_STEP = 5
private const val MAX_DISTANCE = 1500
private const val MAX_CLICKS = 200
private const val INCH_STEP = 0.25
private const val CM_STEP = 0.5
private const val MAX_OFFSET = 100.0

// Rotary pixels per step. The Pixel Watch crown is smooth, so this sets how far a turn moves a value.
private const val ROTARY_PIXELS_PER_STEP = 60f

// Matches the web app's accent so the crown-controlled value stands out.
private val colorScheme = ColorScheme(
    primary = Color(0xFFF2A541),
    primaryDim = Color(0xFFC98A36),
    onPrimary = Color(0xFF1A1206),
)

private enum class Field { DISTANCE, ADJUST }

// Same two directions as the web app's Adjust section.
private enum class AdjustMode { CLICKS_TO_OFFSET, OFFSET_TO_CLICKS }

// The watch has no setup screen, so holdover uses the web app's defaults for a sub-12 ft-lb .177 Gamo Phox
// with an 8.4 gr pellet, 1.5" sight height, 25 yd zero and a 3-9x SFP scope shot at 9x where the reticle reads true.
private val RIFLE = Ballistics.Setup(
    muzzleVelocityMps = 790 * Ballistics.FPS_TO_MPS,
    bc = 0.021,
    sightHeightM = 1.5 * Ballistics.INCH_TO_M,
    zeroM = 25 * Ballistics.YARD_TO_M,
)
private const val HOLD_MIN_DISTANCE = 5
private const val HOLD_MAX_DISTANCE = 100

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("moa", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = colorScheme) {
                AppScaffold { WatchApp(prefs) }
            }
        }
    }
}

@Composable
private fun WatchApp(prefs: SharedPreferences) {
    var metric by remember { mutableStateOf(prefs.getBoolean("metric", false)) }
    LaunchedEffect(metric) { prefs.edit().putBoolean("metric", metric).apply() }

    val pagerState = rememberPagerState(pageCount = { 2 })
    HorizontalPagerScaffold(
        pagerState = pagerState,
        pageIndicatorAnimationSpec = PagerScaffoldDefaults.FadeOutAnimationSpec,
    ) {
        HorizontalPager(state = pagerState) { page ->
            val isCurrent = pagerState.currentPage == page
            when (page) {
                0 -> MoaScreen(prefs, metric, onMetricChange = { metric = it }, isCurrent = isCurrent)
                else -> HoldoverScreen(prefs, metric, isCurrent = isCurrent)
            }
        }
    }
}

// Turns crown rotation into whole steps. Only the visible page takes focus, so the crown drives that page.
@Composable
private fun Modifier.crownSteps(isCurrent: Boolean, onSteps: (Int) -> Unit): Modifier {
    val focusRequester = remember { FocusRequester() }
    var remainder by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isCurrent) { if (isCurrent) focusRequester.requestFocus() }
    return onRotaryScrollEvent { event ->
        remainder += event.verticalScrollPixels
        val steps = (remainder / ROTARY_PIXELS_PER_STEP).toInt()
        if (steps != 0) {
            remainder -= steps * ROTARY_PIXELS_PER_STEP
            onSteps(steps)
        }
        true
    }
        .focusRequester(focusRequester)
        .focusable()
}

@Composable
private fun MoaScreen(prefs: SharedPreferences, metric: Boolean, onMetricChange: (Boolean) -> Unit, isCurrent: Boolean) {
    var clickIndex by remember { mutableIntStateOf(prefs.getInt("click", 1)) }
    var distance by remember { mutableIntStateOf(prefs.getInt("distance", 25)) }
    var clicks by remember { mutableIntStateOf(prefs.getInt("clicks", 4)) }
    var offset by remember { mutableFloatStateOf(prefs.getFloat("offset", 1f)) }
    var mode by remember {
        mutableStateOf(AdjustMode.valueOf(prefs.getString("mode", null) ?: AdjustMode.CLICKS_TO_OFFSET.name))
    }
    var active by remember { mutableStateOf(Field.DISTANCE) }

    LaunchedEffect(clickIndex, distance, clicks, offset, mode) {
        prefs.edit()
            .putInt("click", clickIndex)
            .putInt("distance", distance)
            .putInt("clicks", clicks)
            .putFloat("offset", offset)
            .putString("mode", mode.name)
            .apply()
    }

    val (clickMoa, clickLabel) = CLICK_VALUES[clickIndex]
    val lengthUnit = if (metric) "cm" else "in"
    val yards = Moa.toYards(distance.toDouble(), metric)
    val perClick = Moa.clickInInches(clickMoa, yards)
    val adjustText = when (mode) {
        AdjustMode.CLICKS_TO_OFFSET ->
            "${plural(clicks, "click")} = ${length(Moa.offsetForClicks(clicks, clickMoa, yards), metric)}"
        AdjustMode.OFFSET_TO_CLICKS -> {
            val offsetInches = if (metric) offset * 10 / Moa.MM_PER_INCH else offset.toDouble()
            val needed = Moa.clicksForOffset(offsetInches, clickMoa, yards).roundToInt()
            val shown = if (metric) String.format(Locale.UK, "%.1f cm", offset) else String.format(Locale.UK, "%.2f″", offset)
            "$shown = ${plural(needed, "click")}"
        }
    }
    val colors = MaterialTheme.colorScheme
    fun highlight(field: Field) = if (active == field) colors.primary else colors.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .crownSteps(isCurrent) { steps ->
                when (active) {
                    Field.DISTANCE -> distance = (distance + steps * DISTANCE_STEP).coerceIn(DISTANCE_STEP, MAX_DISTANCE)
                    Field.ADJUST -> when (mode) {
                        AdjustMode.CLICKS_TO_OFFSET -> clicks = (clicks + steps).coerceIn(0, MAX_CLICKS)
                        AdjustMode.OFFSET_TO_CLICKS -> {
                            val step = if (metric) CM_STEP else INCH_STEP
                            // Snap to the step so repeated float adds don't drift.
                            val next = (Math.round(offset / step) + steps) * step
                            offset = next.coerceIn(0.0, MAX_OFFSET).toFloat()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Nudged down to clear the time text at the top of the round screen.
        Column(
            modifier = Modifier.padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactButton(onClick = { clickIndex = (clickIndex + 1) % CLICK_VALUES.size }) {
                    Text("$clickLabel MOA")
                }
                CompactButton(onClick = { onMetricChange(!metric) }) {
                    Text(if (metric) "m" else "yd")
                }
            }
            Text(
                text = "$distance ${if (metric) "m" else "yd"}",
                style = MaterialTheme.typography.numeralSmall,
                color = highlight(Field.DISTANCE),
                modifier = Modifier.clickable { active = Field.DISTANCE },
            )
            Text(
                text = "${length(perClick, metric)} / click",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Text(
                text = adjustText,
                style = MaterialTheme.typography.titleMedium,
                color = highlight(Field.ADJUST),
                modifier = Modifier.clickable { active = Field.ADJUST },
            )
            CompactButton(
                onClick = {
                    mode = if (mode == AdjustMode.CLICKS_TO_OFFSET) AdjustMode.OFFSET_TO_CLICKS else AdjustMode.CLICKS_TO_OFFSET
                    active = Field.ADJUST
                },
                colors = ButtonDefaults.filledTonalButtonColors(),
            ) {
                Text(if (mode == AdjustMode.CLICKS_TO_OFFSET) "clicks → $lengthUnit" else "$lengthUnit → clicks")
            }
        }
    }
}

@Composable
private fun HoldoverScreen(prefs: SharedPreferences, metric: Boolean, isCurrent: Boolean) {
    var distance by remember { mutableIntStateOf(prefs.getInt("holdDistance", 30)) }
    LaunchedEffect(distance) { prefs.edit().putInt("holdDistance", distance).apply() }

    val unit = if (metric) "m" else "yd"
    val hold = remember(distance, metric) {
        val rangeM = if (metric) distance.toDouble() else distance * Ballistics.YARD_TO_M
        Ballistics.holdovers(RIFLE, listOf(rangeM))?.first()
    }
    val colors = MaterialTheme.colorScheme

    val (holdText, caption, impact) = when {
        hold == null || hold.heightM.isNaN() -> Triple("–", "out of range", "")
        else -> {
            val dots = hold.mils
            val caption = when {
                abs(dots) < 0.05 -> "dead on"
                dots > 0 -> "dots over"
                else -> "dots under"
            }
            val impact = when {
                abs(hold.heightM) < 0.0005 -> "on aim"
                metric -> String.format(Locale.UK, "%.1f cm %s", abs(hold.heightM) * 100, if (hold.heightM < 0) "low" else "high")
                else -> String.format(Locale.UK, "%.2f″ %s", abs(hold.heightM) / Ballistics.INCH_TO_M, if (hold.heightM < 0) "low" else "high")
            }
            Triple(String.format(Locale.UK, "%.1f", abs(dots)), caption, "$impact with no hold")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .crownSteps(isCurrent) { steps ->
                distance = (distance + steps).coerceIn(HOLD_MIN_DISTANCE, HOLD_MAX_DISTANCE)
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Holdover · 25 yd zero",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Text(text = "$distance $unit", style = MaterialTheme.typography.numeralSmall, color = colors.primary)
            Text(text = holdText, style = MaterialTheme.typography.displayMedium, color = colors.onSurface)
            Text(text = caption, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(text = impact, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

private fun length(inches: Double, metric: Boolean): String =
    if (metric) String.format(Locale.UK, "%.2f cm", inches * Moa.MM_PER_INCH / 10)
    else String.format(Locale.UK, "%.3f″", inches)

private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"
