package dev.bmcreations.hinge.sample

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bmcreations.hinge.FoldRect
import dev.bmcreations.hinge.FoldingState
import dev.bmcreations.hinge.PaneLayout
import dev.bmcreations.hinge.paneLayout

/**
 * Shows exactly what the SDK reports and what it resolved to.
 *
 * Useful beyond the demo: drop it behind a debug flag in a real app and the next foldable bug
 * report arrives with the posture, the regions and the pane rects already in it.
 */
@Composable
fun PostureInspector(
    state: FoldingState,
    simulated: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Inspector", style = MaterialTheme.typography.labelLarge)
                if (simulated) {
                    Text(
                        text = "SIMULATED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Field("posture", state.posture.name)
            Field("foldAxis", state.foldAxis?.name ?: "none")
            Field("window", "${state.windowSize.width.r()} x ${state.windowSize.height.r()}")
            Field("hingeAngle", state.hingeAngle?.let { "${it.degrees.r()}°" } ?: "unavailable")
            Field("separated", state.isSeparated.toString())

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (state.regions.isEmpty()) {
                Field("regions", "none")
            } else {
                state.regions.forEachIndexed { index, region ->
                    val flags = buildList {
                        if (region.isSeparating) add("separating")
                        if (region.isOccluding) add("occluding")
                        if (!region.isActive) add("inactive")
                    }.joinToString(",").ifEmpty { "none" }
                    Field("region[$index]", "${region.bounds.pretty()}  $flags")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when (val layout = state.paneLayout()) {
                is PaneLayout.Single -> Field("layout", "Single ${layout.bounds.pretty()}")
                is PaneLayout.Split -> {
                    Field("layout", "Split (${layout.axis.name})")
                    Field("  primary", layout.primary.pretty())
                    Field("  secondary", layout.secondary.pretty())
                    Field("  gutter", layout.gutter.pretty())
                }
            }
        }
    }
}

@Composable
private fun Field(name: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun FoldRect.pretty(): String = "[${left.r()}, ${top.r()}, ${right.r()}, ${bottom.r()}]"

/** One decimal place, without pulling in a formatting dependency for a debug panel. */
private fun Float.r(): String {
    val scaled = (this * 10f).toInt()
    // Integer division truncates toward zero, so -0.5 would otherwise print as "0.5".
    val sign = if (this < 0f && scaled > -10) "-" else ""
    return "$sign${scaled / 10}.${kotlin.math.abs(scaled % 10)}"
}
