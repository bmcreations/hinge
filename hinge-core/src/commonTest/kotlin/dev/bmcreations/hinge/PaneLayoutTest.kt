package dev.bmcreations.hinge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Behavioural contract for [paneLayout].
 *
 * These cases are the specification. Every one of them describes a real device configuration,
 * and several of them exist because the obvious implementation gets them wrong.
 */
class PaneLayoutTest {

    private fun state(
        posture: FoldPosture,
        width: Float,
        height: Float,
        vararg regions: FoldRegion,
    ) = FoldingState(posture, FoldSize(width, height), regions.toList())

    private fun fold(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        separating: Boolean = true,
        occluding: Boolean = true,
        active: Boolean = true,
    ) = FoldRegion(FoldRect(left, top, right, bottom), separating, occluding, active)

    // --- no fold -----------------------------------------------------------------------

    @Test
    fun ordinaryPhoneStaysSinglePane() {
        // Regression: an aspect-derived split axis turns a tall phone into a stacked two-pane
        // layout, because a phone is taller than it is wide. Two panes are a width affordance.
        val layout = state(FoldPosture.Flat, 411f, 891f).paneLayout()
        assertIs<PaneLayout.Single>(layout)
    }

    @Test
    fun wideWindowSplitsSideBySideWithNoFold() {
        val layout = state(FoldPosture.Flat, 1024f, 1080f).paneLayout()
        val split = assertIs<PaneLayout.Split>(layout)
        assertEquals(FoldAxis.Vertical, split.axis)
        assertEquals(512f, split.primary.width)
        assertEquals(512f, split.secondary.width)
    }

    @Test
    fun ratioAndGutterAreHonouredWithNoFold() {
        val layout = state(FoldPosture.Flat, 1600f, 1000f)
            .paneLayout(SplitSpec(ratio = 0.4f, gutter = 24f))
        val split = assertIs<PaneLayout.Split>(layout)
        assertEquals(628f, split.primary.right)
        assertEquals(652f, split.secondary.left)
        assertEquals(24f, split.gutter.width)
    }

    // --- folded ------------------------------------------------------------------------

    @Test
    fun bookPostureSplitsAroundTheHinge() {
        val layout = state(FoldPosture.Book, 1280f, 900f, fold(632f, 0f, 648f, 900f)).paneLayout()
        val split = assertIs<PaneLayout.Split>(layout)
        assertEquals(FoldAxis.Vertical, split.axis)
        assertEquals(FoldRect(0f, 0f, 632f, 900f), split.primary)
        assertEquals(FoldRect(648f, 0f, 1280f, 900f), split.secondary)
        assertEquals(FoldRect(632f, 0f, 648f, 900f), split.gutter)
    }

    @Test
    fun tabletopPostureSplitsTopAndBottom() {
        val layout = state(FoldPosture.Tabletop, 900f, 1280f, fold(0f, 632f, 900f, 648f)).paneLayout()
        val split = assertIs<PaneLayout.Split>(layout)
        assertEquals(FoldAxis.Horizontal, split.axis)
        assertEquals(632f, split.primary.height)
        assertEquals(632f, split.secondary.height)
    }

    @Test
    fun seamlessZeroThicknessFoldStillSplits() {
        // Gap-less foldables report a fold line with no width. It must not be discarded.
        val layout = state(FoldPosture.Book, 1280f, 900f, fold(640f, 0f, 640f, 900f, occluding = false))
            .paneLayout()
        val split = assertIs<PaneLayout.Split>(layout)
        assertEquals(640f, split.primary.width)
        assertEquals(640f, split.secondary.width)
        assertEquals(0f, split.gutter.width)
    }

    @Test
    fun foldTooCloseToTheEdgeCollapsesToOnePane() {
        val layout = state(FoldPosture.Book, 700f, 900f, fold(20f, 0f, 36f, 900f)).paneLayout()
        assertIs<PaneLayout.Single>(layout)
    }

