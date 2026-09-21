package dev.bmcreations.hinge

/**
 * Synthetic [FoldingState] values for previews, screenshot tests and posture simulators.
 *
 * Foldable layout is close to untestable without this. Real postures need real hardware, and
 * the half-opened states need a human holding the device, so without a way to inject a posture
 * the only code path anyone ever exercises is the flat one. Every case in this object is a
 * configuration that has broken a real app.
 *
 * These live in the main source set rather than a test fixture on purpose: Compose previews
 * and the sample apps' posture simulator need them at normal compile time. They are pure data
 * and cost nothing at runtime if unused.
 *
 * Each posture has an explicit form taking every dimension, and a convenience overload using
 * the typical values below.
 *
 * ```
 * ProvideFoldingState(Postures.book(1280f, 900f)) { App() }
 * ```
 */
public object Postures {

    /** The default hinge thickness used by the convenience factories, in logical points. */
    public const val DEFAULT_HINGE_THICKNESS: Float = 16f

    /** A typical intrusion for [occlusionOnly], in logical points. */
    public const val DEFAULT_OCCLUSION_THICKNESS: Float = 24f

    /** Typical cover-display width, in logical points. */
    public const val COVER_WIDTH: Float = 260f

    /** Typical cover-display height, in logical points. */
    public const val COVER_HEIGHT: Float = 320f

    /** Posture is not yet known. What every non-foldable reports, and the first frame anywhere. */
    public fun unknown(): FoldingState = FoldingState()

    /** One continuous surface, no fold. A phone, a tablet, an unfolded device lying flat. */
    public fun flat(width: Float, height: Float): FoldingState =
        FoldingState(
            posture = FoldPosture.Flat,
            windowSize = FoldSize(width, height),
        )

    /**
     * Half-opened around a vertical hinge: panes left and right.
     *
     * The hinge is centred, which is where it is on every shipping device in this class.
     */
    public fun book(width: Float, height: Float, hingeThickness: Float): FoldingState {
        val half = hingeThickness / 2f
        val centre = width / 2f
        return FoldingState(
            posture = FoldPosture.Book,
            windowSize = FoldSize(width, height),
            regions = listOf(
                FoldRegion(
                    bounds = FoldRect(centre - half, 0f, centre + half, height),
                    isSeparating = true,
                    isOccluding = hingeThickness > 0f,
                    isActive = true,
                    identifier = "synthetic.book",
                ),
            ),
        )
    }

    /** [book] with [DEFAULT_HINGE_THICKNESS]. */
    public fun book(width: Float, height: Float): FoldingState =
        book(width, height, DEFAULT_HINGE_THICKNESS)

    /** Half-opened around a horizontal hinge: panes top and bottom, like a laptop. */
    public fun tabletop(width: Float, height: Float, hingeThickness: Float): FoldingState {
        val half = hingeThickness / 2f
        val centre = height / 2f
        return FoldingState(
            posture = FoldPosture.Tabletop,
            windowSize = FoldSize(width, height),
            regions = listOf(
                FoldRegion(
                    bounds = FoldRect(0f, centre - half, width, centre + half),
                    isSeparating = true,
                    isOccluding = hingeThickness > 0f,
                    isActive = true,
                    identifier = "synthetic.tabletop",
                ),
            ),
        )
    }

    /** [tabletop] with [DEFAULT_HINGE_THICKNESS]. */
    public fun tabletop(width: Float, height: Float): FoldingState =
        tabletop(width, height, DEFAULT_HINGE_THICKNESS)

    /**
     * A gapless foldable: the display is continuous, but a zero-thickness fold line still
     * divides it into two logical panes.
     *
     * Worth testing separately because a zero-area region is exactly what naive geometry code
     * discards, after which the layout silently stops splitting.
     */
    public fun seamlessBook(width: Float, height: Float): FoldingState =
        book(width, height, 0f).copy(posture = FoldPosture.Book)

    /**
     * Folded shut, with the app running on a small cover display.
     *
     * The interesting case is not the posture but the size: any pane logic that ignores
     * [SplitSpec.minPaneSize] produces two unusable slivers here.
     */
    public fun coverDisplay(width: Float, height: Float): FoldingState =
        FoldingState(
            posture = FoldPosture.Closed,
            windowSize = FoldSize(width, height),
        )

    /**
     * Flat, but with a region that hides pixels without dividing the window: a camera housing
     * or a seam crossing otherwise-continuous content.
     *
     * This must not produce a split. Content should be inset away from it instead, which is
     * what `rememberOcclusionPadding` and `occlusionPadding(_:)` are for.
     */
    public fun occlusionOnly(width: Float, height: Float, thickness: Float): FoldingState =
        FoldingState(
            posture = FoldPosture.Flat,
            windowSize = FoldSize(width, height),
            regions = listOf(
                FoldRegion(
                    bounds = FoldRect(0f, 0f, thickness, height),
                    isSeparating = false,
                    isOccluding = true,
                    isActive = true,
                    identifier = "synthetic.occlusion",
                ),
            ),
        )

    /** [occlusionOnly] with [DEFAULT_OCCLUSION_THICKNESS] intruding from the leading edge. */
    public fun occlusionOnly(width: Float, height: Float): FoldingState =
        occlusionOnly(width, height, DEFAULT_OCCLUSION_THICKNESS)

    /** [coverDisplay] at [COVER_WIDTH] x [COVER_HEIGHT]. */
    public fun coverDisplay(): FoldingState = coverDisplay(COVER_WIDTH, COVER_HEIGHT)

    /**
     * A fold that is reported but not currently in effect.
     *
     * Layout must ignore it. Included because "reported" and "active" are easy to conflate,
     * and doing so makes a flat device render as two panes.
     */
    public fun inactiveFold(width: Float, height: Float): FoldingState {
        val centre = width / 2f
        return FoldingState(
            posture = FoldPosture.Flat,
            windowSize = FoldSize(width, height),
            regions = listOf(
                FoldRegion(
                    bounds = FoldRect(centre - 8f, 0f, centre + 8f, height),
                    isSeparating = true,
                    isOccluding = true,
                    isActive = false,
                    identifier = "synthetic.inactive",
                ),
            ),
        )
    }
}
