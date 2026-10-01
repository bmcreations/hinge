package dev.bmcreations.hinge.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.bmcreations.hinge.PaneLayout
import dev.bmcreations.hinge.Postures
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A flat window splits once it is wide enough for two minimum-size panes, so these tests move
 * between one and two panes by resizing the host, which is what a rotation or a window resize
 * does on a device.
 */
@OptIn(ExperimentalTestApi::class)
class PaneHostTest {

    private val narrow = 300.dp
    private val wide = 800.dp

    @Test
    fun detailKeepsPlainRememberStateWhenTheLayoutSplits() = runComposeUiTest {
        var width by mutableStateOf(narrow)
        setContent {
            Box(Modifier.size(width, 600.dp)) {
                ListDetailPanes(
                    showDetail = true,
                    state = Postures.flat(800f, 600f),
                    list = { BasicText("list") },
                    detail = { Counter("detail") },
                )
            }
        }

        onNodeWithText("detail: 0").performClick()
        onNodeWithText("detail: 1").performClick()
        width = wide

        onNodeWithText("detail: 2").assertIsDisplayed()
        onNodeWithText("list").assertIsDisplayed()
    }

    @Test
    fun detailKeepsPlainRememberStateWhenTheLayoutCollapses() = runComposeUiTest {
        var width by mutableStateOf(wide)
        setContent {
            Box(Modifier.size(width, 600.dp)) {
                ListDetailPanes(
                    showDetail = true,
                    state = Postures.flat(800f, 600f),
                    list = { BasicText("list") },
                    detail = { Counter("detail") },
                )
            }
        }

        onNodeWithText("detail: 0").performClick()
        width = narrow

        onNodeWithText("detail: 1").assertIsDisplayed()
        onNodeWithText("list").assertDoesNotExist()
    }

    @Test
    fun listKeepsSaveableStateWhileTheDetailCoversIt() = runComposeUiTest {
        var showDetail by mutableStateOf(false)
        setContent {
            Box(Modifier.size(narrow, 600.dp)) {
                ListDetailPanes(
                    showDetail = showDetail,
                    state = Postures.flat(300f, 600f),
                    list = { SaveableCounter("list") },
                    detail = { BasicText("detail") },
                )
            }
        }

        onNodeWithText("list: 0").performClick()
        onNodeWithText("list: 1").performClick()
        onNodeWithText("list: 2").performClick()
        showDetail = true
        onNodeWithText("detail").assertIsDisplayed()
        showDetail = false

        onNodeWithText("list: 3").assertIsDisplayed()
    }

    @Test
    fun primaryMovesToTheRightUnderRtl() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.size(wide, 600.dp)) {
                    FoldAwarePanes(
                        state = Postures.book(800f, 600f),
                        primary = { Box(Modifier.fillMaxSize().testTag("primary")) },
                        secondary = { Box(Modifier.fillMaxSize().testTag("secondary")) },
                    )
                }
            }
        }

        val primary = onNodeWithTag("primary").getBoundsInRoot()
        val secondary = onNodeWithTag("secondary").getBoundsInRoot()
        assertTrue(primary.left > secondary.left, "primary $primary, secondary $secondary")
    }

    @Test
    fun rtlCanBeLeftUnmirrored() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.size(wide, 600.dp)) {
                    FoldAwarePanes(
                        state = Postures.book(800f, 600f),
                        mirrorInRtl = false,
                        primary = { Box(Modifier.fillMaxSize().testTag("primary")) },
                        secondary = { Box(Modifier.fillMaxSize().testTag("secondary")) },
                    )
                }
            }
        }

        val primary = onNodeWithTag("primary").getBoundsInRoot()
        val secondary = onNodeWithTag("secondary").getBoundsInRoot()
        assertTrue(primary.left < secondary.left, "primary $primary, secondary $secondary")
    }

    @Test
    fun panesSeeTheLayoutTheyWereMeasuredWith() = runComposeUiTest {
        var width by mutableStateOf(narrow)
        setContent {
            Box(Modifier.size(width, 600.dp)) {
                // The state claims a wide window; the panes are narrow. LocalPaneLayout has to
                // report what the panes actually got.
                FoldAwarePanes(
                    state = Postures.flat(800f, 600f),
                    primary = {
                        val split = LocalPaneLayout.current is PaneLayout.Split
                        BasicText(if (split) "primary split" else "primary single")
                    },
                    secondary = { BasicText("secondary") },
                )
            }
        }

        onNodeWithText("primary single").assertIsDisplayed()
        width = wide
        onNodeWithText("primary split").assertIsDisplayed()
    }
}

@androidx.compose.runtime.Composable
private fun Counter(label: String) {
    var count by remember { mutableStateOf(0) }
    BasicText("$label: $count", Modifier.clickable { count++ })
}

@androidx.compose.runtime.Composable
private fun SaveableCounter(label: String) {
    var count by rememberSaveable { mutableStateOf(0) }
    BasicText("$label: $count", Modifier.clickable { count++ })
}
