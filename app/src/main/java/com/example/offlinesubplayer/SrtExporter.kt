package com.example.offlinesubplayer

import android.content.Context
import android.net.Uri
import java.util.Locale

object SrtExporter {
    fun buildSrtContent(
        cues: List<SubtitleCue>,
        preferTranslated: Boolean = true
    ): String {
        return cues.mapIndexed { idx, cue ->
            val text = if (preferTranslated && !cue.translatedText.isNullOrBlank()) {
                cue.translatedText
            } else {
                cue.originalText
            }
            val start = formatSrtTimestamp(cue.startMs)
            val end = formatSrtTimestamp(cue.endMs)
            "${idx + 1}\n$start --> $end\n$text"
        }.joinToString("\n\n")
    }

    fun writeToUri(context: Context, uri: Uri, content: String): Result<Unit> {
        return runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: error("Tidak bisa membuka file tujuan")
        }
    }

    private fun formatSrtTimestamp(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val hours = safeMs / 3_600_000L
        val remAfterHours = safeMs % 3_600_000L
        val minutes = remAfterHours / 60_000L
        val remAfterMinutes = remAfterHours % 60_000L
        val seconds = remAfterMinutes / 1_000L
        val millis = remAfterMinutes % 1_000L
        return String.format(
            Locale.US,
            "%02d:%02d:%02d,%03d",
            hours,
            minutes,
            seconds,
            millis
        )
    }
}
