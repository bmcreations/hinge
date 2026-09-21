package dev.bmcreations.hinge

/**
 * The direction a fold runs.
 *
 * The naming describes the **hinge line**, not the split direction, which is the convention
 * `androidx.window` uses. It is the more common source of confusion in foldable code, so read
 * the doc on each case rather than the name alone.
 */
public enum class FoldAxis {
    /**
     * The hinge runs top to bottom. Content is divided into a **left and right** pair of panes.
     * Corresponds to `FoldingFeature.Orientation.VERTICAL` on Android, and to a tall, thin
     * reserved region on iOS.
     */
    Vertical,

    /**
     * The hinge runs left to right. Content is divided into a **top and bottom** pair of panes.
     * Corresponds to `FoldingFeature.Orientation.HORIZONTAL` on Android, and to a wide, short
     * reserved region on iOS.
     */
    Horizontal,
}
