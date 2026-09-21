import Foundation

/// Demo content, identical to the Compose sample's so the two apps are directly comparable.
struct Note: Identifiable, Hashable {
    let id: Int
    let title: String
    let subtitle: String
    let body: String
}

let sampleNotes: [Note] = [
    Note(id: 1, title: "Hinge occlusion", subtitle: "Layout",
         body: "A region that hides pixels without dividing the window. Inset content away from it; do not split around it."),
    Note(id: 2, title: "Book posture", subtitle: "Postures",
         body: "Half-opened around a vertical hinge. Two panes, side by side. The hinge is the gutter."),
    Note(id: 3, title: "Tabletop posture", subtitle: "Postures",
         body: "Half-opened around a horizontal hinge. Panes stack: top and bottom. Common for media playback, with controls below the fold."),
    Note(id: 4, title: "Seamless folds", subtitle: "Hardware",
         body: "Gapless displays report a fold line with zero thickness. It still separates. Geometry code that discards empty rects quietly stops splitting here."),
    Note(id: 5, title: "Cover displays", subtitle: "Hardware",
         body: "A folded device may keep running on a much smaller surface. Minimum pane size is what stops two unusable slivers."),
    Note(id: 6, title: "Window coordinates", subtitle: "Pitfalls",
         body: "Platform fold geometry is window-relative. A component that is not full-window will split at a fold line nowhere near it unless you translate first."),
    Note(id: 7, title: "Hinge angle", subtitle: "Pitfalls",
         body: "An interaction signal, not a layout signal. Laying out from the angle gives you continuously reflowing UI mid-fold that disagrees with the system's own chrome."),
    Note(id: 8, title: "Unknown is normal", subtitle: "Pitfalls",
         body: "Every non-folding device reports Unknown forever. It is a renderable state, never a loading state."),
    Note(id: 9, title: "Separating vs occluding", subtitle: "Model",
         body: "Independent properties of one region, not two kinds of region. A hinge is routinely both."),
    Note(id: 10, title: "Minimum pane size", subtitle: "Tuning",
         body: "The single most load-bearing knob. Raise it instead of special-casing postures."),
    Note(id: 11, title: "Split strategy", subtitle: "Tuning",
         body: "FoldAware splits on a fold or on width. AlwaysSplit ignores posture. NeverSplit disables the behaviour from one call site."),
    Note(id: 12, title: "Testing without hardware", subtitle: "Practice",
         body: "Postures supplies synthetic states. Inject them and every branch becomes reachable on a simulator."),
]
