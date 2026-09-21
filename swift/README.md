# HingeKit

The iOS half of Hinge: the iPhone Duo bridge plus a SwiftUI adapter.

## Adding it to an app

HingeKit is **source, not a package**. Swift export works with direct Xcode integration only —
not CocoaPods, and not through an SPM binaryTarget — so there is no `Package.swift` here; a
manifest would not resolve the `Hinge` module and could not even type-check these files.

1. Add a run-script build phase to the app target, ahead of Compile Sources:

   ```bash
   ./gradlew :hinge-core:embedSwiftExportForXcode
   ```

2. Add `Sources/HingeKit` to the app target.
3. Call `HingeKit.install()` once at launch, and `.foldAware()` at the root of your scene.

Requires **Xcode 27.1+** to build (`DuoHingeSource.swift` names the Duo APIs) and runs on
**iOS 18+** (every one of those references is behind `if #available(iOS 27.1, *)`).

## On the Objective-C fallback

`hinge-core` still assembles an XCFramework, and `FoldingStateWatcher` in the Kotlin core is
the callback-based API that path needs. But **these Swift sources will not compile against
it**: under the Objective-C exporter `Flow` is not an `AsyncSequence`, the top-level functions
become `SwiftInteropKt.unknownFoldingState()` rather than free functions, `PaneLayout` is a
protocol rather than an enum, and `DuoHingeSource` would have to subclass `NSObject`.

Falling back therefore means writing a second, small set of Swift sources against
`FoldingStateWatcher`. The Kotlin API is deliberately shaped so that is a contained job — no
function-type parameters, no companion-only factories, no value classes — but it is not free,
and nothing here does it for you. Budget for it if Swift export's Alpha status is a risk you
are not willing to carry.

## Files

| | |
|---|---|
| `HingeKit.swift` | `install()` / `uninstall()`, the one setup call |
| `DuoHingeSource.swift` | The only file touching iOS 27.1 Duo APIs. Read its header before upgrading SDKs. |
| `FoldingEnvironment.swift` | `FoldingModel`, `\.foldingState`, `.foldAware()` |
| `FoldAwarePanes.swift` | The two-pane view and `occlusionPadding(_:)` |
