package dev.bmcreations.hinge

/**
 * Top-level helpers that exist so the Swift side never has to depend on Kotlin features that
 * do not survive export.
 *
 * Two gaps drive everything here:
 *
 * - **Default argument values are not exported.** Every Kotlin function with defaults reaches
 *   Swift with all parameters required, so a Swift call site would have to restate the
 *   defaults and silently drift from Kotlin's. These wrappers keep one source of truth.
 * - **Companion object members are not reliably exported.** Top-level functions are, under
 *   both the Objective-C exporter and Swift export, so the Swift-facing factories live here
 *   rather than on a companion.
 *
 * Kotlin callers should ignore this file and use the ordinary APIs.
 */

/** Swift-facing equivalent of `FoldingState()`. */
public fun unknownFoldingState(): FoldingState = FoldingState()

/** Swift-facing equivalent of `FoldingState.flat(size)`. */
public fun flatFoldingState(windowSize: FoldSize): FoldingState = FoldingState.flat(windowSize)

/** Swift-facing equivalent of `SplitSpec()`, carrying Kotlin's defaults. */
public fun defaultSplitSpec(): SplitSpec = SplitSpec()
