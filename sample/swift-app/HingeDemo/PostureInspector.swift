import Foundation
import SwiftUI
@preconcurrency import Hinge

/// Shows exactly what the SDK reports and what it resolved to.
///
/// Useful beyond the demo: drop it behind a debug flag in a real app and the next foldable bug
/// report arrives with the posture, the regions and the pane rects already in it.
struct PostureInspector: View {
    let state: FoldingState
    let simulated: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("Inspector").font(.subheadline.weight(.medium))
                    Spacer()
                    if simulated {
                        Text("SIMULATED")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(.red)
                    }
                }

                Divider()

                field("posture", String(describing: state.posture))
                field("foldAxis", state.foldAxis.map { String(describing: $0) } ?? "none")
                field("window", "\(r(state.windowSize.width)) x \(r(state.windowSize.height))")
                field("hingeAngle", state.hingeAngle.map { "\(r($0.degrees))°" } ?? "unavailable")
                field("separated", String(state.isSeparated))

                Divider()

                if state.regions.isEmpty {
                    field("regions", "none")
                } else {
                    ForEach(Array(state.regions.enumerated()), id: \.offset) { index, region in
                        field("region[\(index)]", "\(pretty(region.bounds))  \(flags(region))")
                    }
                }

                Divider()

                if let split = state.panes().splitOrNull() {
                    field("layout", "Split (\(String(describing: split.axis)))")
                    field("  primary", pretty(split.primary))
                    field("  secondary", pretty(split.secondary))
                    field("  gutter", pretty(split.gutter))
                } else {
                    // Deliberately not reading `.bounds` off the sealed PaneLayout: a
                    // single pane always fills the surface, and this avoids depending on how
                    // Swift export exposes members of a sealed type.
                    let w = state.windowSize.width
                    let h = state.windowSize.height
                    field("layout", "Single [0.0, 0.0, \(r(w)), \(r(h))]")
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(maxHeight: 220)
        .background(.regularMaterial)
    }

    private func field(_ name: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(name)
                .font(.caption.monospaced())
                .foregroundStyle(.secondary)
            Text(value)
                .font(.caption.monospaced())
                .lineLimit(1)
            Spacer(minLength: 0)
        }
    }

    private func flags(_ region: FoldRegion) -> String {
        var parts: [String] = []
        if region.isSeparating { parts.append("separating") }
        if region.isOccluding { parts.append("occluding") }
        if !region.isActive { parts.append("inactive") }
        return parts.isEmpty ? "none" : parts.joined(separator: ",")
    }

    private func pretty(_ rect: FoldRect) -> String {
        "[\(r(rect.left)), \(r(rect.top)), \(r(rect.right)), \(r(rect.bottom))]"
    }

    private func r(_ value: Float) -> String {
        String(format: "%.1f", value)
    }
}
