package dev.bmcreations.hinge

/**
 * A complete, platform-neutral snapshot of how the device is folded and what that means for
 * the current window.
 *
 * Instances are cheap value objects; a new one is emitted on every posture, size or region
 * change. Always treat [Unknown] as a valid, renderable state rather than a loading state.
 */
public data class FoldingState(
    /** Normalized posture. Branch layout on this. */
    val posture: FoldPosture = FoldPosture.Unknown,

    /** Size of the coordinate space the [regions] are expressed in, in logical points. */
    val windowSize: FoldSize = FoldSize.Zero,

    /** Every region the platform reports, separating and occluding, active and inactive. */
    val regions: List<FoldRegion> = emptyList(),

    /**
     * Live hinge angle where the platform provides one. Read [HingeAngle]'s documentation
     * before using it: this is an interaction signal, not a layout signal.
     */
    val hingeAngle: HingeAngle? = null,
) {
    /** The active separating region, if any. This is what [paneLayout] splits around. */
    public val separatingRegion: FoldRegion?
        get() = regions.firstOrNull { it.isSeparating && it.isActive }

    /** Active regions that hide content and must be avoided even when not splitting. */
    public val occludingRegions: List<FoldRegion>
        get() = regions.filter { it.isOccluding && it.isActive }

    /** True when the window is currently divided by a fold. */
    public val isSeparated: Boolean get() = separatingRegion != null

    /** The axis of the active fold, or `null` when the window is not divided. */
    public val foldAxis: FoldAxis? get() = separatingRegion?.axis

    /**
     * Re-expresses this state in the local coordinate space of a child that occupies
     * [size] at ([originX], [originY]) within the window.
     *
     * Getting this wrong is the most common foldable layout bug: platform regions are always
     * window-relative, so a component that is not itself full-window will split around a fold
     * line that is nowhere near it. UI adapters call this automatically; call it yourself if
     * you consume [FoldingState] in custom layout code.
     *
     * Regions that fall entirely outside the child are dropped. Regions that partly overlap
     * are clipped, which correctly turns a fold at the child's edge into a no-op split.
     */
    public fun inLocalSpace(originX: Float, originY: Float, size: FoldSize): FoldingState {
        if (originX == 0f && originY == 0f && size == windowSize) return this
        val local = FoldRect.ofSize(size)
        return copy(
            windowSize = size,
            regions = regions.mapNotNull { region ->
                val moved = region.bounds.translate(-originX, -originY)
                val clipped = moved.intersect(local) ?: return@mapNotNull null
                region.copy(bounds = clipped)
            },
        )
    }

    public companion object {
        /**
         * The state every non-folding device reports, and the correct value to render on the
         * very first frame before the platform has published anything.
         */
        public fun flat(windowSize: FoldSize): FoldingState =
            FoldingState(posture = FoldPosture.Flat, windowSize = windowSize)

        /**
         * An empty, unknown state. Exists mainly for Swift, where Kotlin default arguments
         * are not exported and `FoldingState()` is therefore not callable.
         */
        public fun unknown(): FoldingState = FoldingState()
    }
}
