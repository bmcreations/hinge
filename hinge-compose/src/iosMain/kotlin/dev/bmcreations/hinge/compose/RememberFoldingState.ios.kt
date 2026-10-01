package dev.bmcreations.hinge.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import dev.bmcreations.hinge.FoldingState
import dev.bmcreations.hinge.foldingStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Observes the posture published by the Swift bridge.
 *
 * Returns an unknown posture until a bridge is registered with `HingeBridgeRegistry`, and
 * forever on any build that never registers one. Register early — the `App` initializer or
 * `application(_:didFinishLaunchingWithOptions:)` — so the first composed frame is correct.
 */
@Composable
public actual fun rememberFoldingState(includeHingeAngle: Boolean): FoldingState {
    val flow = remember(includeHingeAngle) {
        // The bridge always reports an angle. Dropping it here, then deduplicating, is what
        // keeps a moving hinge from recomposing readers that did not ask for it.
        if (includeHingeAngle) {
            foldingStateFlow()
        } else {
            foldingStateFlow().map { it.copy(hingeAngle = null) }.distinctUntilChanged()
        }
    }
    val state by flow.collectAsState(initial = FoldingState())
    return state
}
