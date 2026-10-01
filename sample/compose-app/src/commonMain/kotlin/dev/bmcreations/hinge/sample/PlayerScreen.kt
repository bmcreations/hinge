package dev.bmcreations.hinge.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bmcreations.hinge.FoldAxis
import dev.bmcreations.hinge.PaneLayout
import dev.bmcreations.hinge.compose.FoldAwarePanes
import dev.bmcreations.hinge.compose.LocalPaneLayout
import dev.bmcreations.hinge.compose.rememberOcclusionPadding
import kotlinx.coroutines.delay

/**
 * A media player, the case where the *shape* of the split matters and not only whether there
 * is one.
 *
 * | Layout | Leading / top pane | Trailing / bottom pane |
 * |---|---|---|
 * | Book, or a wide flat window | video and transport controls | the queue |
 * | Tabletop | video, filling the raised half | controls and queue on the half lying flat |
 * | Single | video, controls, then the queue below | — |
 * | Single, short (cover display) | video and controls only | — |
 *
 * Each pane reads [LocalPaneLayout] for the axis, so it arranges itself from the geometry it
 * was measured with. Playback state is hoisted above the panes: a real app would keep its
 * player (ExoPlayer, AVPlayer) outside composition for the same reason, and attach the video
 * surface wherever the layout puts it.
 */
@Composable
fun PlayerScreen(contentPadding: PaddingValues) {
    val player = remember { PlayerState() }

    LaunchedEffect(player.playing, player.index) {
        while (player.playing) {
            delay(TICK_MILLIS)
            player.advance(TICK_MILLIS / 1000f)
        }
    }

    FoldAwarePanes(
        modifier = Modifier.fillMaxSize().padding(rememberOcclusionPadding()),
        primary = {
            val layout = LocalPaneLayout.current
            val padding = contentPadding.forPane(layout, leading = true)
            when {
                layout.isTabletop -> VideoStage(
                    player = player,
                    modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
                )
                layout is PaneLayout.Split -> Column(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // The video takes what the controls leave, so a short pane (a phone in
                    // landscape) shrinks the video instead of pushing the controls off screen.
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        VideoStage(player, Modifier.aspectRatio(16f / 9f, matchHeightConstraintsFirst = true))
                    }
                    NowPlaying(player)
                    Transport(player)
                }
                else -> SinglePane(player, padding)
            }
        },
        secondary = {
            val layout = LocalPaneLayout.current
            val padding = contentPadding.forPane(layout, leading = false)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
            ) {
                if (layout.isTabletop) {
                    item {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            NowPlaying(player)
                            Transport(player)
                        }
                    }
                }
                queue(player)
            }
        },
    )
}

/** One pane: everything stacked, with the queue dropped when there is no room for it. */
@Composable
private fun SinglePane(player: PlayerState, padding: PaddingValues) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < COMPACT_HEIGHT
        val videoMaxHeight = maxHeight * VIDEO_MAX_HEIGHT_FRACTION
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Column(
                    Modifier.padding(if (compact) 8.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp),
                ) {
                    // Capped by height so a short, wide pane (a phone in landscape, or Tabletop
                    // too short to split) keeps the controls on screen.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        VideoStage(
                            player,
                            Modifier
                                .heightIn(max = videoMaxHeight)
                                .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true),
                        )
                    }
                    NowPlaying(player, compact = compact)
                    Transport(player)
                }
            }
            if (!compact) queue(player)
        }
    }
}

private val PaneLayout?.isTabletop: Boolean
    get() = this is PaneLayout.Split && axis == FoldAxis.Horizontal

/**
 * The demo's overlays sit at the top and bottom of the window. Side by side, both panes span
 * the full height and need both; stacked, the top pane only meets the top overlay and the
 * bottom pane only the bottom one.
 */
@Composable
private fun PaddingValues.forPane(layout: PaneLayout?, leading: Boolean): PaddingValues {
    val direction = LocalLayoutDirection.current
    val stacked = layout.isTabletop
    return PaddingValues(
        start = calculateStartPadding(direction),
        end = calculateEndPadding(direction),
        top = if (stacked && !leading) 0.dp else calculateTopPadding(),
        bottom = if (stacked && leading) 0.dp else calculateBottomPadding(),
    )
}

/** Stands in for a video surface: the track's artwork colour, its title, and play/pause. */
@Composable
private fun VideoStage(player: PlayerState, modifier: Modifier) {
    val track = player.track
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(track.color, track.color.copy(alpha = 0.45f), Color.Black)))
            .clickable { player.playing = !player.playing },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (player.playing) "❚❚" else "▶",
            style = MaterialTheme.typography.displaySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
        Text(
            text = "${formatTime(player.positionSeconds)} / ${formatTime(track.durationSeconds.toFloat())}",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
        )
    }
}

@Composable
private fun NowPlaying(player: PlayerState, compact: Boolean = false) {
    Column {
        Text(
            text = player.track.title,
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = player.track.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun Transport(player: PlayerState) {
    Column {
        Slider(
            value = player.positionSeconds,
            onValueChange = { player.positionSeconds = it },
            valueRange = 0f..player.track.durationSeconds.toFloat(),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = player::previous) { Text("⏮") }
            FilledIconButton(onClick = { player.playing = !player.playing }, modifier = Modifier.size(56.dp)) {
                Text(if (player.playing) "❚❚" else "▶")
            }
            IconButton(onClick = player::next) { Text("⏭") }
        }
    }
}

private fun LazyListScope.queue(player: PlayerState) {
    item {
        Text(
            "Up next",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
    }
    itemsIndexed(Tracks, key = { _, track -> track.id }) { index, track ->
        val current = index == player.index
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { player.select(index) }
                .background(if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp, 27.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(track.color),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatTime(track.durationSeconds.toFloat()), style = MaterialTheme.typography.labelMedium)
        }
    }
    item { Spacer(Modifier.height(16.dp)) }
}

@Stable
private class PlayerState {
    var index by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
    var positionSeconds by mutableFloatStateOf(0f)

    val track: Track get() = Tracks[index]

    fun select(index: Int) {
        this.index = index
        positionSeconds = 0f
        playing = true
    }

    fun next() = select((index + 1) % Tracks.size)

    /** Like most players: restart the track unless it has only just begun. */
    fun previous() {
        if (positionSeconds > 3f) positionSeconds = 0f else select((index - 1 + Tracks.size) % Tracks.size)
    }

    fun advance(seconds: Float) {
        val next = positionSeconds + seconds
        if (next >= track.durationSeconds) next() else positionSeconds = next
    }
}

private data class Track(
    val id: Int,
    val title: String,
    val artist: String,
    val durationSeconds: Int,
    val color: Color,
)

private val Tracks = listOf(
    Track(1, "Folding Light", "The Hinges", 214, Color(0xFF3D5AFE)),
    Track(2, "Tabletop Session", "Crease", 187, Color(0xFF00897B)),
    Track(3, "Half Open", "Book Posture", 242, Color(0xFFD81B60)),
    Track(4, "Seam", "Occlusion Band", 199, Color(0xFFF4511E)),
    Track(5, "Cover Display", "The Hinges", 163, Color(0xFF8E24AA)),
    Track(6, "Reserved Regions", "Crease", 228, Color(0xFF6D4C41)),
)

private fun formatTime(seconds: Float): String {
    val total = seconds.toInt()
    val s = total % 60
    return "${total / 60}:${if (s < 10) "0" else ""}$s"
}

private const val TICK_MILLIS = 250L
private val COMPACT_HEIGHT = 420.dp

private const val VIDEO_MAX_HEIGHT_FRACTION = 0.5f
