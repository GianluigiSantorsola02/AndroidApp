package com.example.toxicchat.androidapp.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedImportManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class PendingImport(val fileUri: Uri, val fileName: String)

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport = _pendingImport.asStateFlow()

    fun handleSharedFile(uri: Uri) {
        val mimeType = context.contentResolver.getType(uri)
        var fileName = getFileName(uri)
        
        Log.d("SharedImportManager", "Ricevuto file: URI=$uri, MimeType=$mimeType, Name=$fileName")

        // Rilevamento estensione se mancante
        if (fileName == null || (!fileName.endsWith(".txt", true) && !fileName.endsWith(".zip", true))) {
            fileName = when (mimeType) {
                "application/zip" -> fileName?.let { if (it.contains(".")) it.substringBeforeLast(".") + ".zip" else "$it.zip" } ?: "shared_chat.zip"
                "text/plain" -> fileName?.let { if (it.contains(".")) it.substringBeforeLast(".") + ".txt" else "$it.txt" } ?: "shared_chat.txt"
                else -> fileName
            }
        }

        if (fileName != null && !fileName.endsWith(".txt", true) && !fileName.endsWith(".zip", true)) {
            if (mimeType == "text/plain" || mimeType == "application/octet-stream" || uri.toString().contains("whatsapp", true)) {
                fileName += ".txt"
            }
        }

        if (fileName == null) fileName = "whatsapp_chat.txt"

        val cachedFile = copyToCache(uri, fileName!!)
        if (cachedFile != null) {
            Log.d("SharedImportManager", "File salvato in cache: ${cachedFile.absolutePath}")
            _pendingImport.value = PendingImport(Uri.fromFile(cachedFile), fileName!!)
        }
    }

    fun consumeImport(): PendingImport? {
        val current = _pendingImport.value
        _pendingImport.value = null
        return current
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {}
        return name ?: uri.lastPathSegment
    }

    private fun copyToCache(uri: Uri, fileName: String): File? {
        return try {
            val cacheDir = File(context.cacheDir, "shared_imports").apply { mkdirs() }
            cacheDir.listFiles()?.forEach { it.delete() }
            
            val destFile = File(cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        } catch (e: Exception) {
            null
        }
    }
}
