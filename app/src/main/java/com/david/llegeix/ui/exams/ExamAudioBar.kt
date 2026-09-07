package com.david.llegeix.ui.exams

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.david.llegeix.R
import com.david.llegeix.data.db.entity.ExamAudioEntity
import com.david.llegeix.ui.common.Space
import kotlinx.coroutines.delay
import java.io.File

/**
 * The listening section, playing above the page.
 *
 * A bar rather than a screen of its own, and that is the whole point: a
 * listening exercise is answered *while* it plays, so anything that covered the
 * page would make the feature useless. It is only drawn when the paper actually
 * has recordings attached.
 *
 * [MediaPlayer] rather than anything larger. One file at a time, from local
 * storage, with play, pause and a position — there is nothing here that would
 * repay a media session, a service or a third-party player, and each of those
 * would bring a notification and a foreground service the reader never asked
 * for.
 *
 * ### The scrubber
 *
 * It used to be a [androidx.compose.material3.LinearProgressIndicator]: a
 * hairline that reported where the track had got to and could not be touched.
 * That is the wrong control for a listening exam, where the thing everybody
 * does is play the same fifteen seconds four times — and with no way back, the
 * only way to hear a sentence again was to start the whole track over. It is
 * now a slider: drag it, or tap anywhere along it, and the recording goes
 * there.
 *
 * While the finger is down the bar follows the finger rather than the player,
 * because a scrubber that keeps jumping back to where the audio still is under
 * a dragging thumb is a scrubber that fights whoever is using it. The seek
 * itself happens once, on release.
 */
@Composable
fun ExamAudioBar(
    tracks: List<ExamAudioEntity>,
    fileOf: (String) -> File,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) return

    var index by remember(tracks.size) { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    // Non-null while a finger is on the scrubber: the position it is showing,
    // which is not yet the position the player is at.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val track = tracks.getOrNull(index) ?: return

    // One player for the life of the bar, re-pointed when the track changes.
    val player = remember { MediaPlayer() }

    DisposableEffect(player) {
        onDispose {
            // Released rather than merely stopped. A MediaPlayer holds a codec,
            // and there is a small pool of them on the device: leaking one is
            // how an app makes every *other* app's audio stop working.
            runCatching { player.reset() }
            runCatching { player.release() }
        }
    }

    LaunchedEffect(track.id) {
        playing = false
        positionMs = 0
        scrubbing = false
        runCatching {
            player.reset()
            player.setDataSource(fileOf(track.fileName).absolutePath)
            player.prepare()
            durationMs = player.duration
        }.onFailure { durationMs = 0 }
        player.setOnCompletionListener {
            playing = false
            positionMs = 0
        }
    }

    // The position is polled while playing rather than pushed: MediaPlayer has
    // no progress callback, and four ticks a second is smooth enough to follow
    // without waking the CPU for a bar nobody is staring at.
    LaunchedEffect(playing) {
        while (playing) {
            if (!scrubbing) {
                positionMs = runCatching { player.currentPosition }.getOrDefault(positionMs)
            }
            delay(POLL_MS)
        }
    }

    val total = durationMs.takeIf { it > 0 } ?: track.durationMs?.toInt()?.takeIf { it > 0 }
    val fraction = when {
        scrubbing -> scrubFraction
        total == null -> 0f
        else -> (positionMs.toFloat() / total).coerceIn(0f, 1f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = Space.md, end = Space.md, top = Space.sm, bottom = Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A filled disc rather than a bare glyph. It is the one control on
            // this bar that is pressed in the middle of answering a question,
            // so it is the one that should be findable without looking.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(PlayButtonSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                IconButton(
                    onClick = {
                        runCatching {
                            if (player.isPlaying) {
                                player.pause()
                                playing = false
                            } else {
                                player.start()
                                playing = true
                            }
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(
                            if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                        ),
                        contentDescription = stringResource(
                            if (playing) R.string.exam_pause else R.string.exam_play,
                        ),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Space.md),
            ) {
                Text(
                    text = track.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Elapsed and remaining, in the tabular places a player
                    // puts them, so the numbers do not move as they tick.
                    Text(
                        text = timecode(if (scrubbing && total != null) {
                            (scrubFraction * total).toInt()
                        } else {
                            positionMs
                        }) + " / " + timecode(total ?: 0),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (tracks.size > 1) {
                        Text(
                            text = " · " + stringResource(
                                R.string.exam_audio_track,
                                index + 1,
                                tracks.size,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (tracks.size > 1) {
                IconButton(
                    onClick = {
                        playing = false
                        runCatching { player.pause() }
                        index = (index + 1) % tracks.size
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_up),
                        contentDescription = stringResource(R.string.exam_next_track),
                        modifier = Modifier.rotate(90f),
                    )
                }
            }
        }

        val seekLabel = stringResource(R.string.exam_seek)
        Slider(
            value = fraction,
            // Disabled where nothing said how long the file is: a scrubber over
            // an unknown length has no position to drag to.
            enabled = total != null,
            onValueChange = { value ->
                scrubbing = true
                scrubFraction = value
            },
            onValueChangeFinished = {
                val length = total
                if (length != null) {
                    val target = (scrubFraction * length).toInt().coerceIn(0, length)
                    runCatching { player.seekTo(target) }
                    positionMs = target
                }
                scrubbing = false
            },
            colors = SliderDefaults.colors(
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = seekLabel },
        )
    }
}

/** "3:07". Minutes and seconds, which is the length a listening track has. */
private fun timecode(millis: Int): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private const val POLL_MS = 250L

/** Big enough to hit without looking, small enough to stay a bar. */
private val PlayButtonSize = 44.dp
