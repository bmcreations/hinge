# Hinge

A Kotlin Multiplatform SDK for adaptive layout around a physical fold, on Android foldables
and the iPhone Duo.

One shared model and one layout engine; idiomatic adapters for Compose Multiplatform and
SwiftUI on top. Both platforms answer "should this be two panes, and where exactly" with the
same code, so the two apps cannot drift.

```
hinge-core       pure Kotlin model + pane layout engine, androidx.window and Duo bridge
hinge-compose    Compose Multiplatform adapter (Android + iOS)
swift/HingeKit   Swift sources: the Duo bridge implementation + SwiftUI adapter
sample/          two demo apps: Compose Multiplatform and native SwiftUI
```

Start with `sample/README.md`. Both apps ship a posture simulator that forces Book, Tabletop,
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

### Compose (Android and iOS)

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

### SwiftUI

```swift
@main
struct MyApp: App {
    init() { HingeKit.install() }
    var body: some Scene {
        WindowGroup { RootView().foldAware() }
    }
}

struct RootView: View {
    @State private var selected: Conversation?
    var body: some View {
        FoldAwarePanes {
            ConversationList(selection: $selected)
        } secondary: {
            ConversationDetail(selected)
        }
    }
}
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
gradle wrapper --gradle-version 8.14     # once; no wrapper jar is checked in
./gradlew :hinge-core:allTests           # the layout engine's contract tests
```

### iOS integration

The iOS path is **Swift export**, which produces a real Swift module rather than an
Objective-C framework. That matters here: `Flow` arrives as `AsyncSequence`, so SwiftUI
consumes posture with a plain `for await`; sealed types become Swift enums; and nullable
primitives stop being boxed.

Add a run-script build phase to the app target, ahead of Compile Sources:

```bash
./gradlew :hinge-core:embedSwiftExportForXcode
```

Then add `swift/HingeKit/Sources/HingeKit` to the app target. Swift export supports direct
Xcode integration only — not CocoaPods, not an SPM binaryTarget — so HingeKit ships as source
and there is no `Package.swift`. See `swift/README.md`.

Building it needs **Xcode 27.1+**, because `DuoHingeSource.swift` names the Duo APIs. The
resulting binary runs on **iOS 18+**: every one of those references sits behind
`if #available(iOS 27.1, *)`, and older systems get an unknown posture, which renders as a
single pane.

**Swift export is Alpha.** JetBrains expects breaking changes and does not recommend it for
production. The Kotlin API is written to export cleanly under the Objective-C exporter too —
no function-type parameters, no companion-only factories, no value classes, `splitOrNull()`
instead of a sealed-type `switch` — and `FoldingStateWatcher` is the callback API that path
needs. But the Swift sources here will not compile against it unmodified; falling back means
writing a second, small set of them. `swift/README.md` spells out exactly what changes.

## Things worth knowing before you rely on this

**Geometry is in logical points.** `dp` on Android, points on iOS, interchangeable for layout.
Platform sources divide out density before constructing state. Never put physical pixels in.

**Window coordinates are the trap.** Platform fold geometry is window-relative. A component
that is not full-window will happily split at a fold line that is nowhere near it.
`FoldingState.inLocalSpace()` is the fix, and both UI adapters call it for you. If you consume
`FoldingState` in your own layout code, call it yourself.

**`FoldPosture.Unknown` is a normal state, not a loading state.** Every non-folding device
reports it forever. Never gate rendering on it.

**Android never reports `Closed`.** A closed device is either an ordinary small window on the
cover display or a stopped activity; `androidx.window` has no signal to map. `minPaneSize`
is what makes cover displays behave, which is why it exists.

**Hinge angle is usually null.** Android needs API 30 and an OEM that publishes
`TYPE_HINGE_ANGLE`; most do not. Treat it as an enhancement, never a dependency.

**`DuoHingeSource.swift` is the only file that touches the Duo APIs.** Everything downstream
of it speaks in `Double`s and `Bool`s. If Apple renames a case between betas, the blast radius
is that one file. Its header lists exactly which signatures to re-verify against your SDK.

**Compose: `FoldAwarePanes` needs bounded constraints.** Inside a scrolling parent it has no
extent to divide and will measure the primary pane alone.

**The Kotlin API avoids anything that exports differently under the two modes.** No function
types as parameters (the bridge uses `HingeSnapshotListener`), no companion-only factories
(see `SwiftInterop.kt`), no `value class`, and `PaneLayout.splitOrNull()` as a member rather
than a Swift `switch` or an `as?` cast. If you extend the API, hold that line.

**Check the generated `Hinge.swift` for `PaneLayout` before trusting it.** Sealed types map to
Swift enums under Swift export, and that support is new. `splitOrNull()` is a default-bodied
member on a sealed interface — whether the generator attaches it to the emitted enum is the
single least certain thing in this library, and both `FoldAwarePanes` and
`FoldingState.panes(_:)` depend on it. Read the generated file once, first.

**If the Gradle build rejects `swiftExport { }`,** add
`@OptIn(org.jetbrains.kotlin.gradle.ExperimentalSwiftExportDsl::class)` above the block. The
current docs omit it; JetBrains' own sample includes it.

**The hinge angle is off by default on Android.** `foldingStateFlow(activity)` does not
register the sensor unless you pass `includeHingeAngle = true`. The sensor reports
continuously while the device moves, and every sample would produce a new `FoldingState` — for
a value this library tells you not to lay out with.

**Requires Kotlin 2.4.20.** Swift export only gained sealed-class support and
Swift-implements-Kotlin-interfaces in that release, and this library uses both. Compose
Multiplatform 1.12.0 predates it; if the Compose plugin rejects the pairing, pin `:hinge-compose`
to Kotlin 2.4.0 or wait for the next CMP release.
