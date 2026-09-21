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
//  Verify against the SDK you are building with:
//    - UIHingeInteraction's initializer label (assumed: a single trailing update handler)
//    - UIHinge.status case names (assumed: .unknown/.closed/.partiallyOpen/.fullyOpen)
//    - UIHinge.angle units (assumed: radians)
//    - UIView.reservedRegions(kind:options:) and ReservedRegion.frame/.isActive/.identifier
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

    // MARK: - Lifecycle

    fileprivate func begin(emit: @escaping (HingeSnapshot) -> Void) {
        // Idempotent: a second start without an intervening stop would otherwise add a
        // second interaction to the window and leak the previous notification observers.
        teardown()
        self.emit = emit
        attachIfPossible()
        observeGeometryChanges()
        publish(hinge: nil)
    }

    fileprivate func teardown() {
        if let interaction, let view = interaction.view {
            view.removeInteraction(interaction)
        }
        interaction = nil
        notificationTokens.forEach { NotificationCenter.default.removeObserver($0) }
        notificationTokens.removeAll()
        emit = nil
    }

    // MARK: - Observation

    private func attachIfPossible() {
        guard #available(iOS 27.1, *), let window = Self.keyWindow() else { return }
        let hingeInteraction = UIHingeInteraction { [weak self] context in
            self?.publish(hinge: context.hinge)
        }
        window.addInteraction(hingeInteraction)
        interaction = hingeInteraction
    }

    /// The window can resize without the hinge moving. The snapshot's window size is only a
    /// fallback — `FoldAwarePanes` measures itself and overrides it — but keeping it fresh
    /// avoids a stale first frame.
    ///
    /// Note this deliberately does *not* observe `UIDevice.orientationDidChangeNotification`:
    /// UIKit only posts that while `beginGeneratingDeviceOrientationNotifications()` is
    /// active, so an observer for it would silently never fire.
    private func observeGeometryChanges() {
        let token = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.publish(hinge: nil) }
        }
        notificationTokens = [token]
    }

    // MARK: - Publishing

    /// Builds and emits a snapshot. `hinge` is `Any?` so that the call sites above, which are
    /// not inside an availability check, never have to name an iOS 27.1 type.
    private func publish(hinge: Any?) {
        guard let emit else { return }
        let window = Self.keyWindow()
        let size = window?.bounds.size ?? .zero

        var status = SnapshotStatus.unknown
        var angle = -1.0
        var regions: [HingeSnapshotRegion] = []

        if #available(iOS 27.1, *) {
            if let hinge = hinge as? UIHinge {
                status = Self.status(of: hinge)
                angle = Double(hinge.angle)
            }
            if let window {
                regions = Self.reservedRegions(in: window)
            }
        }

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

    @available(iOS 27.1, *)
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
    @available(iOS 27.1, *)
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
                identifier: region.identifier,
                separating: true
            )
        }
        for region in view.reservedRegions(kind: .occlusion, options: [.includeInactive]) {
            absorb(
                region.frame,
                active: region.isActive,
                identifier: region.identifier,
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
        MainActor.assumeIsolated { self.begin(emit: onSnapshot) }
    }

    nonisolated func stopObserving() {
        MainActor.assumeIsolated { self.teardown() }
    }
}
