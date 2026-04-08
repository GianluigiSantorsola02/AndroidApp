package com.example.toxicchat.androidapp.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

enum class SourceKind { TXT, ZIP }

sealed class ReadResult {
    data class Success(val lines: List<String>, val sourceKind: SourceKind, val chosenEntryName: String?) : ReadResult()
    data class Error(val message: String) : ReadResult()
}

object WhatsAppExportReader {

    private const val TAG = "WhatsAppExportReader"
    private const val MAX_FILE_BYTES = 10 * 1024 * 1024 // 10 MB
    private const val MAX_LINES = 300_000

    suspend fun readLinesFromExport(context: Context, uri: Uri): ReadResult = withContext(Dispatchers.IO) {
        try {
            val rawStream = context.contentResolver.openInputStream(uri) ?: return@withContext ReadResult.Error("Impossibile aprire il file.")
            
            val sourceKind = BufferedInputStream(rawStream).use { bis ->
                if (checkIsZip(context, uri, bis)) SourceKind.ZIP else SourceKind.TXT
            }

            Log.d(TAG, "Detected source kind: $sourceKind for URI: $uri")

            if (sourceKind == SourceKind.ZIP) {
                // Se è un file locale (come quelli in cache), usiamo ZipFile che è più affidabile
                if (uri.scheme == "file") {
                    readFromZipFile(File(uri.path!!))
                } else {
                    readFromZipStream(context, uri)
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    readFromTxt(stream)
                } ?: ReadResult.Error("Impossibile riaprire il file.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading export", e)
            ReadResult.Error("Errore durante la lettura: ${e.localizedMessage}")
        }
    }

    private fun checkIsZip(context: Context, uri: Uri, bis: BufferedInputStream): Boolean {
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType == "application/zip") return true
        
        val fileName = try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (e: Exception) { null }
        
        if (fileName?.endsWith(".zip", ignoreCase = true) == true) return true

        bis.mark(4)
        val buffer = ByteArray(4)
        val bytesRead = bis.read(buffer, 0, 4)
        bis.reset()
        
        return bytesRead == 4 && 
                buffer[0] == 0x50.toByte() && // P
                buffer[1] == 0x4B.toByte() && // K
                buffer[2] == 0x03.toByte() && 
                buffer[3] == 0x04.toByte()
    }

    private fun readFromTxt(inputStream: InputStream): ReadResult {
        val lines = mutableListOf<String>()
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        var lineCount = 0
        
        while (true) {
            val line = reader.readLine() ?: break
            if (lineCount >= MAX_LINES) return ReadResult.Error("File troppo grande: limite di $MAX_LINES righe superato.")
            lines.add(line)
            lineCount++
        }
        return ReadResult.Success(lines, SourceKind.TXT, null)
    }

    private fun readFromZipFile(file: File): ReadResult {
        return try {
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().toList()
                val chosenEntry = findBestTxtEntry(entries.map { it.name to it.size })
                    ?: return ReadResult.Error("Nessun file di testo (.txt) trovato nello ZIP.")
                
                val entry = zip.getEntry(chosenEntry) ?: return ReadResult.Error("Errore nell'accesso al file scelto nello ZIP.")
                
                zip.getInputStream(entry).use { stream ->
                    readLinesFromStream(stream, chosenEntry, SourceKind.ZIP)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading ZipFile", e)
            ReadResult.Error("Errore durante l'apertura dello ZIP: ${e.localizedMessage}")
        }
    }

    private fun readFromZipStream(context: Context, uri: Uri): ReadResult {
        var bestEntryName: String? = null
        val entriesInfo = mutableListOf<Pair<String, Long>>()

        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entriesInfo.add(entry.name to entry.size)
                    entry = zis.nextEntry
                }
            }
        }

        bestEntryName = findBestTxtEntry(entriesInfo)
            ?: return ReadResult.Error("Nessun file di testo (.txt) trovato nello ZIP.")

        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == bestEntryName) {
                        return readLinesFromStream(zis, bestEntryName!!, SourceKind.ZIP)
                    }
                    entry = zis.nextEntry
                }
            }
        }
        return ReadResult.Error("Impossibile leggere il file di chat dallo ZIP.")
    }

    private fun findBestTxtEntry(entries: List<Pair<String, Long>>): String? {
        var bestName: String? = null
        var maxLen: Long = -2

        // Priorità 1: file che finiscono con _chat.txt (standard iOS/Android export)
        for ((name, size) in entries) {
            if (name.endsWith(".txt", ignoreCase = true) && !name.contains("..")) {
                if (name.endsWith("/_chat.txt") || name == "_chat.txt") {
                    return name
                }
            }
        }

        // Priorità 2: file che contengono "WhatsApp" e "Chat"
        for ((name, size) in entries) {
            if (name.endsWith(".txt", ignoreCase = true) && !name.contains("..")) {
                if (name.contains("WhatsApp", ignoreCase = true) && name.contains("Chat", ignoreCase = true)) {
                    return name
                }
            }
        }

        // Priorità 3: il file .txt più grande
        for ((name, size) in entries) {
            if (name.endsWith(".txt", ignoreCase = true) && !name.contains("..")) {
                if (size > maxLen) {
                    maxLen = size
                    bestName = name
                } else if (bestName == null) {
                    bestName = name
                }
            }
        }
        return bestName
    }

    private fun readLinesFromStream(stream: InputStream, entryName: String, kind: SourceKind): ReadResult {
        val lines = mutableListOf<String>()
        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        var lineCount = 0
        var totalChars = 0L

        try {
            while (true) {
                val line = reader.readLine() ?: break
                totalChars += line.length
                if (lineCount >= MAX_LINES || totalChars > (MAX_FILE_BYTES / 2)) {
                    return ReadResult.Error("Il file di chat è troppo grande.")
                }
                lines.add(line)
                lineCount++
            }
            return ReadResult.Success(lines, kind, entryName)
        } catch (e: Exception) {
            return ReadResult.Error("Errore durante la lettura del contenuto: ${e.localizedMessage}")
        }
    }
}
