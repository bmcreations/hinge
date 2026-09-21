# Sample

`compose-app` is a Compose Multiplatform demo: one UI, running on Android and iOS. It shows a
list/detail screen laid out by `FoldAwarePanes`, a posture simulator, and an inspector that
prints what the SDK reported and what geometry it resolved to.

## The posture simulator is the point

Foldable layout is close to untestable otherwise. Real postures need real hardware and a human
holding the device, so without injection the only path anyone ever exercises is the flat one.
The chip row forces any of these on any device or emulator:

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

Two details in the demo are load-bearing rather than cosmetic, and worth copying if you build
your own simulator:

**`Cover` shrinks the actual surface**, rather than just reporting a small `windowSize`. The UI
adapters measure themselves and override the reported size, so a simulated small window that
does not shrink the surface proves nothing.

**The simulator bar and inspector overlay the demo surface** instead of stacking above and
below it. Fold geometry is window-relative, so a panes container that did not start at the
window origin would have its injected regions built in one coordinate space and consumed in
another. A horizontal fold would land off-centre and, near the minimum pane size, stop
splitting altogether. Keep injected state in window coordinates.

## Running

### Android

```bash
./gradlew :sample:compose-app:installDebug
```

Then exercise it for real with the foldable emulators in Android Studio's device manager
(Pixel Fold, 7.6" Fold-in), which support posture changes from the extended controls.

### iOS

The iOS target is an [XcodeGen](https://github.com/yonaskolb/XcodeGen) spec rather than a
checked-in `.xcodeproj` — the pbxproj format is long, order-sensitive and merges badly, and a
30-line spec says the same thing.

```bash
brew install xcodegen
cd sample/compose-app/iosApp && xcodegen generate && open HingeComposeDemo.xcodeproj
```

A pre-build script runs the matching Gradle task, so the Kotlin side builds automatically.
Requires Xcode 27.1+.

`iosApp/DuoHingeSource.swift` is the Swift half of the bridge and the only file in the
repository that names an iPhone Duo API. Posture cannot come from Kotlin: those APIs are
Swift-only and version-gated, so Swift observes and Kotlin interprets.

## Caveat

Nothing here has been compiled. There is no Xcode or Maven access in the environment this was
written in, so treat every call site as reviewed rather than verified. The most likely
breakages are the Duo API signatures in `DuoHingeSource.swift`.
