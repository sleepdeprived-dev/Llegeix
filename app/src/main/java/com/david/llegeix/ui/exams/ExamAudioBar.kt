package com.david.llegeix.ui.exams

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
            positionMs = runCatching { player.currentPosition }.getOrDefault(positionMs)
            delay(POLL_MS)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(
                start = Space.md,
                end = Space.md,
                top = Space.sm,
                bottom = Space.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
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
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = timecode(positionMs) + " / " + timecode(
                        durationMs.takeIf { it > 0 } ?: (track.durationMs?.toInt() ?: 0),
                    ) + if (tracks.size > 1) {
                        " · " + stringResource(
                            R.string.exam_audio_track,
                            index + 1,
                            tracks.size,
                        )
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

        val total = durationMs.takeIf { it > 0 }
        LinearProgressIndicator(
            progress = { if (total == null) 0f else (positionMs.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
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
