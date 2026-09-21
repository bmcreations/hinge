package dev.bmcreations.hinge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Receives posture changes. Implemented in Swift when consuming the library through the
 * Objective-C exporter.
 */
public interface FoldingStateListener {
    public fun onFoldingStateChanged(state: FoldingState)
}

/**
 * Callback-based posture observation.
 *
 * **Prefer [foldingStateFlow] when building with Swift export**, where it arrives as a native
 * `AsyncSequence` and needs none of this. This class exists for the Objective-C export path,
 * where a Kotlin `Flow` reaches Swift only through generated suspend-function shims.
 *
 * Collects the same shared flow every other consumer uses, so several watchers — or a watcher
 * alongside a Compose collector — cannot tread on each other's subscription.
 */
public class FoldingStateWatcher {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var job: Job? = null

    /** The most recent reading. [FoldPosture.Unknown] until the first callback arrives. */
    public var current: FoldingState = FoldingState()
        private set

    /**
     * Begins observing and notifies [listener] of every reading. Calling this twice restarts
     * observation rather than doubling it.
     */
    public fun start(listener: FoldingStateListener) {
        stop()
        job = scope.launch {
            foldingStateFlow().collect { next ->
                current = next
                listener.onFoldingStateChanged(next)
            }
        }
    }

    /** Stops observing. Safe to call when not started. */
    public fun stop() {
        job?.cancel()
        job = null
    }
}
