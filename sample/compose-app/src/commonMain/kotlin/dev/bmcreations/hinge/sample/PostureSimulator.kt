package dev.bmcreations.hinge.sample

import dev.bmcreations.hinge.FoldSize
import dev.bmcreations.hinge.FoldingState
import dev.bmcreations.hinge.Postures

/**
 * The postures the demo can force, overriding whatever the hardware reports.
 *
 * This is the part of the sample worth stealing. Real postures need real hardware and a human
 * holding it, so without injection the only path anyone exercises is the flat one.
 */
enum class SimulatedPosture(val label: String) {
    Live("Live"),
    Flat("Flat"),
    Book("Book"),
    Tabletop("Tabletop"),
    Seamless("Seamless"),
    Occlusion("Occlusion"),
    InactiveFold("Inactive"),
    Cover("Cover"),
    Unknown("Unknown"),
    ;

    /**
     * The surface the demo should render into, or `null` to fill the window.
     *
     * [Cover] is the reason this exists. Handing the layout a small `windowSize` is not enough
     * on its own, because the UI adapters measure themselves and override it. To genuinely
     * simulate a cover display you have to shrink the surface the content is given.
     */
    val surfaceOverride: FoldSize?
        get() = if (this == Cover) FoldSize(Postures.COVER_WIDTH, Postures.COVER_HEIGHT) else null

    fun resolve(live: FoldingState, width: Float, height: Float): FoldingState = when (this) {
        Live -> live
        Flat -> Postures.flat(width, height)
        Book -> Postures.book(width, height)
        Tabletop -> Postures.tabletop(width, height)
        Seamless -> Postures.seamlessBook(width, height)
        Occlusion -> Postures.occlusionOnly(width, height)
        InactiveFold -> Postures.inactiveFold(width, height)
        Cover -> Postures.coverDisplay(width, height)
        Unknown -> Postures.unknown()
    }
}
