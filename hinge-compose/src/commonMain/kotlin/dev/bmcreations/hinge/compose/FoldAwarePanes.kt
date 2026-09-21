package dev.bmcreations.hinge.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.bmcreations.hinge.FoldAxis
import dev.bmcreations.hinge.FoldRect
import dev.bmcreations.hinge.FoldSize
import dev.bmcreations.hinge.FoldingState
import dev.bmcreations.hinge.PaneLayout
import dev.bmcreations.hinge.SplitSpec
import dev.bmcreations.hinge.paneLayout

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
    onPaneLayoutChanged: ((PaneLayout) -> Unit)? = null,
) {
    // onPaneLayoutChanged is invoked during the measure pass. Use it to record geometry for
    // things like back handling, but never to write Compose state that this same subtree
    // reads during layout -- that is the classic "state modified during layout" crash.
    var originX by remember { mutableStateOf(0f) }
    var originY by remember { mutableStateOf(0f) }

    SubcomposeLayout(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val position = coordinates.positionInWindow()
            if (position.x != originX) originX = position.x
            if (position.y != originY) originY = position.y
        },
    ) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight

        // Unbounded in either direction means there is nothing to divide.
        val bounded = constraints.hasBoundedWidth && constraints.hasBoundedHeight

        val resolved: PaneLayout = if (!bounded) {
            PaneLayout.Single(FoldRect.Zero)
        } else {
            state
                .inLocalSpace(
                    originX = originX.toDp().value,
                    originY = originY.toDp().value,
                    size = FoldSize(width.toDp().value, height.toDp().value),
                )
                .paneLayout(spec)
        }

        onPaneLayoutChanged?.invoke(resolved)

        when (resolved) {
            is PaneLayout.Single -> {
                val placeable = subcompose(PaneSlot.Primary) { Box { primary() } }
                    .first()
                    .measure(if (bounded) Constraints.fixed(width, height) else constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }

            is PaneLayout.Split -> {
                // Each edge is rounded independently, so summing rounded sizes can overshoot
                // the parent by a pixel and clip the trailing pane. Anchor both panes to
                // rounded offsets and derive the trailing size from the remaining space.
                val primaryLeft = resolved.primary.left.dp.roundToPx()
                val primaryTop = resolved.primary.top.dp.roundToPx()
                val secondaryLeft = resolved.secondary.left.dp.roundToPx()
                val secondaryTop = resolved.secondary.top.dp.roundToPx()

                val primarySize: Constraints
                val secondarySize: Constraints
                when (resolved.axis) {
                    FoldAxis.Vertical -> {
                        primarySize = Constraints.fixed(
                            resolved.primary.right.dp.roundToPx() - primaryLeft,
                            height,
                        )
                        secondarySize = Constraints.fixed(width - secondaryLeft, height)
                    }
                    FoldAxis.Horizontal -> {
                        primarySize = Constraints.fixed(
                            width,
                            resolved.primary.bottom.dp.roundToPx() - primaryTop,
                        )
                        secondarySize = Constraints.fixed(width, height - secondaryTop)
                    }
                }

                val first = subcompose(PaneSlot.Primary) { Box { primary() } }
                    .first()
                    .measure(primarySize)
                val second = subcompose(PaneSlot.Secondary) { Box { secondary() } }
                    .first()
                    .measure(secondarySize)

                layout(width, height) {
                    first.place(primaryLeft, primaryTop)
                    second.place(secondaryLeft, secondaryTop)
                }
            }
        }
    }
}
