package dev.bmcreations.hinge.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import dev.bmcreations.hinge.FoldingState
import dev.bmcreations.hinge.PaneLayout
import dev.bmcreations.hinge.SplitSpec
import dev.bmcreations.hinge.paneLayout

/**
 * The ambient fold posture.
 *
 * Defaults to [FoldingState] with an unknown posture, which every layout in this library
 * renders as a single pane. That default is what makes the library safe to use in previews,
 * tests and non-foldable hosts without any setup.
 *
 * Deliberately not a `staticCompositionLocalOf`: a static local does not track reads, so every
 * posture change would recompose the whole subtree under [ProvideFoldingState] rather than
 * just the parts that actually read the posture.
 */
public val LocalFoldingState: ProvidableCompositionLocal<FoldingState> =
    compositionLocalOf { FoldingState() }

/**
 * Observes the platform's fold posture for the current window.
 *
 * On Android this needs the composition to be hosted by an `Activity`; an application-context
 * host reports [dev.bmcreations.hinge.FoldPosture.Unknown] rather than throwing, so previews
 * keep working. On iOS it reads the bridge registered by `HingeKit.install()`.
 */
@Composable
public expect fun rememberFoldingState(): FoldingState

/**
 * Publishes [state] to the subtree.
 *
 * Call this once, high in your composition — around your app's root content — and everything
 * below can read [LocalFoldingState] without threading state through every layer.
 *
 * ```
 * setContent {
 *     ProvideFoldingState {
 *         AppTheme { AppNavHost() }
 *     }
 * }
 * ```
 */
@Composable
public fun ProvideFoldingState(
    state: FoldingState = rememberFoldingState(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFoldingState provides state, content = content)
}

/**
 * Resolves pane geometry for the **window**, memoized on the inputs.
 *
 * Use this when you need to branch on whether two panes are showing — a list/detail that must
 * decide whether selecting an item navigates or just updates the neighbouring pane. The
 * library deliberately does not own that navigation decision.
 *
 * ```
 * val layout = rememberPaneLayout()
 * val twoPane = layout is PaneLayout.Split
 * BackHandler(enabled = !twoPane && selected != null) { selected = null }
 * ```
 *
 * Note this is window geometry. If you need geometry for a component that is not full-window,
 * use [FoldAwarePanes], which does the coordinate translation for you.
 */
@Composable
public fun rememberPaneLayout(
    state: FoldingState = LocalFoldingState.current,
    spec: SplitSpec = SplitSpec(),
): PaneLayout = remember(state, spec) { state.paneLayout(spec) }
