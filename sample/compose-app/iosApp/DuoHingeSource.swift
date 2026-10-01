import UIKit
@preconcurrency import ComposeApp

// =====================================================================================
//  THE ONLY FILE IN THIS REPOSITORY THAT TOUCHES THE iPHONE DUO APIs.
//
//  Everything downstream — the shared Kotlin model, the pane math, the Compose UI — is
//  written against `HingeSnapshot`, which is made of Doubles and Bools. If Apple renames
//  a case or changes a signature between betas, the fix is confined to this file and
//  nothing else moves.
//
//  Checked against the iOS 27.1 SDK (Xcode 27.1 beta, 27A9269):
//    - UIHingeInteraction's handler takes (interaction, update); the hinge is `update.hinge`
//    - UIHinge.status cases are .unknown/.closed/.partiallyOpen/.fullyOpen
//    - UIHinge.angle is in radians
//    - ReservedRegion exposes frame/margins/isActive/kind and an opaque `id`, not `identifier`
//
//  `@preconcurrency import` is deliberate: Kotlin-exported types and the block that
//  `startObserving` takes carry no Sendable conformances, and this file hands them across
//  the nonisolated protocol boundary below.
// =====================================================================================

/// Mirrors `HingeStatus` in the Kotlin core. Kotlin `Int` arrives as `Int32`, hence the raw
/// value type. Keep the numbers in sync with HingeBridge.kt.
private enum SnapshotStatus: Int32 {
    case unknown = 0
    case closed = 1
    case partiallyOpen = 2
    case fullyOpen = 3
}

/// Observes the hardware and pushes readings into the Kotlin core.
///
/// Main-actor isolated, because every line of it touches UIKit. The two `HingeBridge`
/// requirements are `nonisolated` shims, since the Kotlin protocol carries no isolation:
/// Kotlin calls them from the main thread by contract, and `MainActor.assumeIsolated` states
/// that as a checked precondition rather than an assumption.
@MainActor
final class DuoHingeSource: NSObject {

    private var emit: ((HingeSnapshot) -> Void)?
    private var interaction: UIInteraction?
    private var notificationTokens: [NSObjectProtocol] = []
    private var geometryObservation: NSKeyValueObservation?
    /// The last `UIHinge` the interaction delivered. Geometry-only republishes reuse it so they
    /// never reset the posture.
    private var lastHinge: UIHinge?

    // MARK: - Lifecycle

    fileprivate func begin(emit: @escaping (HingeSnapshot) -> Void) {
        // Idempotent: a second start without an intervening stop would otherwise add a
        // second interaction to the window and leak the previous notification observers.
        teardown()
        self.emit = emit
        observeActivation()
        attachIfPossible()
        publish(hinge: nil)
    }

    fileprivate func teardown() {
        if let interaction, let view = interaction.view {
            view.removeInteraction(interaction)
        }
        interaction = nil
        notificationTokens.forEach { NotificationCenter.default.removeObserver($0) }
        notificationTokens.removeAll()
        geometryObservation?.invalidate()
        geometryObservation = nil
        lastHinge = nil
        emit = nil
    }

    // MARK: - Observation

    /// Attaches the hinge interaction and the geometry observation to the key window. Either
    /// step is skipped when it is already done, so this is safe to call again: observation can
    /// start before the app has a window, and then the next activation finishes the job.
    private func attachIfPossible() {
        guard let window = Self.keyWindow() else { return }
        if geometryObservation == nil {
            observeGeometry(of: window)
        }
        guard interaction == nil else { return }
        // The handler also fires with a nil `hinge` when the interaction leaves a hierarchy
        // that provides hinge updates; `publish` treats that as unknown.
        let hingeInteraction = UIHingeInteraction { [weak self] _, update in
            self?.lastHinge = update.hinge
            self?.publish(hinge: update.hinge)
        }
        window.addInteraction(hingeInteraction)
        interaction = hingeInteraction
    }

