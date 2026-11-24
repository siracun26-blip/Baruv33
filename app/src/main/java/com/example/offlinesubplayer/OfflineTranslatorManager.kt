package com.example.offlinesubplayer

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

class OfflineTranslatorManager {
    private var translator: Translator? = null
    private val cache = ConcurrentHashMap<String, String>()
    private val modelManager = RemoteModelManager.getInstance()

    suspend fun getDownloadedLanguageCodes(): Set<String> {
        val models = modelManager.getDownloadedModels(TranslateRemoteModel::class.java).await()
        return models.map { it.language }.toSet()
    }

    suspend fun downloadLanguagePack(languageCode: String, requireWifi: Boolean = false) {
        val model = TranslateRemoteModel.Builder(languageCode).build()
        val conditions = DownloadConditions.Builder().apply {
            if (requireWifi) requireWifi()
        }.build()
        modelManager.download(model, conditions).await()
    }

    suspend fun deleteLanguagePack(languageCode: String) {
        val model = TranslateRemoteModel.Builder(languageCode).build()
        modelManager.deleteDownloadedModel(model).await()
    }

    suspend fun prepareModel(
        sourceLang: String = TranslateLanguage.ENGLISH,
        targetLang: String = TranslateLanguage.INDONESIAN,
        requireWifi: Boolean = false
    ) {
        translator?.close()
        cache.clear()
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
        val client = Translation.getClient(options)
        val conditionsBuilder = DownloadConditions.Builder()
        if (requireWifi) {
            conditionsBuilder.requireWifi()
        }
        client.downloadModelIfNeeded(conditionsBuilder.build()).await()
        translator = client
    }

    suspend fun translateText(text: String): String {
        val clean = text.trim()
        if (clean.isEmpty()) return ""
        cache[clean]?.let { return it }
        val client = translator ?: return clean
        val result = client.translate(clean).await()
        cache[clean] = result
        return result
    }

    fun release() {
        translator?.close()
        translator = null
    }
}
