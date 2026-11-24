package com.example.offlinesubplayer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val videoUri: Uri? = null,
    val subtitleName: String? = null,
    val cues: List<SubtitleCue> = emptyList(),
    val activeCue: SubtitleCue? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val showControls: Boolean = true,
    val translateEnabled: Boolean = true,
    val translationMode: TranslationMode = TranslationMode.HYBRID,
    val geminiApiKey: String = "",
    val isOfflineModelReady: Boolean = false,
    val downloadedLanguages: Set<String> = emptySet(),
    val downloadingLanguages: Set<String> = emptySet(),
    val sourceLang: String = TranslateLanguage.ENGLISH,
    val targetLang: String = TranslateLanguage.INDONESIAN,
    val translationProgress: Float? = null,
    val statusMessage: String = "Pilih video dan file .srt untuk mulai"
)

class VideoPlayerViewModel : ViewModel() {
    private val offlineTranslator = OfflineTranslatorManager()
    private val onlineTranslator = OnlineTranslatorManager()
    private var translationJob: Job? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        refreshDownloadedLanguages()
        prepareOfflineModel()
    }

    fun refreshDownloadedLanguages() {
        viewModelScope.launch {
            runCatching {
                offlineTranslator.getDownloadedLanguageCodes()
            }.onSuccess { codes ->
                _uiState.update { it.copy(downloadedLanguages = codes) }
            }
        }
    }

    fun downloadLanguagePack(languageCode: String) {
        if (_uiState.value.downloadingLanguages.contains(languageCode)) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    downloadingLanguages = it.downloadingLanguages + languageCode,
                    statusMessage = "Mengunduh paket bahasa ${LanguageCatalog.findLabel(languageCode)} (~30 MB)..."
                )
            }
            runCatching {
                offlineTranslator.downloadLanguagePack(languageCode)
            }.onSuccess {
                refreshDownloadedLanguages()
                _uiState.update {
                    it.copy(
                        downloadingLanguages = it.downloadingLanguages - languageCode,
                        statusMessage = "Paket ${LanguageCatalog.findLabel(languageCode)} terunduh"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        downloadingLanguages = it.downloadingLanguages - languageCode,
                        statusMessage = "Gagal unduh ${languageCode.uppercase()}: ${err.localizedMessage ?: "cek koneksi"}"
                    )
                }
            }
        }
    }

    fun deleteLanguagePack(languageCode: String) {
        viewModelScope.launch {
            runCatching {
                offlineTranslator.deleteLanguagePack(languageCode)
            }.onSuccess {
                refreshDownloadedLanguages()
                _uiState.update {
                    it.copy(statusMessage = "Paket bahasa ${languageCode.uppercase()} dihapus dari perangkat")
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusMessage = "Gagal menghapus paket: ${err.localizedMessage ?: "error"}")
                }
            }
        }
    }

    fun prepareOfflineModel(
        sourceLang: String = _uiState.value.sourceLang,
        targetLang: String = _uiState.value.targetLang
    ) {
        viewModelScope.launch {
            if (_uiState.value.translationMode == TranslationMode.ONLINE) {
                _uiState.update {
                    it.copy(
                        statusMessage = "Mode Online siap (${sourceLang.uppercase()} → ${targetLang.uppercase()})"
                    )
                }
                if (_uiState.value.translateEnabled && _uiState.value.cues.isNotEmpty()) {
                    translateAllCues()
                }
                return@launch
            }

            _uiState.update { it.copy(statusMessage = "Menyiapkan paket terjemahan offline...") }
            runCatching {
                offlineTranslator.prepareModel(sourceLang, targetLang)
            }.onSuccess {
                refreshDownloadedLanguages()
                _uiState.update {
                    it.copy(
                        isOfflineModelReady = true,
                        statusMessage = "${it.translationMode.label} siap (${sourceLang.uppercase()} → ${targetLang.uppercase()})"
                    )
                }
                if (_uiState.value.translateEnabled && _uiState.value.cues.isNotEmpty()) {
                    translateAllCues()
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isOfflineModelReady = false,
                        statusMessage = if (it.translationMode == TranslationMode.HYBRID) {
                            "Offline belum siap, memakai Online (${err.localizedMessage ?: "offline gagal"})"
                        } else {
                            "Gagal menyiapkan bahasa offline: ${err.localizedMessage ?: "cek koneksi saat unduh model"}"
                        }
                    )
                }
                if (_uiState.value.translationMode == TranslationMode.HYBRID &&
                    _uiState.value.translateEnabled &&
                    _uiState.value.cues.isNotEmpty()
                ) {
                    translateAllCues()
                }
            }
        }
    }

    fun setTranslationMode(mode: TranslationMode) {
        if (mode == _uiState.value.translationMode) return
        translationJob?.cancel()
        val clearedCues = _uiState.value.cues.map { it.copy(translatedText = null) }
        _uiState.update {
            it.copy(
                translationMode = mode,
                cues = clearedCues,
                activeCue = null,
                translationProgress = null
            )
        }
        if (mode == TranslationMode.ONLINE) {
            _uiState.update {
                it.copy(statusMessage = "Mode Online aktif (${it.sourceLang.uppercase()} → ${it.targetLang.uppercase()})")
            }
            if (_uiState.value.translateEnabled && _uiState.value.cues.isNotEmpty()) {
                translateAllCues()
            }
        } else {
            prepareOfflineModel(_uiState.value.sourceLang, _uiState.value.targetLang)
        }
    }

    fun updateGeminiApiKey(newKey: String) {
        onlineTranslator.clearCache()
        val clearedCues = _uiState.value.cues.map { it.copy(translatedText = null) }
        _uiState.update {
            it.copy(
                geminiApiKey = newKey.trim(),
                cues = clearedCues,
                statusMessage = if (newKey.isNotBlank()) {
                    "API Key Gemini tersimpan untuk terjemahan online"
                } else {
                    "Menggunakan layanan terjemahan online standar"
                }
            )
        }
        if (_uiState.value.translateEnabled &&
            _uiState.value.cues.isNotEmpty() &&
            _uiState.value.translationMode != TranslationMode.OFFLINE
        ) {
            translateAllCues()
        }
    }

    fun changeLanguagePair(newSourceLang: String, newTargetLang: String) {
        if (newSourceLang == _uiState.value.sourceLang && newTargetLang == _uiState.value.targetLang) return
        translationJob?.cancel()

        val clearedCues = _uiState.value.cues.map { it.copy(translatedText = null) }
        _uiState.update {
            it.copy(
                sourceLang = newSourceLang,
                targetLang = newTargetLang,
                cues = clearedCues,
                activeCue = null,
                isOfflineModelReady = false,
                translationProgress = null
            )
        }
        prepareOfflineModel(newSourceLang, newTargetLang)
    }

    fun swapLanguages() {
        val currentSource = _uiState.value.sourceLang
        val currentTarget = _uiState.value.targetLang
        if (currentSource == currentTarget) return
        changeLanguagePair(newSourceLang = currentTarget, newTargetLang = currentSource)
    }

    fun setVideoUri(uri: Uri) {
        _uiState.update { it.copy(videoUri = uri) }
    }

    fun loadSubtitleFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val fileName = resolveDisplayName(context, uri) ?: uri.lastPathSegment
                    val content = context.contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        .orEmpty()
                    fileName to content
                }
            }
            result.onSuccess { (fileName, content) ->
                if (content.isNotBlank()) {
                    loadSubtitles(fileName, content)
                } else {
                    _uiState.update { it.copy(statusMessage = "File subtitle kosong") }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusMessage = "Gagal membaca subtitle: ${err.localizedMessage ?: "error"}")
                }
            }
        }
    }

    fun loadSubtitles(fileName: String?, srtContent: String) {
        val parsed = SrtParser.parse(srtContent)
        _uiState.update {
            it.copy(
                subtitleName = fileName ?: "Subtitle.srt",
                cues = parsed,
                activeCue = null,
                statusMessage = if (parsed.isEmpty()) {
                    "Tidak ada baris .srt valid ditemukan"
                } else {
                    "Memuat ${parsed.size} baris subtitle"
                }
            )
        }
        if (_uiState.value.translateEnabled && parsed.isNotEmpty()) {
            translateAllCues()
        }
    }

    fun toggleAutoTranslate(enabled: Boolean) {
        _uiState.update { it.copy(translateEnabled = enabled) }
        if (enabled) {
            translateAllCues()
        }
    }

    fun exportTranslatedSrt(context: Context, uri: Uri, fileName: String? = null) {
        viewModelScope.launch {
            val cues = _uiState.value.cues
            if (cues.isEmpty()) {
                _uiState.update { it.copy(statusMessage = "Belum ada subtitle untuk disimpan") }
                return@launch
            }
            val srtText = SrtExporter.buildSrtContent(cues, preferTranslated = true)
            val writeRes = withContext(Dispatchers.IO) {
                SrtExporter.writeToUri(context, uri, srtText)
            }
            writeRes
                .onSuccess {
                    val savedContent = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver
                                .openInputStream(uri)
                                ?.bufferedReader(Charsets.UTF_8)
                                ?.use { it.readText() }
                        }.getOrNull()
                    } ?: srtText
                    loadSubtitles(
                        fileName = fileName ?: uri.lastPathSegment ?: "terjemahan.srt",
                        srtContent = savedContent
                    )
                    _uiState.update {
                        it.copy(statusMessage = "File .srt disimpan & dimuat ulang")
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(statusMessage = "Gagal menyimpan .srt: ${err.localizedMessage ?: "error"}")
                    }
                }
        }
    }

    private suspend fun translateSingleCue(text: String): String {
        val snapshot = _uiState.value
        return when (snapshot.translationMode) {
            TranslationMode.OFFLINE -> {
                if (snapshot.isOfflineModelReady) {
                    runCatching { offlineTranslator.translateText(text) }.getOrElse { text }
                } else {
                    text
                }
            }
            TranslationMode.ONLINE -> {
                onlineTranslator.translateText(
                    text = text,
                    sourceLang = snapshot.sourceLang,
                    targetLang = snapshot.targetLang,
                    geminiApiKey = snapshot.geminiApiKey
                ).getOrElse { text }
            }
            TranslationMode.HYBRID -> {
                val onlineRes = onlineTranslator.translateText(
                    text = text,
                    sourceLang = snapshot.sourceLang,
                    targetLang = snapshot.targetLang,
                    geminiApiKey = snapshot.geminiApiKey
                )
                onlineRes.getOrElse {
                    if (snapshot.isOfflineModelReady) {
                        runCatching { offlineTranslator.translateText(text) }.getOrElse { text }
                    } else {
                        text
                    }
                }
            }
        }
    }

    private fun translateAllCues() {
        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            val snapshot = _uiState.value
            if (snapshot.translationMode == TranslationMode.OFFLINE && !snapshot.isOfflineModelReady) {
                return@launch
            }

            val currentList = snapshot.cues.toMutableList()
            if (currentList.isEmpty()) {
                _uiState.update { it.copy(translationProgress = null) }
                return@launch
            }

            _uiState.update { it.copy(translationProgress = 0f) }

            for (i in currentList.indices) {
                val cue = currentList[i]
                val updatedCue = if (cue.translatedText == null) {
                    cue.copy(translatedText = translateSingleCue(cue.originalText))
                } else {
                    cue
                }
                currentList[i] = updatedCue

                val progress = (i + 1).toFloat() / currentList.size.toFloat()
                if (i % 3 == 0 || i == currentList.lastIndex) {
                    _uiState.update {
                        it.copy(
                            cues = currentList.toList(),
                            translationProgress = progress,
                            statusMessage = "Menerjemahkan (${it.translationMode.label})... ${(progress * 100).toInt()}%"
                        )
                    }
                }
            }

            _uiState.update {
                it.copy(
                    translationProgress = null,
                    statusMessage = "Terjemahan selesai [${it.translationMode.label}] (${it.sourceLang.uppercase()} → ${it.targetLang.uppercase()})"
                )
            }
        }
    }

    fun onPlaybackTick(positionMs: Long, durationMs: Long, isPlaying: Boolean) {
        val cue = _uiState.value.cues.firstOrNull { positionMs in it.startMs..it.endMs }
        _uiState.update {
            it.copy(
                currentPositionMs = positionMs,
                durationMs = durationMs.coerceAtLeast(0L),
                isPlaying = isPlaying,
                activeCue = cue
            )
        }
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (col >= 0 && cursor.moveToFirst()) cursor.getString(col) else null
                }
        }.getOrNull()
    }

    override fun onCleared() {
        translationJob?.cancel()
        offlineTranslator.release()
        super.onCleared()
    }
}
