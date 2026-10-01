package dev.bmcreations.hinge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LocalSpaceTest {

    private val book = Postures.book(800f, 600f, hingeThickness = 20f) // hinge at x 390..410

    @Test
    fun fullWindowIsTheSameState() {
        assertSame(book, book.inLocalSpace(0f, 0f, book.windowSize))
    }

    @Test
    fun regionsMoveIntoTheComponentsCoordinates() {
        val local = book.inLocalSpace(originX = 100f, originY = 50f, size = FoldSize(600f, 500f))

        assertEquals(FoldSize(600f, 500f), local.windowSize)
        assertEquals(FoldRect(290f, 0f, 310f, 500f), local.regions.single().bounds)
    }

    @Test
    fun aComponentBesideTheHingeHasNoRegions() {
        val left = book.inLocalSpace(originX = 0f, originY = 0f, size = FoldSize(380f, 600f))

        assertTrue(left.regions.isEmpty())
        assertTrue(left.paneLayout() is PaneLayout.Single)
    }

    @Test
    fun aComponentStraddlingPartOfTheHingeGetsTheClippedPart() {
        val local = book.inLocalSpace(originX = 395f, originY = 0f, size = FoldSize(405f, 600f))

        assertEquals(FoldRect(0f, 0f, 15f, 600f), local.regions.single().bounds)
    }

    @Test
    fun postureAndAngleSurviveTheTranslation() {
        val withAngle = book.copy(hingeAngle = HingeAngle(120f))
        val local = withAngle.inLocalSpace(10f, 10f, FoldSize(700f, 500f))

        assertEquals(withAngle.posture, local.posture)
        assertEquals(withAngle.hingeAngle, local.hingeAngle)
    }
}
