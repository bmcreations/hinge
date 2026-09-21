package dev.bmcreations.hinge.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 */
@Composable
public fun rememberOcclusionPadding(
    state: FoldingState = LocalFoldingState.current,
): PaddingValues = remember(state) {
    var left = 0f
    var top = 0f
    var right = 0f
    var bottom = 0f

    state.occludingRegions.forEach { region ->
        if (!region.isSubstantial) return@forEach
        when {
            region.touchesLeft(state) -> left = maxOf(left, region.bounds.right)
            region.touchesRight(state) -> right = maxOf(right, state.windowSize.width - region.bounds.left)
            region.touchesTop(state) -> top = maxOf(top, region.bounds.bottom)
            region.touchesBottom(state) -> bottom = maxOf(bottom, state.windowSize.height - region.bounds.top)
        }
    }

    // Absolute, not start/end: these come from physical window coordinates. In an RTL layout
    // `start` resolves to the physical right edge, which would pad the wrong side and draw
    // content straight under the hardware.
    PaddingValues.Absolute(left = left.dp, top = top.dp, right = right.dp, bottom = bottom.dp)
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
