package com.example.offlinesubplayer

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.offlinesubplayer.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun OfflineSubtitlePlayerScreen(vm: VideoPlayerViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val state by vm.uiState.collectAsStateWithLifecycle()

    var pickingSource by remember { mutableStateOf(false) }
    var pickingTarget by remember { mutableStateOf(false) }
    var showingModeSettings by remember { mutableStateOf(false) }
    var showingPackManager by remember { mutableStateOf(false) }
    var suggestedExportName by remember { mutableStateOf("subtitle_id.srt") }

    var seekPreviewMs by remember { mutableStateOf<Long?>(null) }
    var brightnessLevel by remember { mutableFloatStateOf(0.7f) }
    var volumePercent by remember { mutableIntStateOf(50) }
    var activeGestureLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activity) {
        val currentWinBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
        if (currentWinBrightness >= 0f) {
            brightnessLevel = currentWinBrightness.coerceIn(0.08f, 1.0f)
        } else {
            val sysBrightness = runCatching {
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                )
            }.getOrDefault(180)
            brightnessLevel = (sysBrightness / 255f).coerceIn(0.08f, 1.0f)
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(state.videoUri) {
        state.videoUri?.let { uri ->
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            vm.onPlaybackTick(
                positionMs = exoPlayer.currentPosition,
                durationMs = exoPlayer.duration,
                isPlaying = exoPlayer.isPlaying
            )
            delay(150L)
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { vm.setVideoUri(it) }
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { vm.loadSubtitleFromUri(context, it) }
    }

    val saveSubtitleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri ->
        uri?.let { vm.exportTranslatedSrt(context, it, suggestedExportName) }
    }

    if (pickingSource) {
        LanguagePickerDialog(
            title = stringResource(R.string.title_pick_source),
            selectedCode = state.sourceLang,
            downloadedCodes = state.downloadedLanguages,
            onDismiss = { pickingSource = false },
            onSelect = { code -> vm.changeLanguagePair(code, state.targetLang) }
        )
    }

    if (pickingTarget) {
        LanguagePickerDialog(
            title = stringResource(R.string.title_pick_target),
            selectedCode = state.targetLang,
            downloadedCodes = state.downloadedLanguages,
            onDismiss = { pickingTarget = false },
            onSelect = { code -> vm.changeLanguagePair(state.sourceLang, code) }
        )
    }

    if (showingModeSettings) {
        TranslationSettingsDialog(
            currentMode = state.translationMode,
            currentApiKey = state.geminiApiKey,
            downloadedCount = state.downloadedLanguages.size,
            onDismiss = { showingModeSettings = false },
            onSelectMode = { mode -> vm.setTranslationMode(mode) },
            onSaveApiKey = { key -> vm.updateGeminiApiKey(key) },
            onOpenPackManager = {
                vm.refreshDownloadedLanguages()
                showingPackManager = true
            }
        )
    }

    if (showingPackManager) {
        LanguagePackManagerDialog(
            downloadedCodes = state.downloadedLanguages,
            downloadingCodes = state.downloadingLanguages,
            onDismiss = { showingPackManager = false },
            onDownloadPack = { code -> vm.downloadLanguagePack(code) },
            onDeletePack = { code -> vm.deleteLanguagePack(code) }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable { vm.toggleControls() }
            .pointerInput(state.durationMs) {
                var accumulatedSeekDeltaMs = 0L
                detectHorizontalDragGestures(
                    onDragStart = {
                        accumulatedSeekDeltaMs = 0L
                        seekPreviewMs = exoPlayer.currentPosition
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (state.durationMs > 0L) {
                            accumulatedSeekDeltaMs += (dragAmount * 100f).toLong()
                            val target = (exoPlayer.currentPosition + accumulatedSeekDeltaMs)
                                .coerceIn(0L, state.durationMs)
                            seekPreviewMs = target
                        }
                    },
                    onDragEnd = {
                        seekPreviewMs?.let { target -> exoPlayer.seekTo(target) }
                        seekPreviewMs = null
                    },
                    onDragCancel = {
                        seekPreviewMs = null
                    }
                )
            }
            .testTag("player_root_container")
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val delta = -dragAmount / 600f
                                val next = (brightnessLevel + delta).coerceIn(0.08f, 1.0f)
                                brightnessLevel = next
                                activity?.window?.let { win ->
                                    val params = win.attributes
                                    params.screenBrightness = next
                                    win.attributes = params
                                }
                                activeGestureLabel = "Kecerahan ${(next * 100).toInt()}%"
                            },
                            onDragEnd = { activeGestureLabel = null },
                            onDragCancel = { activeGestureLabel = null }
                        )
                    }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val stepDelta = (-dragAmount / 90f).toInt()
                                if (stepDelta != 0) {
                                    val nextVol = (curVol + stepDelta).coerceIn(0, maxVol)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                                    volumePercent = ((nextVol.toFloat() / maxVol.toFloat()) * 100f).toInt()
                                } else {
                                    volumePercent = ((curVol.toFloat() / maxVol.toFloat()) * 100f).toInt()
                                }
                                activeGestureLabel = "Volume $volumePercent%"
                            },
                            onDragEnd = { activeGestureLabel = null },
                            onDragCancel = { activeGestureLabel = null }
                        )
                    }
            )
        }

        if (state.videoUri != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                    }
                },
                update = { it.player = exoPlayer },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("exo_player_view")
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 600.dp)
                    .padding(24.dp)
                    .testTag("empty_player_placeholder"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.OndemandVideo,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.app_full_title),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.statusMessage,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center
                )
            }
        }

        val badgeText = seekPreviewMs?.let { "Seek → ${formatTime(it)}" } ?: activeGestureLabel
        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .testTag("gesture_feedback_badge")
            ) {
                Text(
                    text = badgeText,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        AnimatedVisibility(
            visible = state.showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SubPlayer • ${state.translationMode.label}",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.statusMessage,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                vm.refreshDownloadedLanguages()
                                showingPackManager = true
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .testTag("open_pack_manager_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DownloadForOffline,
                                contentDescription = stringResource(R.string.cd_pack_manager),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = { showingModeSettings = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .testTag("open_mode_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudSync,
                                contentDescription = stringResource(R.string.cd_mode_settings),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.label_auto), color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Switch(
                            checked = state.translateEnabled,
                            onCheckedChange = { vm.toggleAutoTranslate(it) },
                            modifier = Modifier.testTag("auto_translate_switch")
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { pickingSource = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_source_language_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = LanguageCatalog.findLabel(state.sourceLang),
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { vm.swapLanguages() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .testTag("swap_languages_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = stringResource(R.string.cd_swap_languages),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedButton(
                        onClick = { pickingTarget = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_target_language_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = LanguageCatalog.findLabel(state.targetLang),
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                state.translationProgress?.let { progress ->
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .testTag("translation_progress_bar"),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }
        }

        state.activeCue?.let { cue ->
            val displayText = if (state.translateEnabled && !cue.translatedText.isNullOrBlank()) {
                cue.translatedText
            } else {
                cue.originalText
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        bottom = if (state.showControls) 124.dp else 36.dp,
                        start = 24.dp,
                        end = 24.dp
                    )
                    .widthIn(max = 680.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("subtitle_overlay_box")
            ) {
                Text(
                    text = displayText,
                    color = Color.White,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }

        AnimatedVisibility(
            visible = state.showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                        )
                    )
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (state.durationMs > 0L) {
                    Slider(
                        value = (state.currentPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { frac ->
                            exoPlayer.seekTo((frac * state.durationMs).toLong())
                        },
                        modifier = Modifier.testTag("playback_seek_slider")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(state.currentPositionMs),
                            fontFamily = JetBrainsMonoFontFamily,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = formatTime(state.durationMs),
                            fontFamily = JetBrainsMonoFontFamily,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { videoPicker.launch(arrayOf("video/*")) },
                        modifier = Modifier.testTag("pick_video_button")
                    ) {
                        Icon(Icons.Rounded.VideoFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.label_video), fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = { subtitlePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.testTag("pick_srt_button")
                    ) {
                        Icon(Icons.Rounded.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.label_srt), fontSize = 12.sp)
                    }

                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.cd_play_pause),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    FilledTonalButton(
                        enabled = state.cues.isNotEmpty(),
                        onClick = {
                            val baseName = (state.subtitleName ?: "subtitle")
                                .substringBeforeLast(".")
                            val fileName = "${baseName}_${state.targetLang}.srt"
                            suggestedExportName = fileName
                            saveSubtitleLauncher.launch(fileName)
                        },
                        modifier = Modifier.testTag("save_srt_button")
                    ) {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.label_save), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
