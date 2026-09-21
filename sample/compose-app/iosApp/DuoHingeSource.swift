import UIKit
@preconcurrency import ComposeApp

// =====================================================================================
//  The Objective-C-export flavour of the Duo bridge.
//
//  Compose Multiplatform's iOS integration produces an Objective-C framework, so this app
//  cannot use swift/HingeKit, which is written against Swift export. This file is what that
//  second, small set of sources actually looks like.
//
//  Compare it to swift/HingeKit/Sources/HingeKit/DuoHingeSource.swift. The differences are
//  exactly three, and none of them are in the Kotlin API:
//    1. `import ComposeApp` rather than `import Hinge` (the umbrella framework's name).
//    2. `NSObject` subclass, because Objective-C protocols require an Objective-C class.
//    3. Nothing else. Same protocol, same method names, same snapshot type.
//
//  That is the payoff of keeping function types, companion-only factories and value classes
//  out of the Kotlin API. Porting the bridge is a rename, not a rewrite.
//
//  The Duo API notes from the HingeKit copy apply here verbatim. Verify against your SDK:
//    - UIHingeInteraction's initializer label
//    - UIHinge.status case names, UIHinge.angle units (assumed radians)
//    - UIView.reservedRegions(kind:options:) and ReservedRegion.frame/.isActive/.identifier
// =====================================================================================

/// Mirrors `HingeStatus` in the Kotlin core. Keep the numbers in sync.
private enum SnapshotStatus: Int32 {
    case unknown = 0
    case closed = 1
    case partiallyOpen = 2
    case fullyOpen = 3
}

@MainActor
final class DuoHingeSource: NSObject {

    private var listener: HingeSnapshotListener?
    private var interaction: UIInteraction?
    private var notificationTokens: [NSObjectProtocol] = []

    fileprivate func begin(listener: HingeSnapshotListener) {
        teardown()
        self.listener = listener
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
        listener = nil
    }

    private func attachIfPossible() {
        guard #available(iOS 27.1, *), let window = Self.keyWindow() else { return }
        let hingeInteraction = UIHingeInteraction { [weak self] context in
            self?.publish(hinge: context.hinge)
        }
        window.addInteraction(hingeInteraction)
        interaction = hingeInteraction
    }

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

    private func publish(hinge: Any?) {
        guard let listener else { return }
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

        listener.onSnapshot(
            snapshot: HingeSnapshot(
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
            absorb(region.frame, active: region.isActive, identifier: region.identifier, separating: true)
        }
        for region in view.reservedRegions(kind: .occlusion, options: [.includeInactive]) {
            absorb(region.frame, active: region.isActive, identifier: region.identifier, separating: false)
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

    private static func keyWindow() -> UIWindow? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let foreground = scenes.filter { $0.activationState == .foregroundActive }
        let windows = (foreground.isEmpty ? scenes : foreground).flatMap(\.windows)
        return windows.first { $0.isKeyWindow } ?? windows.first
    }
}

extension DuoHingeSource: HingeBridge {

    nonisolated func startObserving(listener: HingeSnapshotListener) {
        MainActor.assumeIsolated { self.begin(listener: listener) }
    }

    nonisolated func stopObserving() {
        MainActor.assumeIsolated { self.teardown() }
    }
}
