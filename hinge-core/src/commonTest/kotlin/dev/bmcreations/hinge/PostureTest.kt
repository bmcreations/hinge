package dev.bmcreations.hinge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PostureTest {

    @Test
    fun tallThinRegionIsAVerticalHinge() {
        assertEquals(FoldAxis.Vertical, FoldRect(632f, 0f, 648f, 900f).axis)
    }

    @Test
    fun shortWideRegionIsAHorizontalHinge() {
        assertEquals(FoldAxis.Horizontal, FoldRect(0f, 632f, 900f, 648f).axis)
    }

    @Test
    fun zeroThicknessVerticalLineResolvesToVertical() {
        assertEquals(FoldAxis.Vertical, FoldRect(640f, 0f, 640f, 900f).axis)
    }

    @Test
    fun postureAxisMatchesRegionAxis() {
        assertEquals(FoldAxis.Vertical, FoldPosture.Book.axis)
        assertEquals(FoldAxis.Horizontal, FoldPosture.Tabletop.axis)
        assertNull(FoldPosture.Flat.axis)
        assertNull(FoldPosture.Unknown.axis)
        assertNull(FoldPosture.Closed.axis)
    }

    @Test
    fun hingeAngleConvertsFromRadians() {
        assertEquals(180f, HingeAngle.ofRadians(3.1415927f).degrees, absoluteTolerance = 0.01f)
        assertEquals(90f, HingeAngle.ofRadians(1.5707964f).degrees, absoluteTolerance = 0.01f)
    }

    @Test
    fun hingeAngleFractionIsClamped() {
        assertEquals(1f, HingeAngle(200f).fraction)
        assertEquals(0f, HingeAngle(-5f).fraction)
        assertEquals(0.5f, HingeAngle(90f).fraction)
    }

    @Test
    fun degenerateIntersectionIsPreserved() {
        val touching = FoldRect(640f, 0f, 640f, 900f).intersect(FoldRect(0f, 0f, 640f, 900f))
        assertEquals(FoldRect(640f, 0f, 640f, 900f), touching)
    }

    @Test
    fun disjointIntersectionIsNull() {
        assertNull(FoldRect(700f, 0f, 716f, 900f).intersect(FoldRect(0f, 0f, 640f, 900f)))
    }
}
