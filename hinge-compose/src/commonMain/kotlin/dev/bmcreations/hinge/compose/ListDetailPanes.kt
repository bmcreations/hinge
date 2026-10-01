package dev.bmcreations.hinge.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import dev.bmcreations.hinge.FoldingState
import dev.bmcreations.hinge.PaneLayout
import dev.bmcreations.hinge.SplitSpec

private enum class ListDetailSlot { List, Detail }

/**
 * List/detail around the fold: both side by side when the layout splits, one at a time when it
 * does not.
 *
 * | Layout | [showDetail] | Shows |
 * |---|---|---|
 * | Split | either | [list] and [detail] |
 * | Single | `false` | [list] |
 * | Single | `true` | [detail] |
 *
 * This exists because the obvious hand-rolled version, a [FoldAwarePanes] whose primary slot
 * swaps between list and detail, has two bugs:
 *
 * 1. **State loss.** The detail moves between slots when the device folds or unfolds, so it is
 *    torn down and rebuilt and loses scroll position, text input and anything else it held.
 *    Here each piece of content keeps one subcomposition for as long as it stays on screen, so
 *    the detail keeps all of its state across the change. Content that leaves the screen keeps
 *    its `rememberSaveable` state for when it comes back.
 * 2. **Disagreeing geometry.** Deciding single versus split from [rememberPaneLayout] reads the
 *    window, while the panes read their own bounds; when the two disagree the detail can become
 *    unreachable. Here one measurement makes both decisions.
 *
 * Navigation stays with the app: [showDetail] is the app's selection state. Inside [detail],
 * read [LocalPaneLayout] to decide whether to show a back affordance or handle back:
 *
 * ```
 * ListDetailPanes(
 *     showDetail = selected != null,
 *     list = { ConversationList(onSelect = { selected = it }) },
 *     detail = {
 *         val twoPane = LocalPaneLayout.current is PaneLayout.Split
 *         BackHandler(enabled = !twoPane) { selected = null }
 *         ConversationDetail(selected, showBack = !twoPane)
 *     },
 * )
 * ```
 *
 * [detail] is composed whenever the layout splits, including when [showDetail] is `false`, so
 * it should render an empty state in that case.
 */
@Composable
public fun ListDetailPanes(
    showDetail: Boolean,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    state: FoldingState = LocalFoldingState.current,
    spec: SplitSpec = SplitSpec(),
    mirrorInRtl: Boolean = true,
) {
    val saveable = rememberSaveableStateHolder()
    val listPane = PaneContent(ListDetailSlot.List) {
        saveable.SaveableStateProvider(ListDetailSlot.List, list)
    }
    val detailPane = PaneContent(ListDetailSlot.Detail) {
        saveable.SaveableStateProvider(ListDetailSlot.Detail, detail)
    }

    PaneHost(
        modifier = modifier,
        state = state,
        spec = spec,
        mirrorInRtl = mirrorInRtl,
        onPaneLayoutChanged = null,
    ) { layout ->
        when {
            layout is PaneLayout.Split -> listPane to detailPane
            showDetail -> detailPane to null
            else -> listPane to null
        }
    }
}
