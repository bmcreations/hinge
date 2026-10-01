package dev.bmcreations.hinge.compose

import dev.bmcreations.hinge.FoldRect
import dev.bmcreations.hinge.FoldRegion
import dev.bmcreations.hinge.FoldSize
import dev.bmcreations.hinge.FoldingState
import dev.bmcreations.hinge.FoldPosture
import kotlin.test.Test
import kotlin.test.assertEquals

class OcclusionInsetsTest {

    private fun state(vararg bounds: FoldRect, active: Boolean = true) = FoldingState(
        posture = FoldPosture.Flat,
        windowSize = FoldSize(400f, 800f),
        regions = bounds.map {
            FoldRegion(bounds = it, isSeparating = false, isOccluding = true, isActive = active)
        },
    )

    @Test
    fun eachEdgeGetsItsOwnInset() {
        val insets = state(
            FoldRect(0f, 0f, 24f, 800f),
            FoldRect(380f, 0f, 400f, 800f),
            FoldRect(100f, 0f, 300f, 30f),
            FoldRect(100f, 760f, 300f, 800f),
        ).occlusionInsets()

        assertEquals(OcclusionInsets(left = 24f, top = 30f, right = 20f, bottom = 40f), insets)
    }

    @Test
    fun theDeepestRegionOnAnEdgeWins() {
        val insets = state(FoldRect(0f, 0f, 10f, 400f), FoldRect(0f, 400f, 32f, 800f))
            .occlusionInsets()

        assertEquals(32f, insets.left)
    }

    @Test
    fun aMidWindowFoldPadsNothing() {
        // A separating hinge in the middle is the pane layout's job, not the padding's.
        assertEquals(OcclusionInsets(0f, 0f, 0f, 0f), state(FoldRect(190f, 0f, 210f, 800f)).occlusionInsets())
    }

    @Test
    fun zeroAreaAndInactiveRegionsPadNothing() {
        assertEquals(OcclusionInsets(0f, 0f, 0f, 0f), state(FoldRect(0f, 0f, 0f, 800f)).occlusionInsets())
        assertEquals(
            OcclusionInsets(0f, 0f, 0f, 0f),
            state(FoldRect(0f, 0f, 24f, 800f), active = false).occlusionInsets(),
        )
    }

    @Test
    fun aCornerRegionPadsOnlyItsCheaperEdge() {
        // A housing in the top-left corner, 120 wide and 40 tall: padding the top costs 40,
        // the left 120, so only the top moves.
        assertEquals(
            OcclusionInsets(left = 0f, top = 40f, right = 0f, bottom = 0f),
            state(FoldRect(0f, 0f, 120f, 40f)).occlusionInsets(),
        )
    }

    @Test
    fun aCornerRegionAlreadyUnderAReservedBandPadsNothing() {
        // A 130 tall top bar already keeps content below the 120 tall housing.
        val reserved = OcclusionInsets(left = 0f, top = 130f, right = 0f, bottom = 0f)

        assertEquals(
            OcclusionInsets(0f, 0f, 0f, 0f),
            state(FoldRect(360f, 0f, 400f, 120f)).occlusionInsets(reserved),
        )
    }

    @Test
    fun reservedSpaceIsSubtractedFromTheInset() {
        // A 24 wide strip on the left with 10 already reserved needs 14 more.
        val reserved = OcclusionInsets(left = 10f, top = 0f, right = 0f, bottom = 0f)

        assertEquals(14f, state(FoldRect(0f, 0f, 24f, 800f)).occlusionInsets(reserved).left)
    }
}
