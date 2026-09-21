package dev.bmcreations.hinge.sample

/** Demo content. Deliberately boring so the layout is what you look at. */
data class Note(
    val id: Int,
    val title: String,
    val subtitle: String,
    val body: String,
)

val sampleNotes: List<Note> = listOf(
    Note(1, "Hinge occlusion", "Layout", "A region that hides pixels without dividing the window. Inset content away from it; do not split around it."),
    Note(2, "Book posture", "Postures", "Half-opened around a vertical hinge. Two panes, side by side. The hinge is the gutter."),
    Note(3, "Tabletop posture", "Postures", "Half-opened around a horizontal hinge. Panes stack: top and bottom. Common for media playback, with controls below the fold."),
    Note(4, "Seamless folds", "Hardware", "Gapless displays report a fold line with zero thickness. It still separates. Geometry code that discards empty rects quietly stops splitting here."),
    Note(5, "Cover displays", "Hardware", "A folded device may keep running on a much smaller surface. Minimum pane size is what stops two unusable slivers."),
    Note(6, "Window coordinates", "Pitfalls", "Platform fold geometry is window-relative. A component that is not full-window will split at a fold line nowhere near it unless you translate first."),
    Note(7, "Hinge angle", "Pitfalls", "An interaction signal, not a layout signal. Laying out from the angle gives you continuously reflowing UI mid-fold that disagrees with the system's own chrome."),
    Note(8, "Unknown is normal", "Pitfalls", "Every non-folding device reports Unknown forever. It is a renderable state, never a loading state."),
    Note(9, "Separating vs occluding", "Model", "Independent properties of one region, not two kinds of region. A hinge is routinely both."),
    Note(10, "Minimum pane size", "Tuning", "The single most load-bearing knob. Raise it instead of special-casing postures."),
    Note(11, "Split strategy", "Tuning", "FoldAware splits on a fold or on width. AlwaysSplit ignores posture. NeverSplit disables the behaviour from one call site."),
    Note(12, "Testing without hardware", "Practice", "Postures supplies synthetic states. Inject them and every branch becomes reachable on a simulator."),
)
