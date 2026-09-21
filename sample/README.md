# Samples

Two apps, the same demo, so the platforms are directly comparable:

| | |
|---|---|
| `compose-app` | Compose Multiplatform. One UI, runs on Android and iOS. |
| `swift-app` | Native SwiftUI against HingeKit. |

Both show a list/detail screen laid out by `FoldAwarePanes`, a posture simulator, and an
inspector that prints what the SDK reported and what geometry it resolved to.

## The posture simulator is the point

Foldable layout is close to untestable otherwise. Real postures need real hardware and a human
holding the device, so without injection the only path anyone ever exercises is the flat one.
The chip row forces any of these on any device or simulator:

| | What it catches |
|---|---|
| Book | Side-by-side panes around a vertical hinge |
| Tabletop | Stacked panes around a horizontal hinge |
| Seamless | Zero-thickness fold line — geometry code that drops empty rects stops splitting here |
| Occlusion | Hides pixels without dividing; must **not** split, but must inset |
| Inactive | Reported but not in effect; must be ignored |
| Cover | Small surface; `minPaneSize` is what stops two unusable slivers |
| Unknown | What every non-folding device reports forever |

It is driven by `Postures` in `hinge-core`, which ships in the main source set precisely so
previews, screenshot tests and debug menus can all use it.

Note that `Cover` shrinks the actual surface rather than just reporting a small `windowSize`.
That is not cosmetic: the UI adapters measure themselves and override the reported size, so a
simulated small window that does not shrink the surface proves nothing.

For the same reason the simulator bar and inspector **overlay** the demo surface instead of
stacking above and below it. Fold geometry is window-relative, so a panes container that does
not start at the window origin would have its simulated regions built in one coordinate space
and consumed in another. A horizontal fold would land off-centre and, near the minimum pane
size, stop splitting altogether. If you copy this simulator into your own app, keep the
injected state in window coordinates.

## Running

### Android

```bash
./gradlew :sample:compose-app:installDebug
```

Then exercise it for real with the foldable emulators in Android Studio's device manager
(Pixel Fold, 7.6" Fold-in), which support posture changes from the emulator's extended
controls.

### iOS

Both iOS targets are declared as [XcodeGen](https://github.com/yonaskolb/XcodeGen) specs
rather than checked-in `.xcodeproj` files — the pbxproj format is long, order-sensitive and
merges badly, and a 30-line spec says the same thing.

```bash
brew install xcodegen

cd sample/swift-app        && xcodegen generate && open HingeSwiftDemo.xcodeproj
cd sample/compose-app/iosApp && xcodegen generate && open HingeComposeDemo.xcodeproj
```

Each project has a pre-build script that runs the matching Gradle task, so the Kotlin side
builds automatically. Requires Xcode 27.1+.

## Two bridges, one API

The two iOS apps reach the Kotlin core through different export modes, and that is worth
looking at:

- `swift-app` uses **Swift export**, via `swift/HingeKit`. `Flow` is an `AsyncSequence`,
  consumed with `for await`.
- `compose-app/iosApp` uses the **Objective-C exporter**, because that is what Compose
  Multiplatform's iOS integration produces. Its `DuoHingeSource.swift` is the second set of
  sources the main README warns you would need.

Compare the two `DuoHingeSource.swift` files. The differences are the import name, an
`NSObject` superclass, and nothing else — same protocol, same method names, same snapshot
type. That is what the Kotlin API's export-mode discipline buys: porting the bridge is a
rename, not a rewrite.

## Caveat

Neither app has been compiled. There is no Xcode or Maven access in the environment these
were written in, so treat every call site as reviewed-not-verified. The most likely breakages
are the Duo API signatures in both `DuoHingeSource.swift` files and how Swift export names
members of the sealed `PaneLayout` type.
