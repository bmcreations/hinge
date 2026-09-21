import SwiftUI
@preconcurrency import Hinge

/// Observes fold posture and republishes it to SwiftUI.
///
/// Most apps never touch this directly — ``SwiftUICore/View/foldAware()`` creates and drives
/// one. Use it explicitly only if you need the posture outside a view hierarchy, for example
/// in a model object that decides navigation behaviour.
@MainActor
@Observable
public final class FoldingModel {

    /// The current posture. Starts unknown, which renders as a single pane.
    public private(set) var state: FoldingState = unknownFoldingState()

    @ObservationIgnored private var observation: Task<Void, Never>?

    public init() {}

    /// Begins observing. Installs the platform source if that has not happened yet.
    ///
    /// Swift export exposes the Kotlin `Flow` as an `AsyncSequence`, so this is a plain
    /// `for await` loop rather than a callback bridge.
    public func start() {
        // Idempotent on purpose. `.task {}` re-fires whenever the view reappears, and
        // cancelling a Kotlin flow collection is asynchronous -- the old collection's
        // `awaitClose` can land *after* a restart and stop the new one. Starting once and
        // letting `stop()` be the only teardown removes that race entirely.
        guard observation == nil else { return }
        HingeKit.install()
        observation = Task { @MainActor [weak self] in
            // `try` covers the case where the generated AsyncSequence has a throwing
            // Failure type; if it is Never this is simply redundant.
            do {
                for try await next in foldingStateFlow() {
                    guard !Task.isCancelled else { return }
                    self?.state = next
                }
            } catch {
                // A terminated posture stream leaves the last good value in place, which
                // renders as a single pane. Nothing here is worth crashing over.
            }
        }
    }

    /// Stops observing and tears down the platform observer.
    public func stop() {
        observation?.cancel()
        observation = nil
    }

    deinit {
        observation?.cancel()
    }
}

private struct FoldingStateKey: EnvironmentKey {
    // FoldingState is deeply immutable and this default is never mutated, so the annotation
    // is an accurate statement rather than a waiver.
    nonisolated(unsafe) static let defaultValue: FoldingState = unknownFoldingState()
}

public extension EnvironmentValues {
    /// The ambient fold posture. Unknown unless an ancestor applied ``View/foldAware()``.
    var foldingState: FoldingState {
        get { self[FoldingStateKey.self] }
        set { self[FoldingStateKey.self] = newValue }
    }
}

public extension View {
    /// Observes fold posture and publishes it to this subtree's environment.
    ///
    /// Apply once, at the root of your scene:
    ///
    /// ```swift
    /// WindowGroup {
    ///     RootView().foldAware()
    /// }
    /// ```
    ///
    /// Everything below can then read `@Environment(\.foldingState)`, and ``FoldAwarePanes``
    /// picks it up with no wiring at all.
    /// `@MainActor` because `FoldAwareModifier` stores a `FoldingModel`, whose initializer
    /// is main-actor-isolated. Without it this is a call to an isolated init from a
    /// synchronous nonisolated context, which Swift 6 rejects.
    @MainActor
    func foldAware() -> some View {
        modifier(FoldAwareModifier())
    }
}

private struct FoldAwareModifier: ViewModifier {
    @State private var model = FoldingModel()

    func body(content: Content) -> some View {
        content
            .environment(\.foldingState, model.state)
            .task { model.start() }
            .onDisappear { model.stop() }
    }
}

public extension SplitSpec {
    /// The defaults from the Kotlin core.
    ///
    /// Kotlin default arguments are not exported, so `SplitSpec()` is not callable from Swift
    /// and every parameter would otherwise have to be restated here — and drift. This forwards
    /// to the Kotlin factory so there is exactly one set of defaults.
    static var standard: SplitSpec { defaultSplitSpec() }
}

public extension FoldingState {
    /// Pane geometry for this state, using ``SplitSpec/standard`` unless told otherwise.
    func panes(_ spec: SplitSpec = .standard) -> PaneLayout {
        resolvePaneLayout(spec: spec)
    }
}