    /// Republishes on every activation, and retries the attach in case observation began before
    /// there was a window to attach to.
    private func observeActivation() {
        let token = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated {
                self?.attachIfPossible()
                self?.republishGeometry()
            }
        }
        notificationTokens = [token]
    }

    /// The window can resize without the hinge moving: rotation, or a size change in a
    /// multi-window scene. The hinge interaction does not report those, so without a second
    /// trigger the snapshot keeps the old window size and regions, and window-level readers
    /// such as `rememberPaneLayout()` disagree with what is on screen.
    ///
    /// `effectiveGeometry` is KVO-observable and changes on rotation and on scene resize. The
    /// publish is deferred one turn of the main queue so the window has laid out at its new
    /// size before the reserved regions are read.
    ///
    /// Note this deliberately does *not* observe `UIDevice.orientationDidChangeNotification`:
    /// UIKit only posts that while `beginGeneratingDeviceOrientationNotifications()` is
    /// active, so an observer for it would silently never fire.
    private func observeGeometry(of window: UIWindow) {
        geometryObservation = window.windowScene?.observe(
            \.effectiveGeometry,
            options: [.new]
        ) { [weak self] _, _ in
            DispatchQueue.main.async { self?.republishGeometry() }
        }
    }

    private func republishGeometry() {
        publish(hinge: lastHinge)
    }

    // MARK: - Publishing

    /// Builds and emits a snapshot.
    private func publish(hinge: UIHinge?) {
        guard let emit else { return }
        let window = Self.keyWindow()
        let size = window?.bounds.size ?? .zero

        let status = hinge.map(Self.status(of:)) ?? .unknown
        let angle = hinge.map { Double($0.angle) } ?? -1.0
        let regions = window.map(Self.reservedRegions(in:)) ?? []

        emit(
            HingeSnapshot(
                status: status.rawValue,
                angleRadians: angle,
                windowWidth: Double(size.width),
                windowHeight: Double(size.height),
                regions: regions
            )
        )
    }

    private static func status(of hinge: UIHinge) -> SnapshotStatus {
        switch hinge.status {
        case .closed: return .closed
        case .partiallyOpen: return .partiallyOpen
        case .fullyOpen: return .fullyOpen
        default: return .unknown
        }
    }

    /// Reads both region kinds and merges them, because a single physical region is routinely
    /// reported as both a division and an occlusion, and the shared model treats those as two
    /// independent properties of one region rather than as two regions.
    private static func reservedRegions(in view: UIView) -> [HingeSnapshotRegion] {
        var merged: [String: MutableRegion] = [:]

        func absorb(_ frame: CGRect, active: Bool, identifier: String?, separating: Bool) {
            let key = "\(frame.minX),\(frame.minY),\(frame.maxX),\(frame.maxY)"
            var entry = merged[key] ?? MutableRegion(
                frame: frame,
                isActive: active,
                identifier: identifier
            )
            if separating { entry.isSeparating = true } else { entry.isOccluding = true }
            entry.isActive = entry.isActive || active
            merged[key] = entry
        }

        for region in view.reservedRegions(kind: .division, options: [.includeInactive]) {
            absorb(
                region.frame,
                active: region.isActive,
                identifier: String(describing: region.id),
                separating: true
            )
        }
        for region in view.reservedRegions(kind: .occlusion, options: [.includeInactive]) {
            absorb(
                region.frame,
                active: region.isActive,
                identifier: String(describing: region.id),
                separating: false
            )
        }

        return merged.values.map { region in
            HingeSnapshotRegion(
                left: Double(region.frame.minX),
                top: Double(region.frame.minY),
                right: Double(region.frame.maxX),
                bottom: Double(region.frame.maxY),
                isSeparating: region.isSeparating,
                isOccluding: region.isOccluding,
                isActive: region.isActive,
                identifier: region.identifier
            )
        }
    }

    private struct MutableRegion {
        let frame: CGRect
        var isActive: Bool
        let identifier: String?
        var isSeparating = false
        var isOccluding = false
    }

    /// The key window of the foreground-active scene, falling back to any window at all so
    /// that a snapshot taken during launch still carries a usable size.
    private static func keyWindow() -> UIWindow? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let foreground = scenes.filter { $0.activationState == .foregroundActive }
        let windows = (foreground.isEmpty ? scenes : foreground).flatMap(\.windows)
        return windows.first { $0.isKeyWindow } ?? windows.first
    }
}

extension DuoHingeSource: HingeBridge {

    nonisolated func startObserving(onSnapshot: @escaping (HingeSnapshot) -> Void) {
        // Kotlin's block carries no Sendable conformance. Compose calls this on the main
        // thread, and `assumeIsolated` traps if that ever stops being true, so the block
        // never actually crosses an isolation boundary.
        nonisolated(unsafe) let emit = onSnapshot
        MainActor.assumeIsolated { self.begin(emit: emit) }
    }

    nonisolated func stopObserving() {
        MainActor.assumeIsolated { self.teardown() }
    }
}
