package dev.bmcreations.hinge

import android.graphics.Rect
import androidx.window.layout.FoldingFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FoldingFeatureMappingTest {

    private class Fold(
        private val rect: Rect,
        override val state: FoldingFeature.State,
        override val orientation: FoldingFeature.Orientation,
        override val isSeparating: Boolean = true,
        override val occlusionType: FoldingFeature.OcclusionType = FoldingFeature.OcclusionType.NONE,
    ) : FoldingFeature {
        override val bounds: Rect get() = rect
    }

    // android.jar's Rect constructor is a stub on the host; its fields are real.
    private fun rect(left: Int, top: Int, right: Int, bottom: Int) = Rect().apply {
        this.left = left; this.top = top; this.right = right; this.bottom = bottom
    }

    private val window = FoldSize(800f, 600f)

    @Test
    fun noFoldIsUnknownWithNoRegions() {
        val state = foldingState(emptyList(), density = 2f, windowSize = window, angle = null)

        assertEquals(FoldPosture.Unknown, state.posture)
        assertTrue(state.regions.isEmpty())
    }

    @Test
    fun halfOpenedPostureFollowsOrientation() {
        val book = Fold(rect(800, 0, 800, 1200), FoldingFeature.State.HALF_OPENED, FoldingFeature.Orientation.VERTICAL)
        val tabletop = Fold(rect(0, 600, 1600, 600), FoldingFeature.State.HALF_OPENED, FoldingFeature.Orientation.HORIZONTAL)
        val flat = Fold(rect(800, 0, 800, 1200), FoldingFeature.State.FLAT, FoldingFeature.Orientation.VERTICAL)

        assertEquals(FoldPosture.Book, foldingState(listOf(book), 2f, window, null).posture)
        assertEquals(FoldPosture.Tabletop, foldingState(listOf(tabletop), 2f, window, null).posture)
        assertEquals(FoldPosture.Flat, foldingState(listOf(flat), 2f, window, null).posture)
    }

    @Test
    fun boundsAreDividedByDensity() {
        val fold = Fold(
            rect(780, 0, 820, 1200),
            FoldingFeature.State.FLAT,
            FoldingFeature.Orientation.VERTICAL,
            occlusionType = FoldingFeature.OcclusionType.FULL,
        )
        val region = foldingState(listOf(fold), density = 2f, windowSize = window, angle = null).regions.single()

        assertEquals(FoldRect(390f, 0f, 410f, 600f), region.bounds)
        assertTrue(region.isOccluding)
        assertTrue(region.isSeparating)
    }

    @Test
    fun partialOcclusionIsNotOccluding() {
        val fold = Fold(rect(800, 0, 800, 1200), FoldingFeature.State.FLAT, FoldingFeature.Orientation.VERTICAL, isSeparating = false)
        val region = foldingState(listOf(fold), 2f, window, null).regions.single()

        assertFalse(region.isOccluding)
        assertFalse(region.isSeparating)
    }
}
