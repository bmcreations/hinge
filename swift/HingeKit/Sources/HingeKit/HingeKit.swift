import Foundation
@preconcurrency import Hinge

/// Entry point for the iOS side of the library.
///
/// The shared Kotlin core cannot reach the iPhone Duo APIs on its own, so Swift observes the
/// hardware and hands readings across. Registering that observer is the one setup step.
///
/// ```swift
/// @main
/// struct MyApp: App {
///     init() { HingeKit.install() }
///     var body: some Scene { WindowGroup { RootView().foldAware() } }
/// }
/// ```
///
/// Safe to call on any iOS version and on any device. On anything that is not a foldable
/// running iOS 27.1 or later, the installed source reports an unknown posture forever, which
/// every layout in this library already renders as a single pane.
public enum HingeKit {

    /// Registers the platform posture source with the shared Kotlin core.
    ///
    /// Idempotent. Call it once, early — the `App` initializer or
    /// `application(_:didFinishLaunchingWithOptions:)`. Calling it late is not an error, but
    /// the first frames render as a single pane until it runs.
    public static func install() {
        guard !HingeBridgeRegistry.shared.isInstalled else { return }
        HingeBridgeRegistry.shared.install(bridge: DuoHingeSource())
    }

    /// Removes the registered source. Intended for tests.
    public static func uninstall() {
        HingeBridgeRegistry.shared.uninstall()
    }

    /// Whether this OS version can report a fold at all.
    ///
    /// Use it for diagnostics and settings screens, not for layout — layout should follow
    /// posture, which is already correct on devices that report nothing.
    public static var isFoldableCapable: Bool {
        if #available(iOS 27.1, *) { return true }
        return false
    }
}
