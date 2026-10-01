package dev.bmcreations.hinge.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bmcreations.hinge.FoldAxis
import dev.bmcreations.hinge.PaneLayout
import dev.bmcreations.hinge.compose.FoldAwarePanes
import dev.bmcreations.hinge.compose.LocalPaneLayout
import dev.bmcreations.hinge.compose.rememberOcclusionPadding
import kotlinx.coroutines.delay

/**
 * A streaming-app episode player, the case where the *shape* of the split matters and not only
 * whether there is one.
 *
 * | Layout | Leading / top pane | Trailing / bottom pane |
 * |---|---|---|
 * | Book, or a wide flat window | video with its controls, then the episode details | up next |
 * | Tabletop | video alone, filling the raised half | the control deck, then up next |
 * | Single | video, details, then up next below | — |
 * | Single, short (cover display) | video and details only | — |
 *
 * In Tabletop the controls move off the video and onto the half lying flat, where a thumb can
 * reach them without covering the picture. Each pane reads [LocalPaneLayout] for the axis, so
 * it arranges itself from the geometry it was measured with. Playback state is hoisted above the
 * panes: a real app would keep its player (ExoPlayer, AVPlayer) outside composition for the same
 * reason, and attach the video surface wherever the layout puts it.
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
    // Like most players, the overlay hides itself a few seconds after the last touch.
    LaunchedEffect(player.controlsVisible, player.playing, player.lastTouch) {
        if (player.controlsVisible && player.playing) {
            delay(CONTROLS_TIMEOUT_MILLIS)
            player.controlsVisible = false
        }
    }

    // Streaming apps stay dark whatever the system theme, so the picture is the brightest thing.
    MaterialTheme(colorScheme = PlayerColors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            FoldAwarePanes(
                modifier = Modifier.fillMaxSize().padding(rememberOcclusionPadding(reserved = contentPadding)),
                primary = {
                    val layout = LocalPaneLayout.current
                    val padding = contentPadding.forPane(layout, leading = true)
                    when {
                        // Letterboxed, as a real 16:9 picture would be on the wide raised half.
                        layout.isTabletop -> Box(
                            Modifier.fillMaxSize().padding(padding).padding(12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            VideoStage(
                                player = player,
                                overlay = false,
                                modifier = Modifier.aspectRatio(16f / 9f, matchHeightConstraintsFirst = true),
                            )
                        }
                        layout is PaneLayout.Split -> Column(
                            Modifier.fillMaxSize().padding(padding).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            // The video sits at the top and is capped by what the details leave,
                            // so a short pane (a phone in landscape) shrinks the video instead of
                            // pushing text off screen.
                            Box(Modifier.weight(1f, fill = false).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                VideoStage(
                                    player,
                                    overlay = true,
                                    modifier = Modifier.aspectRatio(16f / 9f, matchHeightConstraintsFirst = true),
                                )
                            }
                            EpisodeDetails(player)
                        }
                        else -> SinglePane(player, padding)
                    }
                },
                secondary = {
                    val layout = LocalPaneLayout.current
                    val padding = contentPadding.forPane(layout, leading = false)
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                        if (layout.isTabletop) {
                            item { ControlDeck(player, Modifier.padding(16.dp)) }
                        }
                        upNext(player)
                    }
                },
            )
        }
    }
}

/** One pane: everything stacked, with up next dropped when there is no room for it. */
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
                    // too short to split) keeps the details on screen.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        VideoStage(
                            player,
                            overlay = true,
                            modifier = Modifier
                                .heightIn(max = videoMaxHeight)
                                .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true),
                        )
                    }
                    EpisodeDetails(player, compact = compact)
                }
            }
            if (!compact) upNext(player)
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

/**
 * Stands in for a video surface: the episode's artwork colour, captions, and, when [overlay] is
 * true, the controls drawn over the picture. Tapping toggles the overlay; without one (Tabletop,
 * where the controls live on the other pane) a thin progress line runs along the bottom edge.
 */
@Composable
private fun VideoStage(player: PlayerState, overlay: Boolean, modifier: Modifier) {
    val episode = player.episode
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .background(
                Brush.verticalGradient(listOf(episode.color.copy(alpha = 0.9f), episode.color.copy(alpha = 0.35f), Color.Black)),
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                player.touch()
                player.controlsVisible = !player.controlsVisible
            },
    ) {
        if (player.captions && player.playing) {
            Text(
                text = episode.caption,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (overlay && player.controlsVisible) 64.dp else 16.dp, start = 24.dp, end = 24.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        if (overlay) {
            AnimatedVisibility(
                visible = player.controlsVisible || !player.playing,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                VideoOverlay(player)
            }
        } else {
            ProgressLine(player, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Top bar, centred transport, and scrubber, over a scrim — the layout most streaming apps share. */
@Composable
private fun VideoOverlay(player: PlayerState) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
        // On the cover display the video is only ~100dp tall; keep just the centre button.
        val roomy = maxHeight >= 150.dp
        if (roomy) {
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                OverlayButton(PlayerIcons.ChevronDown, "Minimise") { player.touch() }
                Spacer(Modifier.weight(1f))
                OverlayButton(PlayerIcons.ClosedCaption, "Captions", selected = player.captions) {
                    player.touch()
                    player.captions = !player.captions
                }
                OverlayButton(PlayerIcons.MoreVert, "More") { player.touch() }
            }
        }
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(if (roomy) 32.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (roomy) SeekButton(forward = false, player = player, size = 40.dp)
            PlayPauseButton(player, size = if (roomy) 64.dp else 44.dp, filled = false)
            if (roomy) SeekButton(forward = true, player = player, size = 40.dp)
        }
        if (roomy) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${formatTime(player.positionSeconds)} / ${formatTime(player.episode.durationSeconds.toFloat())}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
                Scrubber(player, Modifier.weight(1f).padding(horizontal = 8.dp))
                OverlayButton(PlayerIcons.Fullscreen, "Full screen") { player.touch() }
            }
        }
    }
}

