package dev.bmcreations.hinge.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.bmcreations.hinge.FoldAxis
import dev.bmcreations.hinge.FoldRect
import dev.bmcreations.hinge.FoldSize
import dev.bmcreations.hinge.FoldingState
import dev.bmcreations.hinge.PaneLayout
import dev.bmcreations.hinge.SplitSpec
import dev.bmcreations.hinge.paneLayout

/**
 * The pane geometry that the nearest enclosing [FoldAwarePanes] or [ListDetailPanes] resolved,
 * in that layout's own coordinate space. `null` outside one.
 *
 * Prefer this over [rememberPaneLayout] inside pane content. [rememberPaneLayout] resolves the
 * *window*, so in a layout that is not full-window, or while the platform's reported window size
 * lags a resize, the two can disagree about whether there are two panes. This value is the
 * geometry the panes were actually measured with.
 */
public val LocalPaneLayout: ProvidableCompositionLocal<PaneLayout?> =
    staticCompositionLocalOf { null }

/** One pane's content, keyed by what it shows rather than where it sits. */
internal class PaneContent(
    val key: Any,
    val content: @Composable () -> Unit,
)

/**
 * The layout behind [FoldAwarePanes] and [ListDetailPanes].
 *
 * [panes] receives the resolved layout and returns what goes in the leading pane and, when
 * split, the trailing one. Each pane is subcomposed under its [PaneContent.key], so content that
 * stays on screen across a single/split change keeps its subcomposition and all of its state,
 * even when it moves from one pane to the other.
 */
@Composable
internal fun PaneHost(
    modifier: Modifier,
    state: FoldingState,
    spec: SplitSpec,
    mirrorInRtl: Boolean,
    onPaneLayoutChanged: ((PaneLayout) -> Unit)?,
    panes: (PaneLayout) -> Pair<PaneContent, PaneContent?>,
) {
    // onPaneLayoutChanged is invoked during the measure pass. Use it to record geometry for
    // things like analytics, but never to write Compose state that this same subtree reads
    // during layout -- that is the classic "state modified during layout" crash.
    var originX by remember { mutableStateOf(0f) }
    var originY by remember { mutableStateOf(0f) }
    val direction = LocalLayoutDirection.current

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

        val (leading, trailing) = panes(resolved)

        fun compose(pane: PaneContent) = subcompose(pane.key) {
            CompositionLocalProvider(LocalPaneLayout provides resolved) {
                Box { pane.content() }
            }
        }.first()

        if (resolved !is PaneLayout.Split || trailing == null) {
            val placeable = compose(leading)
                .measure(if (bounded) Constraints.fixed(width, height) else constraints)
            return@SubcomposeLayout layout(placeable.width, placeable.height) {
                placeable.place(0, 0)
            }
        }

        // Each edge is rounded independently, so summing rounded sizes can overshoot the parent
        // by a pixel and clip the trailing pane. Anchor both panes to rounded offsets and derive
        // the trailing size from the remaining space.
        val firstLeft = resolved.primary.left.dp.roundToPx()
        val firstTop = resolved.primary.top.dp.roundToPx()
        val secondLeft = resolved.secondary.left.dp.roundToPx()
        val secondTop = resolved.secondary.top.dp.roundToPx()

        val firstSize: Constraints
        val secondSize: Constraints
        when (resolved.axis) {
            FoldAxis.Vertical -> {
                firstSize = Constraints.fixed(resolved.primary.right.dp.roundToPx() - firstLeft, height)
                secondSize = Constraints.fixed(width - secondLeft, height)
            }
            FoldAxis.Horizontal -> {
                firstSize = Constraints.fixed(width, resolved.primary.bottom.dp.roundToPx() - firstTop)
                secondSize = Constraints.fixed(width, height - secondTop)
            }
        }

        // In a right-to-left layout the leading pane belongs on the right. Swap which content
        // goes into which physical rectangle rather than mirroring the rectangles: the fold is
        // where it is, and an off-centre hinge mirrored would land the gap in the wrong place.
        val swap = mirrorInRtl &&
            direction == LayoutDirection.Rtl &&
            resolved.axis == FoldAxis.Vertical

        val leftContent = if (swap) trailing else leading
        val rightContent = if (swap) leading else trailing

        val first = compose(leftContent).measure(firstSize)
        val second = compose(rightContent).measure(secondSize)

        layout(width, height) {
            // place, not placeRelative: these are physical positions around physical hardware.
            first.place(firstLeft, firstTop)
            second.place(secondLeft, secondTop)
        }
    }
}
