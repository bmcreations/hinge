package dev.bmcreations.hinge

/**
 * How aggressively a two-pane layout should be produced.
 */
public enum class SplitStrategy {
    /**
     * Split when the device is folded into distinct panes, **or** when the window is simply
     * large enough to hold two. This is the right default: it gives a Duo two panes when open
     * flat and when half-opened, gives a tablet two panes, and gives a phone one.
     */
    FoldAware,

    /**
     * Always produce two panes when they clear [SplitSpec.minPaneSize], regardless of posture.
     * For layouts that are inherently two-pane, such as a list/detail that has no single-pane
     * fallback.
     */
    AlwaysSplit,

    /**
     * Never split. Useful to disable the behaviour from a single call site, for example behind
     * a user preference, without unwinding the layout.
     */
    NeverSplit,
}

/**
 * Inputs to [paneLayout]. The defaults are tuned for a list/detail layout and are a reasonable
 * starting point for most apps.
 */
public data class SplitSpec(
    val strategy: SplitStrategy = SplitStrategy.FoldAware,

    /**
     * Fraction of the available extent given to the primary pane when there is no fold to
     * split around. Ignored when a fold is present, because the hardware decides the ratio.
     * Clamped to 0.15..0.85.
     */
    val ratio: Float = 0.5f,

    /**
     * Smallest acceptable pane, in logical points, along the split direction.
     *
     * If either pane would come out below this the layout collapses to a single pane instead.
     * This is what stops a cover display or a narrow window from producing two unusable
     * slivers, so prefer raising it over special-casing postures yourself.
     */
    val minPaneSize: Float = 320f,

    /**
     * Gap between panes when there is no fold to split around, in logical points. When a fold
     * *is* present the gutter is the fold itself and this value is ignored.
     */
    val gutter: Float = 0f,

    /**
     * Forces the split direction when there is no fold to take it from.
     *
     * `null` means side by side ([FoldAxis.Vertical]). Deriving the axis from the window's
     * aspect instead looks reasonable and is wrong — a tall phone would split into a top and
     * bottom pane. Set this explicitly for a genuinely stacked layout.
     */
    val preferredAxis: FoldAxis? = null,
)
