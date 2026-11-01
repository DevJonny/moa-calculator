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
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import java.util.Locale

private val CLICK_VALUES = listOf(0.125 to "⅛", 0.25 to "¼", 0.5 to "½", 1.0 to "1")
private const val DISTANCE_STEP = 5
private const val MAX_DISTANCE = 1500
private const val MAX_CLICKS = 200

// Rotary pixels per step. The Pixel Watch crown is smooth, so this sets how far a turn moves a value.
private const val ROTARY_PIXELS_PER_STEP = 30f

// Matches the web app's accent so the crown-controlled value stands out.
private val colorScheme = ColorScheme(
    primary = Color(0xFFF2A541),
    primaryDim = Color(0xFFC98A36),
    onPrimary = Color(0xFF1A1206),
)

private enum class Field { DISTANCE, CLICKS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("moa", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = colorScheme) {
                AppScaffold { MoaScreen(prefs) }
            }
        }
    }
}

@Composable
private fun MoaScreen(prefs: SharedPreferences) {
    var clickIndex by remember { mutableIntStateOf(prefs.getInt("click", 1)) }
    var metric by remember { mutableStateOf(prefs.getBoolean("metric", false)) }
    var distance by remember { mutableIntStateOf(prefs.getInt("distance", 100)) }
    var clicks by remember { mutableIntStateOf(prefs.getInt("clicks", 4)) }
    var active by remember { mutableStateOf(Field.DISTANCE) }
    var rotaryRemainder by remember { mutableFloatStateOf(0f) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(clickIndex, metric, distance, clicks) {
        prefs.edit()
            .putInt("click", clickIndex)
            .putBoolean("metric", metric)
            .putInt("distance", distance)
            .putInt("clicks", clicks)
            .apply()
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val (clickMoa, clickLabel) = CLICK_VALUES[clickIndex]
    val yards = Moa.toYards(distance.toDouble(), metric)
    val perClick = Moa.clickInInches(clickMoa, yards)
    val moved = Moa.offsetForClicks(clicks, clickMoa, yards)
    val colors = MaterialTheme.colorScheme
    fun highlight(field: Field) = if (active == field) colors.primary else colors.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                rotaryRemainder += event.verticalScrollPixels
                val steps = (rotaryRemainder / ROTARY_PIXELS_PER_STEP).toInt()
                if (steps != 0) {
                    rotaryRemainder -= steps * ROTARY_PIXELS_PER_STEP
                    when (active) {
                        Field.DISTANCE -> distance = (distance + steps * DISTANCE_STEP).coerceIn(DISTANCE_STEP, MAX_DISTANCE)
                        Field.CLICKS -> clicks = (clicks + steps).coerceIn(0, MAX_CLICKS)
                    }
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactButton(onClick = { clickIndex = (clickIndex + 1) % CLICK_VALUES.size }) {
                    Text("$clickLabel MOA")
                }
                CompactButton(onClick = { metric = !metric }) {
                    Text(if (metric) "m" else "yd")
                }
            }
            Text(
                text = "$distance ${if (metric) "m" else "yd"}",
                style = MaterialTheme.typography.numeralMedium,
                color = highlight(Field.DISTANCE),
                modifier = Modifier.clickable { active = Field.DISTANCE },
            )
            Text(
                text = "${length(perClick, metric)} / click",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Text(
                text = "$clicks clicks = ${length(moved, metric)}",
                style = MaterialTheme.typography.titleMedium,
                color = highlight(Field.CLICKS),
                modifier = Modifier.clickable { active = Field.CLICKS },
            )
        }
    }
}

private fun length(inches: Double, metric: Boolean): String =
    if (metric) String.format(Locale.UK, "%.2f cm", inches * Moa.MM_PER_INCH / 10)
    else String.format(Locale.UK, "%.3f″", inches)
