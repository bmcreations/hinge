package dev.bmcreations.hinge

import kotlin.math.max
import kotlin.math.min

/**
 * All geometry in this library is expressed in **logical points**: density-independent
 * pixels (`dp`) on Android and UIKit points on iOS. The two units are close enough to be
 * interchangeable for layout purposes, so a single scalar type keeps the shared model honest.
 *
 * Platform sources are responsible for converting raw physical pixels into this unit before
 * constructing a [FoldingState]. Never put physical pixels into these types.
 */
public data class FoldSize(
    val width: Float,
    val height: Float,
) {
    public val isEmpty: Boolean get() = width <= 0f || height <= 0f

    public companion object {
        public val Zero: FoldSize = FoldSize(0f, 0f)
    }
}

/**
 * An axis-aligned rectangle in logical points, relative to whatever origin the enclosing
 * [FoldingState] declares (the window by default; a composable's own bounds after
 * [FoldingState.inLocalSpace]).
 */
public data class FoldRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    public val width: Float get() = right - left
    public val height: Float get() = bottom - top

    /**
     * True when the rectangle has no area. Exactly `!isSubstantial`.
     *
     * Note a zero-thickness fold line is empty by this definition and still meaningful —
     * [paneLayout] splits around it. Do not use emptiness to decide whether a fold matters.
     */
    public val isEmpty: Boolean get() = width <= 0f || height <= 0f

    /** True when the rectangle has area. A fold line is typically *not* substantial. */
    public val isSubstantial: Boolean get() = width > 0f && height > 0f

    /**
     * The axis the rectangle runs along. A hinge that separates the display into left and
     * right panes is a tall, thin rectangle and reports [FoldAxis.Vertical].
     *
     * Ties (a perfect square, or a degenerate point) resolve to [FoldAxis.Vertical] because
     * both shipping foldables in this class fold along a vertical hinge in portrait.
     */
    public val axis: FoldAxis
        get() = if (height >= width) FoldAxis.Vertical else FoldAxis.Horizontal

    public fun translate(dx: Float, dy: Float): FoldRect =
        FoldRect(left + dx, top + dy, right + dx, bottom + dy)

    /**
     * Intersection with [other], or `null` when the two do not touch.
     *
     * Degenerate results are preserved rather than discarded: a zero-thickness fold line that
     * lands exactly on the boundary of [other] is a real, meaningful overlap.
     */
    public fun intersect(other: FoldRect): FoldRect? {
        val l = max(left, other.left)
        val t = max(top, other.top)
        val r = min(right, other.right)
        val b = min(bottom, other.bottom)
        return if (l > r || t > b) null else FoldRect(l, t, r, b)
    }

    public fun overlaps(other: FoldRect): Boolean = intersect(other) != null

    public companion object {
        public val Zero: FoldRect = FoldRect(0f, 0f, 0f, 0f)

        public fun ofSize(size: FoldSize): FoldRect =
            FoldRect(0f, 0f, size.width, size.height)
    }
}
