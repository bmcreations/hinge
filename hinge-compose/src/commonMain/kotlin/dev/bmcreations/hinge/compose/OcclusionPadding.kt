package dev.bmcreations.hinge.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import dev.bmcreations.hinge.FoldRegion
import dev.bmcreations.hinge.FoldingState

/**
 * Padding that keeps content clear of occluding fold regions along the window's edges.
 *
 * This covers the case [FoldAwarePanes] does not: a region that hides pixels without dividing
 * the window into panes, such as a hinge seam or a camera housing intruding from one side.
 *
 * ```
 * Column(Modifier.fillMaxSize().padding(rememberOcclusionPadding())) { ... }
 * ```
 *
 * The result is direction-absolute, since fold geometry is physical.
 *
 * ### Scope
 * Only regions that touch a window edge produce padding — those are the ones an inset can
 * actually resolve. A region sitting in the middle of the window cannot be padded away; that
 * is a split, and [FoldAwarePanes] is the answer. Apply this to a full-window container, since
 * the geometry it reads is window-relative.
 *
 * A region in a corner, such as a camera housing, touches two edges but is padded on only one:
 * the edge that costs the least space. Pass the padding the content already applies as
 * [reserved] (a top bar, system insets): a region that space already clears needs nothing,
 * and the result covers only what [reserved] does not, so the two stack.
 */
@Composable
public fun rememberOcclusionPadding(
    state: FoldingState = LocalFoldingState.current,
    reserved: PaddingValues = PaddingValues(0.dp),
): PaddingValues {
    val direction = LocalLayoutDirection.current
    val reservedInsets = OcclusionInsets(
        left = reserved.calculateLeftPadding(direction).value,
        top = reserved.calculateTopPadding().value,
        right = reserved.calculateRightPadding(direction).value,
        bottom = reserved.calculateBottomPadding().value,
    )
    return remember(state, reservedInsets) { state.occlusionInsets(reservedInsets).toPadding() }
}

// Absolute, not start/end: these come from physical window coordinates. In an RTL layout
// `start` resolves to the physical right edge, which would pad the wrong side and draw
// content straight under the hardware.
private fun OcclusionInsets.toPadding(): PaddingValues =
    PaddingValues.Absolute(left = left.dp, top = top.dp, right = right.dp, bottom = bottom.dp)

/** Per-edge insets in logical points. */
internal data class OcclusionInsets(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object {
        val Zero = OcclusionInsets(0f, 0f, 0f, 0f)
    }
}

/** The geometry behind [rememberOcclusionPadding], kept free of Compose so it can be tested. */
internal fun FoldingState.occlusionInsets(reserved: OcclusionInsets = OcclusionInsets.Zero): OcclusionInsets {
    var left = 0f
    var top = 0f
    var right = 0f
    var bottom = 0f

    occludingRegions.forEach { region ->
        if (!region.isSubstantial) return@forEach
        // How deep the region reaches in from each edge it touches, and what that edge already has.
        val candidates = buildList {
            if (region.touchesLeft(this@occlusionInsets)) add(Edge.Left to region.bounds.right)
            if (region.touchesRight(this@occlusionInsets)) add(Edge.Right to windowSize.width - region.bounds.left)
            if (region.touchesTop(this@occlusionInsets)) add(Edge.Top to region.bounds.bottom)
            if (region.touchesBottom(this@occlusionInsets)) add(Edge.Bottom to windowSize.height - region.bounds.top)
        }
        if (candidates.isEmpty()) return@forEach
        // Content already kept clear on any touched edge is clear of the whole region.
        if (candidates.any { (edge, depth) -> reserved[edge] >= depth }) return@forEach
        val (edge, depth) = candidates.minBy { (edge, depth) -> depth - reserved[edge] }
        val extra = depth - reserved[edge]
        when (edge) {
            Edge.Left -> left = maxOf(left, extra)
            Edge.Top -> top = maxOf(top, extra)
            Edge.Right -> right = maxOf(right, extra)
            Edge.Bottom -> bottom = maxOf(bottom, extra)
        }
    }
    return OcclusionInsets(left, top, right, bottom)
}

private enum class Edge { Left, Top, Right, Bottom }

private operator fun OcclusionInsets.get(edge: Edge): Float = when (edge) {
    Edge.Left -> left
    Edge.Top -> top
    Edge.Right -> right
    Edge.Bottom -> bottom
}

private const val EDGE_TOLERANCE = 0.5f

private fun FoldRegion.touchesLeft(state: FoldingState): Boolean =
    bounds.left <= EDGE_TOLERANCE && bounds.right < state.windowSize.width

private fun FoldRegion.touchesRight(state: FoldingState): Boolean =
    bounds.right >= state.windowSize.width - EDGE_TOLERANCE && bounds.left > 0f

private fun FoldRegion.touchesTop(state: FoldingState): Boolean =
    bounds.top <= EDGE_TOLERANCE && bounds.bottom < state.windowSize.height

private fun FoldRegion.touchesBottom(state: FoldingState): Boolean =
    bounds.bottom >= state.windowSize.height - EDGE_TOLERANCE && bounds.top > 0f
