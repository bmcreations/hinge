package dev.bmcreations.hinge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/**
 * The hardware is a single, process-wide resource and [HingeBridge] holds exactly one
 * listener, so every consumer has to share one subscription. Without this, two collectors
 * each call `startObserving`, and the first to cancel calls `stopObserving` and silently
 * kills the other one's updates.
 */
private val hingeScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

@OptIn(ExperimentalCoroutinesApi::class)
private val sharedFoldingState: Flow<FoldingState> =
    HingeBridgeRegistry.bridge
        .flatMapLatest { bridge ->
            if (bridge == null) {
                flowOf(FoldingState())
            } else {
                callbackFlow {
                    trySend(FoldingState())
                    bridge.startObserving { snapshot -> trySend(snapshot.toFoldingState()) }
                    awaitClose { bridge.stopObserving() }
                }
            }
        }
        .distinctUntilChanged()
        .shareIn(hingeScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

/**
 * Observes fold posture through the bridge registered with [HingeBridgeRegistry].
 *
 * Safe to collect at any time, including before the bridge is installed: it emits
 * [FoldPosture.Unknown] immediately and switches to real readings the moment Swift registers.
 * All collectors share one hardware subscription.
 */
public fun foldingStateFlow(): Flow<FoldingState> = sharedFoldingState

/** Hot [FoldingStateSource] for consumers that want an always-readable current value. */
public fun foldingStateSource(scope: CoroutineScope): FoldingStateSource =
    object : FoldingStateSource {
        override val state: StateFlow<FoldingState> = foldingStateFlow().stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FoldingState(),
        )
    }

internal fun HingeSnapshot.toFoldingState(): FoldingState {
    val mapped = regions.map { it.toRegion() }
    return FoldingState(
        posture = derivePosture(mapped),
        windowSize = FoldSize(windowWidth.toFloat(), windowHeight.toFloat()),
        regions = mapped,
        hingeAngle = if (angleRadians < 0.0) {
            null
        } else {
            HingeAngle.ofRadians(angleRadians.toFloat())
        },
    )
}

/**
 * Maps `UIHinge.Status` onto [FoldPosture], taking the *axis* from geometry rather than from
 * the status, which does not carry one.
 *
 * Apple's guidance is to treat the reserved regions as the source of truth for layout, so the
 * region a half-opened device reports is what decides Book versus Tabletop. With no active
 * region to read, the window's shape decides: the Duo's fold halves the longer side, so a
 * tall window has a horizontal fold (Tabletop) and a wide one a vertical fold (Book).
 */
private fun HingeSnapshot.derivePosture(regions: List<FoldRegion>): FoldPosture = when (status) {
    HingeStatus.CLOSED -> FoldPosture.Closed
    HingeStatus.FULLY_OPEN -> FoldPosture.Flat
    HingeStatus.PARTIALLY_OPEN -> {
        val axis = regions.firstOrNull { it.isSeparating && it.isActive }?.axis
            ?: if (windowHeight > windowWidth) FoldAxis.Horizontal else FoldAxis.Vertical
        if (axis == FoldAxis.Horizontal) FoldPosture.Tabletop else FoldPosture.Book
    }
    else -> FoldPosture.Unknown
}

private fun HingeSnapshotRegion.toRegion(): FoldRegion = FoldRegion(
    bounds = FoldRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()),
    isSeparating = isSeparating,
    isOccluding = isOccluding,
    isActive = isActive,
    identifier = identifier,
)