/**
 * Tabletop's flat half: the controls the overlay would otherwise draw on the picture, at a size
 * meant for a thumb, plus what's playing.
 */
@Composable
private fun ControlDeck(player: PlayerState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EpisodeTitle(player)
        Scrubber(player, Modifier.fillMaxWidth().padding(top = 4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(player.positionSeconds), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(
                "-" + formatTime(player.episode.durationSeconds - player.positionSeconds),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = player::previous) { Icon(PlayerIcons.SkipPrevious, "Previous episode") }
            SeekButton(forward = false, player = player, size = 44.dp)
            PlayPauseButton(player, size = 64.dp, filled = true)
            SeekButton(forward = true, player = player, size = 44.dp)
            IconButton(onClick = player::next) { Icon(PlayerIcons.SkipNext, "Next episode") }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            AssistChip(
                onClick = player::cycleSpeed,
                label = { Text("${formatSpeed(player.speed)}×") },
            )
            AssistChip(
                onClick = { player.captions = !player.captions },
                label = { Text("Captions") },
                leadingIcon = { Icon(PlayerIcons.ClosedCaption, null, Modifier.size(AssistChipDefaults.IconSize)) },
                colors = if (player.captions) {
                    AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    AssistChipDefaults.assistChipColors()
                },
            )
        }
    }
}

