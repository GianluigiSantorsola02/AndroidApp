package com.example.toxicchat.androidapp.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
        val fileName = getFileName(uri) ?: "shared_chat.txt"
        
        if (!fileName.endsWith(".txt", true) && !fileName.endsWith(".zip", true)) {
            return
        }

        val cachedFile = copyToCache(uri, fileName)
        if (cachedFile != null) {
            _pendingImport.value = PendingImport(Uri.fromFile(cachedFile), fileName)
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
