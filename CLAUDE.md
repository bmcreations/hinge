# Hinge

Kotlin Multiplatform SDK for adaptive layout around a physical fold: Android foldables via
`androidx.window`, and the iPhone Duo via a Swift bridge. See `README.md` for the design.

## Layout

- `hinge-core` — pure Kotlin model and the pane layout engine (`FoldingState.paneLayout`),
  plus the Android source (`androidMain`) and the iOS bridge (`iosMain`).
- `hinge-compose` — Compose Multiplatform adapter (`ProvideFoldingState`, `FoldAwarePanes`,
  `ListDetailPanes`, `LocalPaneLayout`, `rememberPaneLayout`, `rememberOcclusionPadding`). Both
  pane composables sit on `PaneHost`, which subcomposes each pane by content key so state
  survives single/split changes.
- `sample/compose-app` — the shared Compose UI for Android and iOS (a KMP library), with a
  posture simulator. `iosApp/` is an XcodeGen spec; the generated `.xcodeproj` is gitignored.
- `sample/android-app` — Android entry point only (`MainActivity`, manifest, theme).

## Commands

```bash
./gradlew :hinge-core:allTests                 # layout engine contract tests
./gradlew :sample:android-app:assembleDebug    # Android sample
./gradlew :sample:android-app:installDebug
cd sample/compose-app/iosApp && xcodegen generate   # then build HingeComposeDemo in Xcode 27.1+
```

The Duo APIs ship in the Xcode 27.1 SDK. If `xcode-select` points at an older Xcode, build with
`DEVELOPER_DIR=/Applications/Xcode-27.1.0-Beta.app/Contents/Developer`, and pass
`ARCHS=arm64` for a generic simulator destination (CMP 1.12 publishes no `iosX64`). UI tests
for `hinge-compose` run with `./gradlew :hinge-compose:iosSimulatorArm64Test`.

The Xcode pre-build script runs `embedAndSignAppleFrameworkForXcode`, so the Kotlin
framework builds from Xcode.

`hinge-core`'s `commonTest` runs on the Android host (`testAndroidHostTest`) and the iOS
simulator.

## Rules

- **Kotlin stays at or above the compiler CMP's klibs were built with.** CMP 1.12.0 needs
  Kotlin 2.3.20; older compilers fail with "KLIB resolver: Could not find". Re-check the klib
  manifest's `compiler_version` when bumping CMP. CMP 1.12 publishes no `iosX64`.
- **Only `sample/compose-app/iosApp/DuoHingeSource.swift` names an iPhone Duo API.** The
  sample's deployment target is iOS 27.1, the first Duo release, so no availability checks
  are needed there. Keep `HingeSnapshot` made of primitives. Don't add a second copy of the
  bridge.
- **Geometry is logical points** (`dp` / UIKit points). Platform sources divide out density.
- **Hinge angle is not a layout input.** Layout reads reserved regions and posture only.
- **`FoldPosture.Unknown` is a normal state, not loading.** Every non-folding device reports it
  forever. It gets two panes only when the window alone is wide enough (`SplitStrategy.FoldAware`).
- **AGP 9 (9.4.1).** Library modules use `com.android.kotlin.multiplatform.library` with
  config inside `kotlin { android { } }`; never apply `com.android.application` or
  `com.android.library` next to the multiplatform plugin. CMP 1.12 needs AGP 9.1+ and
  compileSdk 37.
- Both library modules use `explicitApi()`: public declarations need explicit visibility and
  return types. Types reachable from Swift must stay Obj-C exportable (no value classes).
- iOS frameworks are produced only by the app module, which `export`s `hinge-core`. Don't add
  framework binaries to the library modules.
