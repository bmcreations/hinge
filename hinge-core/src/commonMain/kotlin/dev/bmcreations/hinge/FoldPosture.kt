package dev.bmcreations.hinge

/**
 * The physical configuration of the device, normalized across platforms.
 *
 * This is the value you branch layout on. It is deliberately coarse: it carries only the
 * distinctions that should change a layout, and none of the continuous signal that shouldn't.
 * For the continuous signal, see [HingeAngle] and read its warning first.
 */
public enum class FoldPosture {
    /**
     * Posture is not yet known, or the host is not a foldable.
     *
     * Treat this exactly like [Flat]: it is the state every non-folding phone, tablet and
     * desktop reports forever, and the state a foldable reports for the first frame or two
     * before the platform delivers its first update. Layout must never wait on it.
     */
    Unknown,

    /**
     * Fully open, or a device that does not fold. One continuous display surface.
     *
     * Note that a flat foldable may still publish an *occluding* region (a hinge seam or a
     * camera cutout crossing otherwise-continuous content) — see [FoldingState.occludingRegions].
     * Flat means "do not split", not "ignore geometry".
     */
    Flat,

    /** Half-opened around a vertical hinge: two panes side by side, like a book. */
    Book,

    /** Half-opened around a horizontal hinge: two panes stacked, like a laptop on a table. */
    Tabletop,

    /**
     * Folded shut. On devices with a cover display the app may still be visible and running
     * on a much smaller surface; on devices without one the app is not visible at all.
     *
     * Do not assume this means "backgrounded" — drive that from normal lifecycle callbacks.
     */
    Closed,
    ;

    /** True for [Book] and [Tabletop], the two postures where the device is bent. */
    public val isHalfOpened: Boolean
        get() = this == Book || this == Tabletop

    /** The hinge axis implied by the posture, or `null` when the device is not bent. */
    public val axis: FoldAxis?
        get() = when (this) {
            Book -> FoldAxis.Vertical
            Tabletop -> FoldAxis.Horizontal
            else -> null
        }
}
