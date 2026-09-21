import SwiftUI
import ComposeApp

@main
struct iOSApp: App {

    init() {
        // The Compose UI is shared with Android, but posture is not: the Duo APIs are
        // Swift-only, so the bridge has to be registered from here before anything renders.
        HingeBridgeRegistry.shared.install(bridge: DuoHingeSource())
    }

    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea()
        }
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
