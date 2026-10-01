package dev.bmcreations.hinge.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bmcreations.hinge.PaneLayout
import dev.bmcreations.hinge.compose.ListDetailPanes
import dev.bmcreations.hinge.compose.LocalPaneLayout
import dev.bmcreations.hinge.compose.rememberOcclusionPadding

/**
 * List/detail, the canonical two-pane case.
 *
 * `ListDetailPanes` decides one pane or two from its own measured bounds, and the app keeps
 * navigation: [selected] is plain app state, and whether a tap replaces the list or fills the
 * neighbouring pane follows from the layout the panes report through `LocalPaneLayout`.
 *
 * Open a note in the Flat posture, then switch to Book: the same detail moves to the trailing
 * pane with its state intact.
 */
@Composable
fun NotesScreen(contentPadding: PaddingValues) {
    var selected by remember { mutableStateOf<Note?>(null) }

    ListDetailPanes(
        showDetail = selected != null,
        // Insets the panes away from any occluding region that reaches a window edge. With
        // the Occlusion posture selected, watch the whole layout shift clear of the strip.
        modifier = Modifier.fillMaxSize().padding(rememberOcclusionPadding()),
        list = {
            val twoPane = LocalPaneLayout.current is PaneLayout.Split
            NoteList(
                selected = selected.takeIf { twoPane },
                onSelect = { selected = it },
                contentPadding = contentPadding,
            )
        },
        detail = {
            val twoPane = LocalPaneLayout.current is PaneLayout.Split
            NoteDetail(
                note = selected,
                onBack = if (twoPane) null else ({ selected = null }),
                contentPadding = contentPadding,
            )
        },
    )
}

@Composable
private fun NoteList(
    selected: Note?,
    onSelect: (Note) -> Unit,
    contentPadding: PaddingValues,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            items(sampleNotes, key = { it.id }) { note ->
                val isSelected = note.id == selected?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(note) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(
                            text = note.subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun NoteDetail(
    note: Note?,
    onBack: (() -> Unit)?,
    contentPadding: PaddingValues,
) {
    val direction = LocalLayoutDirection.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        if (note == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Select a note",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Surface
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = contentPadding.calculateStartPadding(direction) + 20.dp,
                    top = contentPadding.calculateTopPadding() + 20.dp,
                    end = contentPadding.calculateEndPadding(direction) + 20.dp,
                    bottom = contentPadding.calculateBottomPadding() + 20.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack) { Text("Back") }
            }
            Text(note.subtitle.uppercase(), style = MaterialTheme.typography.labelMedium)
            Text(note.title, style = MaterialTheme.typography.headlineSmall)
            Text(note.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
