package dev.bmcreations.hinge

/**
 * A region of the window that content must account for, because the display is physically
 * interrupted there.
 *
 * Both platforms converge on this concept and both split it the same two ways, so the library
 * models the two properties independently rather than as an enum — a single region can be both:
 *
 * | | Android (`androidx.window`) | iOS 27.1 |
 * |---|---|---|
 * | [isSeparating] | `FoldingFeature.isSeparating` | `reservedRegions(kind: .division)` |
 * | [isOccluding] | `occlusionType == FULL` | `reservedRegions(kind: .occlusion)` |
 */
public data class FoldRegion(
    /** Bounds in the coordinate space of the enclosing [FoldingState]. */
    val bounds: FoldRect,

    /**
     * The region divides the window into logically distinct areas. Content should be placed
     * on one side or the other, never spanning it. This is the property that drives splitting.
     */
    val isSeparating: Boolean,

    /**
     * The region hides pixels behind it. Content placed here is not visible, so interactive
     * or load-bearing content must avoid it even when [isSeparating] is false.
     */
    val isOccluding: Boolean,

    /**
     * Whether the region currently applies. Inactive regions are reported so that layouts can
     * reserve space ahead of a fold rather than jumping when it happens; they should not
     * change the current layout.
     */
    val isActive: Boolean = true,

    /** Platform-supplied identifier where one exists, for debugging and for stable keys. */
    val identifier: String? = null,
) {
    /** The axis the region runs along. See [FoldRect.axis]. */
    public val axis: FoldAxis get() = bounds.axis

    /** True when the region has real thickness and will visibly interrupt content. */
    public val isSubstantial: Boolean get() = bounds.isSubstantial
}