@Composable
private fun EpisodeTitle(player: PlayerState, compact: Boolean = false) {
    val episode = player.episode
    Column {
        Text(
            text = Show.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
        )
        Text(
            text = episode.title,
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "S1 E${episode.number} · ${episode.durationSeconds / 60} min",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Title, synopsis and speed — what sits under the video when it carries its own controls. */
@Composable
private fun EpisodeDetails(player: PlayerState, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { EpisodeTitle(player, compact) }
            if (!compact) {
                AssistChip(onClick = player::cycleSpeed, label = { Text("${formatSpeed(player.speed)}×") })
            }
        }
        if (!compact) {
            Text(
                player.episode.synopsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun LazyListScope.upNext(player: PlayerState) {
    item {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("Up next", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(
                "${player.index + 1}/${Episodes.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    itemsIndexed(Episodes, key = { _, episode -> episode.number }) { index, episode ->
        EpisodeRow(
            episode = episode,
            label = when (index) {
                player.index -> "NOW PLAYING"
                (player.index + 1) % Episodes.size -> "UP NEXT"
                else -> null
            },
            progress = if (index == player.index) player.progress else player.watched[index],
            current = index == player.index,
            onClick = { player.select(index) },
        )
    }
    item { Spacer(Modifier.height(16.dp)) }
}

/** Thumbnail with a duration badge and a watched bar, the row shape every streaming queue uses. */
@Composable
private fun EpisodeRow(episode: Episode, label: String?, progress: Float, current: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (current) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(112.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(episode.color, Color.Black))),
        ) {
            Text(
                formatTime(episode.durationSeconds.toFloat()),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
            if (progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.3f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
            Text(
                "${episode.number}. ${episode.title}",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${episode.durationSeconds / 60} min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A thin track with a small round thumb, rather than Material's chunkier default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Scrubber(player: PlayerState, modifier: Modifier = Modifier) {
    val colors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
    )
    Slider(
        value = player.positionSeconds,
        onValueChange = {
            player.touch()
            player.positionSeconds = it
        },
        valueRange = 0f..player.episode.durationSeconds.toFloat(),
        colors = colors,
        thumb = { Box(Modifier.size(14.dp).background(Color.White, CircleShape)) },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                colors = colors,
                drawStopIndicator = null,
                thumbTrackGapSize = 0.dp,
                modifier = Modifier.height(4.dp),
            )
        },
        modifier = modifier.height(32.dp),
    )
}

@Composable
private fun ProgressLine(player: PlayerState, modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(player.progress).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun PlayPauseButton(player: PlayerState, size: Dp, filled: Boolean) {
    val icon = if (player.playing) PlayerIcons.Pause else PlayerIcons.Play
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.Transparent)
            .clickable {
                player.touch()
                player.playing = !player.playing
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = if (player.playing) "Pause" else "Play",
            tint = if (filled) Color.Black else Color.White,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

/** The circular-arrow 10-second skip. Forward is the replay icon mirrored. */
@Composable
private fun SeekButton(forward: Boolean, player: PlayerState, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable { player.seekBy(if (forward) SEEK_SECONDS else -SEEK_SECONDS) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            PlayerIcons.Replay,
            contentDescription = if (forward) "Forward 10 seconds" else "Back 10 seconds",
            tint = Color.White,
            modifier = Modifier.fillMaxSize().graphicsLayer { if (forward) scaleX = -1f },
        )
        Text(
            "10",
            color = Color.White,
            fontSize = (size.value * 0.24f).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = size * 0.12f),
        )
    }
}

@Composable
private fun OverlayButton(icon: ImageVector, description: String, selected: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, description, tint = if (selected) MaterialTheme.colorScheme.primary else Color.White)
    }
}

@Stable
private class PlayerState {
    var index by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
    var positionSeconds by mutableFloatStateOf(0f)
    var speed by mutableFloatStateOf(1f)
        private set
    var captions by mutableStateOf(false)

    /** Hoisted with the rest so the overlay doesn't reappear when the video moves panes. */
    var controlsVisible by mutableStateOf(true)
    var lastTouch by mutableIntStateOf(0)
        private set

    /** How far through each episode the viewer got, for the queue's watched bars. */
    val watched = mutableStateListOf(*Episodes.map { it.startingProgress }.toTypedArray())

    val episode: Episode get() = Episodes[index]
    val progress: Float get() = positionSeconds / episode.durationSeconds

    fun touch() {
        lastTouch++
    }

    fun select(index: Int) {
        watched[this.index] = progress
        this.index = index
        positionSeconds = 0f
        playing = true
    }

    fun next() = select((index + 1) % Episodes.size)

    /** Restart the episode unless it has only just begun. */
    fun previous() {
        if (positionSeconds > 3f) positionSeconds = 0f else select((index - 1 + Episodes.size) % Episodes.size)
    }

    fun seekBy(seconds: Float) {
        touch()
        positionSeconds = (positionSeconds + seconds).coerceIn(0f, episode.durationSeconds.toFloat())
    }

    fun cycleSpeed() {
        speed = Speeds[(Speeds.indexOf(speed) + 1) % Speeds.size]
    }

    fun advance(seconds: Float) {
        val next = positionSeconds + seconds * speed
        if (next >= episode.durationSeconds) {
            watched[index] = 1f
            next()
        } else {
            positionSeconds = next
        }
    }
}

private data class Episode(
    val number: Int,
    val title: String,
    val synopsis: String,
    val caption: String,
    val durationSeconds: Int,
    val color: Color,
    val startingProgress: Float = 0f,
)

private const val Show = "Field Notes"

private val Episodes = listOf(
    Episode(
        1, "The Long Thaw",
        "A glaciologist returns to the ice field she mapped twenty years ago and finds the camp site underwater.",
        "[ice cracking in the distance]", 2_580, Color(0xFF4F7CAC), startingProgress = 1f,
    ),
    Episode(
        2, "Tide Tables",
        "On a tidal island cut off twice a day, the postmistress keeps a ledger of every crossing since 1962.",
        "We go when the sand says we can.", 2_340, Color(0xFF2E8B7A), startingProgress = 0.35f,
    ),
    Episode(
        3, "Basalt",
        "Quarry workers and a volcanologist read the same cliff face for very different reasons.",
        "[hammer on stone]", 2_710, Color(0xFF6B5B95),
    ),
    Episode(
        4, "Night Shift",
        "A rural observatory runs on volunteers, a kettle, and one telescope older than everyone using it.",
        "Clouds by midnight. Maybe.", 2_460, Color(0xFF283C63),
    ),
    Episode(
        5, "The Quiet Valley",
        "Sound recordists spend a week trying to capture a minute without engines.",
        "[birdsong, then a distant plane]", 2_220, Color(0xFF8A9A5B),
    ),
    Episode(
        6, "Return",
        "The season's subjects revisit the places they showed us, a year on.",
        "It looks smaller than I remember.", 2_900, Color(0xFFB5654A),
    ),
)

private val PlayerColors = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    background = Color(0xFF0B0B0D),
    surface = Color(0xFF0B0B0D),
    surfaceContainerHigh = Color(0xFF1C1C21),
    secondaryContainer = Color(0xFF1E3A47),
)

private val Speeds = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()

private fun formatTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val ss = if (s < 10) "0$s" else "$s"
    return if (h > 0) "$h:${if (m < 10) "0$m" else "$m"}:$ss" else "$m:$ss"
}

private const val TICK_MILLIS = 250L
private const val CONTROLS_TIMEOUT_MILLIS = 3_000L
private const val SEEK_SECONDS = 10f
private val COMPACT_HEIGHT = 420.dp
private const val VIDEO_MAX_HEIGHT_FRACTION = 0.5f
