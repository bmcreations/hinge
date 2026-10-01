package dev.bmcreations.hinge

/**
 * The resolved geometry for a one- or two-pane layout, in the coordinate space of the
 * [FoldingState] it was derived from.
 *
 * Exhaustively handling these two cases is the whole contract: there is no "is it folded"
 * boolean to get wrong, and no posture enum to forget a branch of.
 */
public sealed interface PaneLayout {

    /** Every layout occupies a rectangle, whether it is split or not. */
    public val bounds: FoldRect


    /** One continuous area. Render the primary content only. */
    public data class Single(
        override val bounds: FoldRect,
    ) : PaneLayout

    /** Two areas with a gap between them that content must not cross. */
    public data class Split(
        val primary: FoldRect,
        val secondary: FoldRect,
        /**
         * The dead space between the panes. When the split came from a fold this is the fold
         * region, which may have zero thickness on a seamless display; when it came from
         * [SplitSpec.gutter] it is that gap.
         */
        val gutter: FoldRect,
        /** The hinge axis. [FoldAxis.Vertical] means primary is left, secondary is right. */
        val axis: FoldAxis,
        override val bounds: FoldRect,
    ) : PaneLayout
}

/**
 * Resolves [this] state and [spec] into concrete pane geometry.
 *
 * This is the function the whole library exists to provide. Everything else — the platform
 * sources and the Compose adapter — is plumbing that feeds it or draws its result.
 *
 * It is a pure function, so it is worth calling directly in tests against synthetic
 * [FoldingState] values rather than only through the UI layers.
 */
public fun FoldingState.paneLayout(spec: SplitSpec = SplitSpec()): PaneLayout {
    val full = FoldRect.ofSize(windowSize)
    if (!full.isSubstantial) return PaneLayout.Single(full)

    val fold = separatingRegion

    val wantsSplit = when (spec.strategy) {
        SplitStrategy.NeverSplit -> false
        SplitStrategy.AlwaysSplit -> true
        SplitStrategy.FoldAware -> fold != null || fitsTwoPanes(full, spec)
    }
    if (!wantsSplit) return PaneLayout.Single(full)

    val split = if (fold != null) splitAtFold(full, fold, spec) else splitAtRatio(full, spec)
    return split ?: PaneLayout.Single(full)
}

private fun fitsTwoPanes(full: FoldRect, spec: SplitSpec): Boolean {
    val extent = when (resolveAxis(spec)) {
        FoldAxis.Vertical -> full.width
        FoldAxis.Horizontal -> full.height
    }
    return extent - spec.gutter >= spec.minPaneSize * 2f
}

/**
 * The split direction to use when there is no fold to take one from.
 *
 * Defaults to [FoldAxis.Vertical] — panes side by side — rather than deriving the axis from
 * the window's aspect ratio. Deriving it looks reasonable and is wrong: a tall phone is
 * "taller than it is wide", so an aspect-derived axis splits an ordinary 411x891 phone into
 * a top and bottom pane. Two-pane layouts are a width affordance, so width is what decides.
 * Set [SplitSpec.preferredAxis] explicitly for a genuinely stacked layout.
 */
private fun resolveAxis(spec: SplitSpec): FoldAxis =
    spec.preferredAxis ?: FoldAxis.Vertical

private fun splitAtFold(full: FoldRect, fold: FoldRegion, spec: SplitSpec): PaneLayout.Split? {
    val g = fold.bounds.intersect(full) ?: return null
    return when (fold.axis) {
        FoldAxis.Vertical -> build(
            primary = FoldRect(full.left, full.top, g.left, full.bottom),
            secondary = FoldRect(g.right, full.top, full.right, full.bottom),
            gutter = FoldRect(g.left, full.top, g.right, full.bottom),
            axis = FoldAxis.Vertical,
            full = full,
            spec = spec,
        )
        FoldAxis.Horizontal -> build(
            primary = FoldRect(full.left, full.top, full.right, g.top),
            secondary = FoldRect(full.left, g.bottom, full.right, full.bottom),
            gutter = FoldRect(full.left, g.top, full.right, g.bottom),
            axis = FoldAxis.Horizontal,
            full = full,
            spec = spec,
        )
    }
}

private fun splitAtRatio(full: FoldRect, spec: SplitSpec): PaneLayout.Split? {
    val axis = resolveAxis(spec)
    val ratio = spec.ratio.coerceIn(0.15f, 0.85f)
    val half = spec.gutter / 2f
    return when (axis) {
        FoldAxis.Vertical -> {
            val cut = full.left + full.width * ratio
            build(
                primary = FoldRect(full.left, full.top, cut - half, full.bottom),
                secondary = FoldRect(cut + half, full.top, full.right, full.bottom),
                gutter = FoldRect(cut - half, full.top, cut + half, full.bottom),
                axis = axis,
                full = full,
                spec = spec,
            )
        }
        FoldAxis.Horizontal -> {
            val cut = full.top + full.height * ratio
            build(
                primary = FoldRect(full.left, full.top, full.right, cut - half),
                secondary = FoldRect(full.left, cut + half, full.right, full.bottom),
                gutter = FoldRect(full.left, cut - half, full.right, cut + half),
                axis = axis,
                full = full,
                spec = spec,
            )
        }
    }
}

/** Rejects any split whose panes fall below [SplitSpec.minPaneSize] along the split direction. */
private fun build(
    primary: FoldRect,
    secondary: FoldRect,
    gutter: FoldRect,
    axis: FoldAxis,
    full: FoldRect,
    spec: SplitSpec,
): PaneLayout.Split? {
    val (a, b) = when (axis) {
        FoldAxis.Vertical -> primary.width to secondary.width
        FoldAxis.Horizontal -> primary.height to secondary.height
    }
    if (a < spec.minPaneSize || b < spec.minPaneSize) return null
    return PaneLayout.Split(primary, secondary, gutter, axis, full)
}
