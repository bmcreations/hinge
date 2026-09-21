package dev.bmcreations.hinge.sample

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.bmcreations.hinge.FoldingState
import dev.bmcreations.hinge.compose.ProvideFoldingState
import dev.bmcreations.hinge.compose.rememberFoldingState

/**
 * Root of the Compose Multiplatform sample. Identical code runs on Android and iOS.
 *
 * Three things are on show:
 *  1. `FoldAwarePanes` doing list/detail without the app knowing anything about hardware.
 *  2. A posture simulator, so every fold state is reachable on an ordinary phone or simulator.
 *  3. An inspector showing exactly what the SDK reports and what geometry it resolved to.
 */
@Composable
fun DemoApp() {
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            DemoRoot()
        }
    }
}

@Composable
private fun DemoRoot() {
    var simulation by remember { mutableStateOf(SimulatedPosture.Live) }
    var inspectorOpen by remember { mutableStateOf(true) }
    val live = rememberFoldingState()

    // Hoisted so the inspector can report the state the demo surface actually resolved,
    // which differs from `live` whenever a posture is being simulated.
    var resolved by remember { mutableStateOf(FoldingState()) }

    var barHeight by remember { mutableStateOf(0.dp) }
    var inspectorHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    // The controls OVERLAY the demo surface rather than stacking above and below it, and this
    // is load-bearing rather than cosmetic. Fold geometry is window-relative: if the panes
    // container did not start at the window origin, every simulated posture would be built in
    // one coordinate space and consumed in another, and `FoldAwarePanes.inLocalSpace` would
    // subtract an offset that was never added. A horizontal fold would land off-centre and,
    // near the minimum pane size, silently stop splitting at all. Keeping the surface
    // full-window makes the translation a no-op and keeps the demo honest.
    Box(Modifier.fillMaxSize()) {

        DemoSurface(
            simulation = simulation,
            live = live,
            contentPadding = PaddingValues(top = barHeight, bottom = inspectorHeight),
            onResolved = { resolved = it },
        )

        SimulatorBar(
            selected = simulation,
            onSelect = { simulation = it },
            inspectorOpen = inspectorOpen,
            onToggleInspector = { inspectorOpen = !inspectorOpen },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onGloballyPositioned {
                    barHeight = with(density) { it.size.height.toDp() }
                },
        )

        if (inspectorOpen) {
            PostureInspector(
                state = resolved,
                simulated = simulation != SimulatedPosture.Live,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned {
                        inspectorHeight = with(density) { it.size.height.toDp() }
                    },
            )
        } else {
            LaunchedEffect(Unit) { inspectorHeight = 0.dp }
        }
    }
}

/**
 * The surface the demo content is given.
 *
 * Usually the full window. For the cover-display case it is deliberately shrunk, because
 * handing the layout a small `windowSize` is not enough on its own: the UI adapters measure
 * themselves and override it. Simulating a small device means actually giving it less room.
 *
 * The cover surface is centred rather than at the window origin, which would normally
 * reintroduce the coordinate mismatch described in [DemoRoot]. It does not, because a cover
 * display reports no fold regions at all — there is nothing to translate.
 */
@Composable
private fun DemoSurface(
    simulation: SimulatedPosture,
    live: FoldingState,
    contentPadding: PaddingValues,
    onResolved: (FoldingState) -> Unit,
) {
    val override = simulation.surfaceOverride
    val shape = RoundedCornerShape(if (override == null) 0.dp else 20.dp)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = if (override == null) {
                Modifier.fillMaxSize()
            } else {
                Modifier.size(override.width.dp, override.height.dp)
            },
            shape = shape,
            color = MaterialTheme.colorScheme.background,
            border = if (override == null) {
                null
            } else {
                BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().clip(shape)) {
                val state = simulation.resolve(live, maxWidth.value, maxHeight.value)
                LaunchedEffect(state) { onResolved(state) }
                ProvideFoldingState(state) {
                    NotesScreen(contentPadding = if (override == null) contentPadding else NoPadding)
                }
            }
        }
    }
}

private val NoPadding = PaddingValues(0.dp)

@Composable
private fun SimulatorBar(
    selected: SimulatedPosture,
    onSelect: (SimulatedPosture) -> Unit,
    inspectorOpen: Boolean,
    onToggleInspector: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Posture", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = onToggleInspector) {
                    Text(if (inspectorOpen) "Hide inspector" else "Show inspector")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SimulatedPosture.entries.forEach { posture ->
                    FilterChip(
                        selected = posture == selected,
                        onClick = { onSelect(posture) },
                        label = { Text(posture.label) },
                    )
                }
            }
        }
    }
}
