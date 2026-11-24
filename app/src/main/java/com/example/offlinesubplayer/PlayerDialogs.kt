package com.example.offlinesubplayer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinesubplayer.ui.theme.JetBrainsMonoFontFamily

@Composable
fun LanguagePackManagerDialog(
    downloadedCodes: Set<String>,
    downloadingCodes: Set<String>,
    onDismiss: () -> Unit,
    onDownloadPack: (String) -> Unit,
    onDeletePack: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var query by remember { mutableStateOf("") }
    var filterDownloadedOnly by remember { mutableStateOf(false) }

    val filtered = remember(query, downloadedCodes, filterDownloadedOnly) {
        LanguageCatalog.allLanguages.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.label.contains(query, ignoreCase = true) ||
                item.code.contains(query, ignoreCase = true)
            val matchesDownloaded = !filterDownloadedOnly || downloadedCodes.contains(item.code)
            matchesQuery && matchesDownloaded
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.title_pack_manager),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    text = "Terunduh: ${downloadedCodes.size} dari ${LanguageCatalog.allLanguages.size} bahasa (~30 MB/paket)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.hint_search_language)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pack_search_input")
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = filterDownloadedOnly,
                        onClick = { filterDownloadedOnly = !filterDownloadedOnly },
                        label = { Text("Sudah Diunduh (${downloadedCodes.size})", fontSize = 11.sp) },
                        modifier = Modifier.testTag("pack_filter_downloaded_chip")
                    )
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(LanguageCatalog.MLKIT_MODELS_GUIDE_URL))
                                )
                            }
                        },
                        modifier = Modifier.testTag("pack_official_catalog_button")
                    ) {
                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.label_official_catalog), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered.size, key = { idx -> filtered[idx].code }) { idx ->
                        val item = filtered[idx]
                        val isDownloaded = downloadedCodes.contains(item.code)
                        val isDownloading = downloadingCodes.contains(item.code)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.label,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = item.packageArchiveUrl,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(item.packageArchiveUrl))
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("copy_pack_url_${item.code}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = stringResource(R.string.cd_copy_url),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(item.packageArchiveUrl))
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("open_pack_url_${item.code}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Link,
                                            contentDescription = stringResource(R.string.cd_open_url),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    if (isDownloading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .padding(horizontal = 12.dp)
                                                .size(22.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else if (isDownloaded) {
                                        IconButton(
                                            onClick = { onDeletePack(item.code) },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("delete_pack_${item.code}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = stringResource(R.string.cd_delete_pack),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { onDownloadPack(item.code) },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("download_pack_${item.code}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Download,
                                                contentDescription = stringResource(R.string.cd_download_pack),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("pack_manager_close_button")
            ) {
                Text(stringResource(R.string.label_close))
            }
        }
    )
}

@Composable
fun TranslationSettingsDialog(
    currentMode: TranslationMode,
    currentApiKey: String,
    downloadedCount: Int,
    onDismiss: () -> Unit,
    onSelectMode: (TranslationMode) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onOpenPackManager: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf(currentApiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.title_translation_settings),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.label_choose_engine),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                TranslationMode.entries.forEach { mode ->
                    val selected = mode == currentMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .clickable { onSelectMode(mode) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("mode_option_${mode.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mode.label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            val desc = when (mode) {
                                TranslationMode.OFFLINE -> "100% lokal memakai paket bahasa ML Kit"
                                TranslationMode.ONLINE -> "Terjemahan cloud langsung tanpa unduh paket"
                                TranslationMode.HYBRID -> "Prioritas Online, otomatis fallback ke Offline jika internet putus"
                            }
                            Text(desc, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                        }
                        if (selected) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        }
                    }
                }

                FilledTonalButton(
                    onClick = {
                        onDismiss()
                        onOpenPackManager()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_open_pack_manager_button")
                ) {
                    Icon(Icons.Rounded.DownloadForOffline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kelola Paket Bahasa & Link Download ($downloadedCount)")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                Text(
                    text = stringResource(R.string.label_gemini_api_key),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    placeholder = { Text(stringResource(R.string.hint_gemini_api_key)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_api_key_input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSaveApiKey(apiKeyInput)
                    onDismiss()
                },
                modifier = Modifier.testTag("settings_save_button")
            ) {
                Text(stringResource(R.string.label_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("settings_cancel_button")
            ) {
                Text(stringResource(R.string.label_cancel))
            }
        }
    )
}

@Composable
fun LanguagePickerDialog(
    title: String,
    selectedCode: String,
    downloadedCodes: Set<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) LanguageCatalog.allLanguages
        else LanguageCatalog.allLanguages.filter {
            it.label.contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.hint_search_language)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("language_picker_search_input")
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    items(filtered.size, key = { idx -> filtered[idx].code }) { idx ->
                        val item = filtered[idx]
                        val isSelected = item.code == selectedCode
                        val isOfflineReady = downloadedCodes.contains(item.code)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    onSelect(item.code)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("language_option_${item.code}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isOfflineReady) {
                                    Text(
                                        text = stringResource(R.string.label_offline_downloaded),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(Icons.Rounded.Check, contentDescription = null)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("language_picker_close_button")
            ) {
                Text(stringResource(R.string.label_close))
            }
        }
    )
}
