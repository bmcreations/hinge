# Hinge

A Kotlin Multiplatform SDK for adaptive layout around a physical fold, on Android foldables
and the iPhone Duo.

One shared model and one layout engine, with a Compose Multiplatform adapter on top. Both
platforms answer "should this be two panes, and where exactly" with the same code, so the two
apps cannot drift.

```
hinge-core       pure Kotlin model + pane layout engine, androidx.window and Duo bridges
hinge-compose    Compose Multiplatform adapter (Android + iOS)
sample/          Compose Multiplatform demo app
```

Start with `sample/README.md`. The demo ships a posture simulator that forces Book, Tabletop,
seamless, cover-display and occlusion-only states on any device, which is the only practical
way to exercise this code without a drawer full of foldables.

## The design in one paragraph

Both platforms independently converged on the same two ideas, and this library takes them at
their word. **Reserved regions** — `FoldingFeature` on Android, `reservedRegions(kind:)` on
iOS — describe where the display is physically interrupted, and they are what layout reads.
**Hinge angle** is a continuous interaction signal and is explicitly *not* for layout; Apple
says so outright for the Duo, and the same is true of Android's `TYPE_HINGE_ANGLE`. So the
core exposes regions as the layout input, keeps the angle nullable and clearly labelled, and
resolves everything down to one function:

```kotlin
fun FoldingState.paneLayout(spec: SplitSpec = SplitSpec()): PaneLayout
```

`PaneLayout` is either `Single` or `Split`. There is no "is it folded" boolean to get wrong
and no posture enum with a branch you forgot.

## Usage

```kotlin
setContent {
    ProvideFoldingState {
        FoldAwarePanes(
            primary = { ConversationList(onSelect = { selected = it }) },
            secondary = { ConversationDetail(selected) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
```

Branch on the layout yourself when navigation depends on it:

```kotlin
val twoPane = rememberPaneLayout() is PaneLayout.Split
BackHandler(enabled = !twoPane && selected != null) { selected = null }
```

### Tuning

```kotlin
SplitSpec(
    strategy = SplitStrategy.FoldAware,  // FoldAware | AlwaysSplit | NeverSplit
    ratio = 0.4f,                        // primary's share when there is no fold to follow
    minPaneSize = 320f,                  // below this, collapse to one pane
    gutter = 24f,                        // gap when there is no fold to use as one
    preferredAxis = null,                // null means side-by-side
)
```

## Build

```bash
./gradlew :hinge-core:allTests           # the layout engine's contract tests
./gradlew :sample:android-app:installDebug
```

The build needs Gradle 9.7 (the wrapper), JDK 17+, and Android SDK platform 37. AGP 9 does
not allow `com.android.application` alongside the multiplatform plugin, so the sample's
Android entry point is its own module, `sample/android-app`, and the shared UI in
`sample/compose-app` is a library.

### iOS

Compose Multiplatform's iOS integration produces an Objective-C framework, and that is the
only export path this library uses. The app module's framework must explicitly export
`hinge-core`, or none of the bridge types reach Swift:

```kotlin
target.binaries.framework {
    baseName = "ComposeApp"
    isStatic = true
    export(project(":hinge-core"))
}
```

Posture itself cannot come from Kotlin: the Duo APIs are Swift-only and version-gated. The app
implements `HingeBridge` in Swift and registers it at launch:

```swift
HingeBridgeRegistry.shared.install(bridge: DuoHingeSource())
```

`sample/compose-app/iosApp/DuoHingeSource.swift` is a complete implementation, and is the only
file in the repository that names an iPhone Duo API. Building it needs **Xcode 27.1+**; the
resulting binary runs on **iOS 18+**, because every Duo reference sits behind
`if #available(iOS 27.1, *)` and older systems get an unknown posture, which renders as a
single pane.

## Things worth knowing before you rely on this

**Geometry is in logical points.** `dp` on Android, points on iOS, interchangeable for layout.
Platform sources divide out density before constructing state. Never put physical pixels in.

**Window coordinates are the trap.** Platform fold geometry is window-relative. A component
that is not full-window will happily split at a fold line that is nowhere near it.
`FoldingState.inLocalSpace()` is the fix, and `FoldAwarePanes` calls it for you. If you consume
`FoldingState` in your own layout code, call it yourself. Note also that `rememberPaneLayout()`
resolves *window* geometry while `FoldAwarePanes` resolves its own local geometry: they agree
only when the panes fill the window.

**`FoldPosture.Unknown` is a normal state, not a loading state.** Every non-folding device
reports it forever. Never gate rendering on it.

**Android never reports `Closed`.** A closed device is either an ordinary small window on the
cover display or a stopped activity; `androidx.window` has no signal to map. `minPaneSize`
is what makes cover displays behave, which is why it exists.

**The hinge angle is off by default on Android.** `foldingStateFlow(activity)` does not
register the sensor unless you pass `includeHingeAngle = true`. It reports continuously while
the device moves, and every sample would produce a new `FoldingState` — for a value this
library tells you not to lay out with. It also needs API 30 and an OEM that publishes
`TYPE_HINGE_ANGLE`, which most are not.

**`DuoHingeSource.swift` is the only file that touches the Duo APIs.** Everything downstream of
it speaks in `Double`s and `Bool`s. If Apple renames a case between betas, the blast radius is
that one file. Its header lists exactly which signatures to re-verify against your SDK.

**Compose: `FoldAwarePanes` needs bounded constraints.** Inside a scrolling parent it has no
extent to divide and will measure the primary pane alone.

**What has actually run.** `hinge-core`'s tests pass on the Android unit-test and
`iosSimulatorArm64` targets. The sample builds with Xcode 27.1 and runs on the iPhone Duo
simulator, where `DuoHingeSource` reports Flat, Book (a 40pt separating region in the centre,
with list/detail split around it) and Closed (a 466x678 cover display). The Android sample
builds, but has not yet been run on a foldable device or emulator.
