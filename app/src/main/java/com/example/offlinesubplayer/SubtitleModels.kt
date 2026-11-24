package com.example.offlinesubplayer

data class SubtitleCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val originalText: String,
    val translatedText: String? = null
)

object SrtParser {
    private val timeRegex =
        Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})""")

    fun parse(content: String): List<SubtitleCue> {
        val normalized = content
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()
        if (normalized.isEmpty()) return emptyList()

        return normalized.split(Regex("""\n\s*\n+""")).mapNotNull { block ->
            val lines = block.lines().filter { it.isNotBlank() }
            if (lines.size < 2) return@mapNotNull null

            val index = lines[0].trim().toIntOrNull() ?: return@mapNotNull null
            val match = timeRegex.find(lines[1].trim()) ?: return@mapNotNull null

            val startMs = toMillis(
                match.groupValues[1].toLong(),
                match.groupValues[2].toLong(),
                match.groupValues[3].toLong(),
                match.groupValues[4].toLong()
            )
            val endMs = toMillis(
                match.groupValues[5].toLong(),
                match.groupValues[6].toLong(),
                match.groupValues[7].toLong(),
                match.groupValues[8].toLong()
            )
            val text = lines.drop(2).joinToString("\n").trim()
            if (text.isEmpty()) null else SubtitleCue(index, startMs, endMs, text)
        }
    }

    private fun toMillis(h: Long, m: Long, s: Long, ms: Long): Long =
        ((h * 3600L) + (m * 60L) + s) * 1000L + ms
}