    @Test
    fun inactiveFoldDoesNotSplit() {
        val layout = state(FoldPosture.Flat, 500f, 900f, fold(240f, 0f, 260f, 900f, active = false))
            .paneLayout()
        assertIs<PaneLayout.Single>(layout)
    }

    @Test
    fun occludingButNotSeparatingRegionDoesNotDriveTheSplit() {
        // A camera cutout crossing continuous content must be avoided, not split around.
        val layout = state(
            FoldPosture.Flat, 1024f, 1080f,
            fold(500f, 0f, 524f, 1080f, separating = false),
        ).paneLayout()
        val split = assertIs<PaneLayout.Split>(layout)
        assertEquals(512f, split.primary.width, "split came from size, not from the cutout")
    }

    // --- strategies --------------------------------------------------------------------

    @Test
    fun neverSplitWinsOverAFold() {
        val layout = state(FoldPosture.Book, 1280f, 900f, fold(632f, 0f, 648f, 900f))
            .paneLayout(SplitSpec(strategy = SplitStrategy.NeverSplit))
        assertIs<PaneLayout.Single>(layout)
    }

    @Test
    fun alwaysSplitStillRespectsMinimumPaneSize() {
        val layout = state(FoldPosture.Flat, 411f, 891f)
            .paneLayout(SplitSpec(strategy = SplitStrategy.AlwaysSplit))
        assertIs<PaneLayout.Single>(layout)
    }

    @Test
    fun preferredAxisForcesAStackedSplit() {
        val layout = state(FoldPosture.Flat, 411f, 891f).paneLayout(
            SplitSpec(strategy = SplitStrategy.AlwaysSplit, preferredAxis = FoldAxis.Horizontal),
        )
        val split = assertIs<PaneLayout.Split>(layout)
        assertEquals(FoldAxis.Horizontal, split.axis)
        assertEquals(445.5f, split.primary.height)
    }

    @Test
    fun coverDisplayStaysSinglePane() {
        assertIs<PaneLayout.Single>(state(FoldPosture.Closed, 260f, 320f).paneLayout())
    }

    // --- local space -------------------------------------------------------------------

    @Test
    fun childLeftOfTheFoldDoesNotSplit() {
        // The bug this guards: window-relative fold coordinates applied to a child that is
        // nowhere near the fold, producing a split at an arbitrary offset inside the child.
        val local = state(FoldPosture.Book, 1280f, 900f, fold(632f, 0f, 648f, 900f))
            .inLocalSpace(0f, 0f, FoldSize(640f, 900f))
        assertIs<PaneLayout.Single>(local.paneLayout())
        assertEquals(1, local.regions.size, "the clipped fold is retained, not dropped")
    }

    @Test
    fun childSpanningTheFoldSplitsInItsOwnCoordinates() {
        val local = state(FoldPosture.Book, 1280f, 900f, fold(632f, 0f, 648f, 900f))
            .inLocalSpace(140f, 50f, FoldSize(1000f, 800f))
        val split = assertIs<PaneLayout.Split>(local.paneLayout())
        assertEquals(FoldRect(0f, 0f, 492f, 800f), split.primary)
        assertEquals(FoldRect(508f, 0f, 1000f, 800f), split.secondary)
    }

    @Test
    fun childFullyPastTheFoldDoesNotSplit() {
        val local = state(FoldPosture.Book, 1280f, 900f, fold(632f, 0f, 648f, 900f))
            .inLocalSpace(648f, 0f, FoldSize(632f, 900f))
        assertIs<PaneLayout.Single>(local.paneLayout())
    }

    @Test
    fun regionsOutsideTheChildAreDropped() {
        val local = state(FoldPosture.Book, 1280f, 900f, fold(1000f, 0f, 1016f, 900f))
            .inLocalSpace(0f, 0f, FoldSize(400f, 900f))
        assertTrue(local.regions.isEmpty())
    }
}
