package dev.bmcreations.hinge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Status codes mirroring `UIHinge.Status` / SwiftUI's `DeviceHinge.status`.
 *
 * Plain `Int` rather than a Kotlin enum so that the Swift side of the bridge stays a trivial,
 * dependency-light mapping. Note Kotlin `Int` arrives in Swift as `Int32`, so the Swift mirror
 * of this type uses `Int32` raw values.
 */
public object HingeStatus {
    public const val UNKNOWN: Int = 0
    public const val CLOSED: Int = 1
    public const val PARTIALLY_OPEN: Int = 2
    public const val FULLY_OPEN: Int = 3
}

/** One reserved region, as reported by `UIView.reservedRegions(kind:options:)`. */
public data class HingeSnapshotRegion(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
    val isSeparating: Boolean,
    val isOccluding: Boolean,
    val isActive: Boolean,
    val identifier: String?,
)

/**
 * A raw posture reading handed across the language boundary.
 *
 * Deliberately made of primitives and flat data classes only. Everything interesting happens
 * in Kotlin on the far side of this type, which keeps the Swift file that produces it small
 * enough to re-verify by hand against each iOS SDK release.
 */
public data class HingeSnapshot(
    /** One of the [HingeStatus] constants. */
    val status: Int,
    /** Hinge angle in **radians**, matching `UIHinge.angle`. Negative means unavailable. */
    val angleRadians: Double,
    /** Window size in points. */
    val windowWidth: Double,
    val windowHeight: Double,
    /** Reserved regions in window-relative points. */
    val regions: List<HingeSnapshotRegion>,
)

/**
 * Implemented in Swift, consumed here.
 *
 * The iOS foldable APIs are Swift-only and version-gated, and none of them are reachable from
 * Kotlin/Native's UIKit bindings until those bindings are regenerated against a matching SDK.
 * Rather than depend on that, the library inverts the dependency: Swift observes, Kotlin
 * interprets. The whole platform surface is this one interface.
 *
 * The app registers an implementation with [HingeBridgeRegistry] at launch. See
 * `sample/compose-app/iosApp/DuoHingeSource.swift` for a complete one.
 */
public interface HingeBridge {
    /** Begin observing. [onSnapshot] is called on the main thread with each new reading. */
    public fun startObserving(onSnapshot: (HingeSnapshot) -> Unit)

    /** Stop observing and release any interaction or observer the implementation installed. */
    public fun stopObserving()
}

/**
 * Registration point for the iOS posture bridge.
 *
 * Named `HingeBridgeRegistry` rather than `Hinge` on purpose: the generated framework carries
 * the app module's name and a type called `Hinge` inside it reads ambiguously at every Swift
 * call site.
 *
 * Register early, before the first frame. Until then [foldingStateFlow] reports
 * [FoldPosture.Unknown], which every layout in this library already renders as a single pane.
 */
public object HingeBridgeRegistry {
    private val _bridge = MutableStateFlow<HingeBridge?>(null)

    /**
     * The installed bridge, observable so that a collector started before registration picks
     * the bridge up when it arrives rather than being stuck on [FoldPosture.Unknown] forever.
     */
    internal val bridge: StateFlow<HingeBridge?> = _bridge.asStateFlow()

    /** Registers [bridge], replacing any previous one. */
    public fun install(bridge: HingeBridge) {
        _bridge.value = bridge
    }

    /** Removes the current bridge. Mostly useful in tests. */
    public fun uninstall() {
        _bridge.value = null
    }

    /** True once Swift has registered a bridge. */
    public val isInstalled: Boolean get() = _bridge.value != null
}
