import SwiftUI
@preconcurrency import Hinge

@main
struct HingeDemoApp: App {

    init() {
        HingeKit.install()
    }

    var body: some Scene {
        WindowGroup {
            DemoRoot()
        }
    }
}

/// Root of the native SwiftUI sample. Mirrors `DemoApp.kt` in the Compose sample.
///
/// Deliberately does *not* use `.foldAware()`. That modifier publishes the live posture, and
/// this demo needs to override it with a simulated one, so it drives `FoldingModel` directly
/// and writes `\.foldingState` itself. A real app uses `.foldAware()` and never sees this.
struct DemoRoot: View {

    @State private var model = FoldingModel()
    @State private var simulation: SimulatedPosture = .live
    @State private var inspectorOpen = true
    @State private var surfaceSize: CGSize = .zero
    @State private var barHeight: CGFloat = 0
    @State private var inspectorHeight: CGFloat = 0

    private var resolved: FoldingState {
        simulation.resolve(
            live: model.state,
            width: Float(surfaceSize.width),
            height: Float(surfaceSize.height)
        )
    }

    var body: some View {
        // The controls OVERLAY the demo surface rather than stacking above and below it, and
        // this is load-bearing rather than cosmetic. Fold geometry is window-relative: if the
        // panes container did not start at the window origin, every simulated posture would
        // be built in one coordinate space and consumed in another, and
        // `FoldAwarePanes.inLocalSpace` would subtract an offset that was never added. A
        // horizontal fold would land off-centre and, near the minimum pane size, silently
        // stop splitting at all. Keeping the surface full-window makes it a no-op.
        DemoSurface(override: simulation.surfaceOverride) {
            NotesScreen(
                contentInsets: EdgeInsets(
                    top: simulation.surfaceOverride == nil ? barHeight : 0,
                    leading: 0,
                    bottom: simulation.surfaceOverride == nil ? inspectorHeight : 0,
                    trailing: 0
                )
            )
        }
        .onGeometryChange(for: CGSize.self) { proxy in
            proxy.size
        } action: { size in
            surfaceSize = size
        }
        .environment(\.foldingState, resolved)
        .overlay(alignment: .top) {
            SimulatorBar(selected: $simulation, inspectorOpen: $inspectorOpen)
                .onGeometryChange(for: CGFloat.self) { proxy in
                    proxy.size.height
                } action: { height in
                    barHeight = height
                }
        }
        .overlay(alignment: .bottom) {
            if inspectorOpen {
                PostureInspector(state: resolved, simulated: simulation != .live)
                    .onGeometryChange(for: CGFloat.self) { proxy in
                        proxy.size.height
                    } action: { height in
                        inspectorHeight = height
                    }
            }
        }
        .task { model.start() }
    }
}

/// The surface the demo content is given: the full window, or a small framed one for the
/// cover-display case.
///
/// The cover surface is centred rather than at the window origin, which would normally
/// reintroduce the coordinate mismatch described above. It does not, because a cover display
/// reports no fold regions at all — there is nothing to translate.
private struct DemoSurface<Content: View>: View {
    let override: CGSize?
    @ViewBuilder let content: () -> Content

    var body: some View {
        if let override {
            ZStack {
                Color(.secondarySystemBackground).ignoresSafeArea()
                content()
                    .frame(width: override.width, height: override.height)
                    .background(Color(.systemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .strokeBorder(Color.secondary, lineWidth: 2)
                    )
            }
        } else {
            content()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.systemBackground))
        }
    }
}

private struct SimulatorBar: View {
    @Binding var selected: SimulatedPosture
    @Binding var inspectorOpen: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Posture").font(.subheadline.weight(.medium))
                Spacer()
                Button(inspectorOpen ? "Hide inspector" : "Show inspector") {
                    inspectorOpen.toggle()
                }
                .font(.subheadline)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(SimulatedPosture.allCases) { posture in
                        Button {
                            selected = posture
                        } label: {
                            Text(posture.label)
                                .font(.footnote)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(
                                    Capsule().fill(
                                        posture == selected
                                            ? Color.accentColor.opacity(0.2)
                                            : Color(.tertiarySystemFill)
                                    )
                                )
                                .overlay(
                                    Capsule().strokeBorder(
                                        posture == selected ? Color.accentColor : .clear
                                    )
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(.regularMaterial)
    }
}
