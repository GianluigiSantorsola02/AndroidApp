package com.example.toxicchat.androidapp.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

enum class SourceKind { TXT, ZIP }

sealed class ReadResult {
    data class Success(val lines: List<String>, val sourceKind: SourceKind, val chosenEntryName: String?) : ReadResult()
    data class Error(val message: String) : ReadResult()
}

object WhatsAppExportReader {

    private const val MAX_FILE_BYTES = 10 * 1024 * 1024 // 10 MB
    private const val MAX_LINES = 300_000

    suspend fun readLinesFromExport(context: Context, uri: Uri): ReadResult = withContext(Dispatchers.IO) {
        try {
            // Primo tentativo di apertura per identificare il tipo
            val rawStream = context.contentResolver.openInputStream(uri) ?: return@withContext ReadResult.Error("Impossibile aprire il file.")
            
            val sourceKind = BufferedInputStream(rawStream).use { bis ->
                if (checkIsZip(context, uri, bis)) SourceKind.ZIP else SourceKind.TXT
            }

            if (sourceKind == SourceKind.ZIP) {
                readFromZip(context, uri)
            } else {
                // Riapriamo per leggere il TXT dall'inizio
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    readFromTxt(stream)
                } ?: ReadResult.Error("Impossibile riaprire il file.")
            }
        } catch (e: Exception) {
            ReadResult.Error("Errore durante la lettura: ${e.localizedMessage}")
        }
    }

    private fun checkIsZip(context: Context, uri: Uri, bis: BufferedInputStream): Boolean {
        // 1. Controllo MIME type
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType == "application/zip") return true
        
        // 2. Controllo estensione dal nome file reale
        val fileName = try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (e: Exception) { null }
        
        if (fileName?.endsWith(".zip", ignoreCase = true) == true) return true
        if (uri.path?.endsWith(".zip", ignoreCase = true) == true) return true

        // 3. Controllo Magic Bytes (PK..)
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

    private fun readFromZip(context: Context, uri: Uri): ReadResult {
        var bestEntryName: String? = null
        var maxEntrySize: Long = -1

        // Pass 1: Trova l'entry migliore
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && name.endsWith(".txt", ignoreCase = true)) {
                        // Protezione path traversal
                        if (!name.contains("..")) {
                            if (name.endsWith("/_chat.txt") || name == "_chat.txt") {
                                bestEntryName = name
                                break // Trovata chat principale
                            }
                            if (entry.size > maxEntrySize) {
                                maxEntrySize = entry.size
                                bestEntryName = name
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        }

        val chosen = bestEntryName ?: return ReadResult.Error("Nessun file di testo (.txt) trovato nello ZIP.")

        // Pass 2: Leggi l'entry scelta
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == chosen) {
                        // Verifica dimensione se disponibile nell'header
                        if (entry.size > MAX_FILE_BYTES) return ReadResult.Error("Il file di chat è troppo grande (max 10MB).")
                        
                        val lines = mutableListOf<String>()
                        val reader = BufferedReader(InputStreamReader(zis, Charsets.UTF_8))
                        var lineCount = 0
                        var totalBytes = 0L

                        while (true) {
                            val line = reader.readLine() ?: break
                            totalBytes += line.length // Approssimativo
                            if (lineCount >= MAX_LINES || totalBytes > MAX_FILE_BYTES) {
                                return ReadResult.Error("Il file di chat supera i limiti consentiti.")
                            }
                            lines.add(line)
                            lineCount++
                        }
                        return ReadResult.Success(lines, SourceKind.ZIP, chosen)
                    }
                    entry = zis.nextEntry
                }
            }
        }

        return ReadResult.Error("Impossibile leggere il file di chat dallo ZIP.")
    }
}
