package dev.bmcreations.hinge.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import dev.bmcreations.hinge.FoldingState
import dev.bmcreations.hinge.foldingStateFlow

/**
 * Observes the posture published by the Swift bridge.
 *
 * Returns an unknown posture until `HingeKit.install()` has run, and forever on any build that
 * never calls it. Install early — the `App` initializer or
 * `application(_:didFinishLaunchingWithOptions:)` — so the first composed frame is correct.
 */
@Composable
public actual fun rememberFoldingState(): FoldingState {
    val flow = remember { foldingStateFlow() }
    val state by flow.collectAsState(initial = FoldingState())
    return state
}
