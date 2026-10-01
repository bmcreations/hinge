package dev.bmcreations.hinge

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HingeSnapshotMappingTest {

    private fun snapshot(
        status: Int,
        angleRadians: Double = -1.0,
        vararg regions: HingeSnapshotRegion,
    ) = HingeSnapshot(status, angleRadians, 800.0, 600.0, regions.toList())

    private fun region(left: Double, top: Double, right: Double, bottom: Double, active: Boolean = true) =
        HingeSnapshotRegion(left, top, right, bottom, isSeparating = true, isOccluding = true, isActive = active, identifier = "hinge")

    @Test
    fun statusMapsToPosture() {
        assertEquals(FoldPosture.Closed, snapshot(HingeStatus.CLOSED).toFoldingState().posture)
        assertEquals(FoldPosture.Flat, snapshot(HingeStatus.FULLY_OPEN).toFoldingState().posture)
        assertEquals(FoldPosture.Unknown, snapshot(HingeStatus.UNKNOWN).toFoldingState().posture)
    }

    @Test
    fun partiallyOpenReadsTheAxisFromTheActiveSeparatingRegion() {
        val book = snapshot(HingeStatus.PARTIALLY_OPEN, -1.0, region(390.0, 0.0, 410.0, 600.0))
        val tabletop = snapshot(HingeStatus.PARTIALLY_OPEN, -1.0, region(0.0, 290.0, 800.0, 310.0))

        assertEquals(FoldPosture.Book, book.toFoldingState().posture)
        assertEquals(FoldPosture.Tabletop, tabletop.toFoldingState().posture)
    }

    @Test
    fun anInactiveHorizontalRegionDoesNotMakeTabletop() {
        val snap = snapshot(HingeStatus.PARTIALLY_OPEN, -1.0, region(0.0, 290.0, 800.0, 310.0, active = false))

        assertEquals(FoldPosture.Book, snap.toFoldingState().posture)
    }

    @Test
    fun partiallyOpenWithNoActiveRegionTakesTheAxisFromTheWindowShape() {
        // The Duo's tall window is UIKit portrait, and its fold runs side to side.
        val tall = HingeSnapshot(HingeStatus.PARTIALLY_OPEN, -1.0, 669.0, 951.0, emptyList())
        val wide = HingeSnapshot(HingeStatus.PARTIALLY_OPEN, -1.0, 951.0, 669.0, emptyList())

        assertEquals(FoldPosture.Tabletop, tall.toFoldingState().posture)
        assertEquals(FoldPosture.Book, wide.toFoldingState().posture)
    }

    @Test
    fun geometryCarriesOverInPoints() {
        val state = snapshot(HingeStatus.PARTIALLY_OPEN, -1.0, region(390.0, 0.0, 410.0, 600.0)).toFoldingState()

        assertEquals(FoldSize(800f, 600f), state.windowSize)
        assertEquals(FoldRect(390f, 0f, 410f, 600f), state.regions.single().bounds)
        assertEquals("hinge", state.regions.single().identifier)
    }

    @Test
    fun angleConvertsFromRadiansAndNegativeMeansUnavailable() {
        assertNull(snapshot(HingeStatus.FULLY_OPEN, -1.0).toFoldingState().hingeAngle)
        assertEquals(90f, snapshot(HingeStatus.PARTIALLY_OPEN, PI / 2).toFoldingState().hingeAngle!!.degrees, 0.01f)
    }
}
