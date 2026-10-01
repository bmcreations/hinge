package dev.bmcreations.hinge.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bmcreations.hinge.FoldingState
import dev.bmcreations.hinge.foldingStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Resolves the hosting `Activity` from the composition's context and observes its fold posture.
 *
 * A composition hosted outside an Activity — a preview, a `ComposeView` in a service-owned
 * window, a unit test — has no window geometry to report, so this returns an unknown posture
 * rather than throwing. Everything downstream renders that as a single pane.
 */
@Composable
public actual fun rememberFoldingState(includeHingeAngle: Boolean): FoldingState {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val flow: Flow<FoldingState> = remember(activity, includeHingeAngle) {
        if (activity == null) flowOf(FoldingState()) else foldingStateFlow(activity, includeHingeAngle)
    }
    // collectAsStateWithLifecycle, not collectAsState: foldingStateFlow registers a
    // WindowInfoTracker subscription (and, when enabled, a SensorEventListener). Plain
    // collectAsState holds both open for the lifetime of the composition, including while
    // the Activity is stopped.
    val state by flow.collectAsStateWithLifecycle(initialValue = FoldingState())
    return state
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
