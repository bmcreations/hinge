package dev.bmcreations.hinge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Anything that can publish [FoldingState] over time.
 *
 * The library ships a platform-backed implementation for Android and iOS. This interface is
 * public so that tests, previews and screenshot harnesses can inject a posture without a
 * device, and so that a host app with its own window management can supply a better source.
 */
public interface FoldingStateSource {
    public val state: StateFlow<FoldingState>
}

/**
 * A source that never changes. Use it to render a specific posture in a preview or test.
 *
 * ```
 * val bookMode = StaticFoldingStateSource(
 *     FoldingState(
 *         posture = FoldPosture.Book,
 *         windowSize = FoldSize(1280f, 900f),
 *         regions = listOf(
 *             FoldRegion(FoldRect(632f, 0f, 648f, 900f), isSeparating = true, isOccluding = true)
 *         ),
 *     )
 * )
 * ```
 */
public class StaticFoldingStateSource(
    initial: FoldingState,
) : FoldingStateSource {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<FoldingState> = _state.asStateFlow()

    /** Push a new posture, for driving a test through a fold. */
    public fun emit(next: FoldingState) {
        _state.value = next
    }
}

/** Convenience for consumers that only want the stream. */
public fun FoldingStateSource.asFlow(): Flow<FoldingState> = state
