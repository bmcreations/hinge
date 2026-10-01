package dev.bmcreations.hinge.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.bmcreations.hinge.FoldingState
import dev.bmcreations.hinge.PaneLayout
import dev.bmcreations.hinge.SplitSpec

private enum class PaneSlot { Primary, Secondary }

/**
 * Lays out [primary] and [secondary] as one or two panes, avoiding the hinge.
 *
 * This is the main entry point. It handles the three things that are tedious to get right by
 * hand:
 *
 * 1. **Coordinate translation.** Platform fold geometry is window-relative. This composable
 *    translates it into its own bounds, so a `FoldAwarePanes` that is not full-window splits
 *    correctly, or declines to split when the fold does not actually cross it.
 * 2. **Subcomposition.** In single-pane mode [secondary] is never composed, so a detail pane
 *    costs nothing when it is not showing.
 * 3. **Collapse.** Any split that would produce a pane below [SplitSpec.minPaneSize] falls
 *    back to a single pane, which is what makes cover displays and small windows behave.
 *
 * ```
 * FoldAwarePanes(
 *     primary = { ConversationList(onSelect = { selected = it }) },
 *     secondary = { ConversationDetail(selected) },
 *     modifier = Modifier.fillMaxSize(),
 * )
 * ```
 *
 * ### Requires bounded constraints
 * This is a space-filling layout. Placed inside a scrolling parent it has no height to divide,
 * so it measures [primary] alone against the incoming constraints and skips the split. Give it
 * a bounded size if you want two panes.
 *
 * ### Right-to-left
 * With [mirrorInRtl] (the default) and a side-by-side split, [primary] goes in the right-hand
 * pane under an RTL layout direction, matching how a list/detail reads in those languages. A
 * stacked split is unaffected.
 *
 * ### Reading the layout from inside a pane
 * Both panes can read [LocalPaneLayout] for the geometry they were measured with. Use it rather
 * than [rememberPaneLayout] to decide, for example, whether to show a back button.
 *
 * ### One frame of settling
 * The window offset comes from `onGloballyPositioned`, so a `FoldAwarePanes` that is *not*
 * full-window resolves its final geometry on the layout pass after its first. Full-window
 * usage, which is the common case, is correct on the first pass.
 */
@Composable
public fun FoldAwarePanes(
    primary: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    state: FoldingState = LocalFoldingState.current,
    spec: SplitSpec = SplitSpec(),
    mirrorInRtl: Boolean = true,
    onPaneLayoutChanged: ((PaneLayout) -> Unit)? = null,
) {
    PaneHost(
        modifier = modifier,
        state = state,
        spec = spec,
        mirrorInRtl = mirrorInRtl,
        onPaneLayoutChanged = onPaneLayoutChanged,
    ) { layout ->
        val first = PaneContent(PaneSlot.Primary, primary)
        if (layout is PaneLayout.Split) first to PaneContent(PaneSlot.Secondary, secondary) else first to null
    }
}
