package dev.bmcreations.hinge

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import androidx.window.layout.WindowMetricsCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Observes fold posture for [activity].
 *
 * Cold: collection starts the underlying `WindowInfoTracker` and, where the OEM provides one,
 * the hinge angle sensor. Both stop when collection stops, so collect it from a
 * lifecycle-aware scope (`repeatOnLifecycle(STARTED)`) rather than a process-wide one.
 *
 * An [Activity] is required rather than a `Context` because window metrics and fold geometry
 * are only meaningful relative to a real window; an application context has none.
 *
 * ### Known limits
 * - [FoldPosture.Closed] is never reported here. Android surfaces a closed device as an
 *   ordinary small window on the cover display, or by stopping the activity outright, and
 *   `androidx.window` has no "closed" signal to map. Size-based logic covers the cover display
 *   correctly, which is why [SplitSpec.minPaneSize] exists.
 * - Hinge angle is off unless [includeHingeAngle] is set, and even then it needs API 30 and
 *   an OEM that publishes `TYPE_HINGE_ANGLE`, which most are not. It is opt-in because the
 *   sensor reports continuously while the device moves and every sample produces a new
 *   [FoldingState] -- churn for a value that must not drive layout anyway. Turn it on only
 *   for the interaction cases [HingeAngle] describes.
 */
public fun foldingStateFlow(
    activity: Activity,
    includeHingeAngle: Boolean = false,
): Flow<FoldingState> =
    combine(
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity),
        if (includeHingeAngle) hingeAngleFlow(activity) else flowOf(null),
    ) { layoutInfo, angle ->
        layoutInfo.toFoldingState(activity, angle)
    }.distinctUntilChanged()

/**
 * [FoldingStateSource] wrapper for consumers that want a hot, always-readable value.
 *
 * Shares one upstream subscription across collectors and keeps it alive for 5s past the last
 * one, so a configuration change does not tear down and re-create the tracker.
 */
public fun foldingStateSource(
    activity: Activity,
    scope: CoroutineScope,
    includeHingeAngle: Boolean = false,
): FoldingStateSource = object : FoldingStateSource {
    override val state: StateFlow<FoldingState> = foldingStateFlow(activity, includeHingeAngle).stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FoldingState.flat(activity.windowSizeInDp()),
    )
}

internal fun WindowLayoutInfo.toFoldingState(
    activity: Activity,
    angle: HingeAngle?,
): FoldingState {
    val density = activity.resources.displayMetrics.density
    val size = activity.windowSizeInDp()
    val folds = displayFeatures.filterIsInstance<FoldingFeature>()

    val posture = when {
        folds.isEmpty() -> FoldPosture.Flat
        else -> folds.first().toPosture()
    }

    return FoldingState(
        posture = posture,
        windowSize = size,
        regions = folds.map { it.toRegion(density) },
        hingeAngle = angle,
    )
}

private fun FoldingFeature.toPosture(): FoldPosture = when (state) {
    FoldingFeature.State.HALF_OPENED -> when (orientation) {
        FoldingFeature.Orientation.VERTICAL -> FoldPosture.Book
        else -> FoldPosture.Tabletop
    }
    else -> FoldPosture.Flat
}

/**
 * `FoldingFeature.bounds` are already in the window's coordinate space and in physical pixels,
 * so only a density division is needed. Do not subtract the window origin here.
 */
private fun FoldingFeature.toRegion(density: Float): FoldRegion = FoldRegion(
    bounds = FoldRect(
        left = bounds.left / density,
        top = bounds.top / density,
        right = bounds.right / density,
        bottom = bounds.bottom / density,
    ),
    isSeparating = isSeparating,
    isOccluding = occlusionType == FoldingFeature.OcclusionType.FULL,
    isActive = true,
    identifier = "androidx.window.FoldingFeature",
)

internal fun Activity.windowSizeInDp(): FoldSize {
    val density = resources.displayMetrics.density
    val bounds = WindowMetricsCalculator.getOrCreate()
        .computeCurrentWindowMetrics(this)
        .bounds
    return FoldSize(bounds.width() / density, bounds.height() / density)
}

/**
 * Emits the OEM hinge angle where one exists, and a single `null` where it does not, so that
 * [combine] always has a value to work with and never stalls the posture stream.
 */
private fun hingeAngleFlow(context: Context): Flow<HingeAngle?> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return flowOf(null)
    val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        ?: return flowOf(null)
    val sensor = manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) ?: return flowOf(null)

    return callbackFlow {
        trySend(null)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                event.values.firstOrNull()?.let { trySend(HingeAngle(it)) }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { manager.unregisterListener(listener) }
    }
}
