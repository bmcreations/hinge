# Sample

`compose-app` is a Compose Multiplatform demo: one UI, running on Android and iOS. `android-app`
is the Android entry point only (`MainActivity` and the manifest), because AGP 9 does not allow
an application module to also apply the multiplatform plugin. It has two screens, a posture
simulator, and an inspector that prints what the SDK reported and what geometry it resolved to:

- **Notes**, a list/detail screen on `ListDetailPanes`. The open note keeps its state when the
  layout switches between one and two panes.
- **Player**, a media player that changes shape with the fold: video beside the queue in Book or
  a wide window, video on the raised half and controls on the flat half in Tabletop, and video
  and controls alone on a cover display. Each pane reads `LocalPaneLayout` for the axis.

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
./gradlew :sample:android-app:installDebug
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

## Status

The iOS app runs on the iPhone Duo simulator (Xcode 27.1) and reports live Flat, Book and
Closed postures. The Android app builds, but has not yet been run on a foldable device or
emulator.
