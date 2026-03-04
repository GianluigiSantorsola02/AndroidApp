package com.example.toxicchat.androidapp.data.importer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WhatsAppExportReaderTest {

    private val context = mockk<Context>()
    private val contentResolver = mockk<ContentResolver>()
    private val uri = mockk<Uri>()

    init {
        every { context.contentResolver } returns contentResolver
        every { uri.path } returns null
    }

    @Test
    fun `readLinesFromExport with TXT file returns success`() = runBlocking {
        val content = "Line 1\nLine 2\nLine 3"
        every { contentResolver.getType(uri) } returns "text/plain"
        every { contentResolver.openInputStream(uri) } returns ByteArrayInputStream(content.toByteArray())

        val result = WhatsAppExportReader.readLinesFromExport(context, uri)

        assertTrue(result is ReadResult.Success)
        val success = result as ReadResult.Success
        assertEquals(3, success.lines.size)
        assertEquals("Line 1", success.lines[0])
        assertEquals(SourceKind.TXT, success.sourceKind)
    }

    @Test
    fun `readLinesFromExport with ZIP containing _chat_txt returns success`() = runBlocking {
        val zipData = createZipInMemory(mapOf(
            "_chat.txt" to "Message from zip",
            "other.txt" to "Short",
            "image.jpg" to "binary"
        ))
        
        every { contentResolver.getType(uri) } returns "application/zip"
        // We need to return a new stream each time because it might be consumed
        every { contentResolver.openInputStream(uri) } answers { ByteArrayInputStream(zipData) }

        val result = WhatsAppExportReader.readLinesFromExport(context, uri)

        assertTrue(result is ReadResult.Success)
        val success = result as ReadResult.Success
        assertEquals(1, success.lines.size)
        assertEquals("Message from zip", success.lines[0])
        assertEquals("_chat.txt", success.chosenEntryName)
        assertEquals(SourceKind.ZIP, success.sourceKind)
    }

    @Test
    fun `readLinesFromExport with ZIP containing multiple txt chooses largest if no _chat_txt`() = runBlocking {
        val zipData = createZipInMemory(mapOf(
            "small.txt" to "Tiny",
            "large.txt" to "This is a much larger text file content to be chosen."
        ))
        
        every { contentResolver.getType(uri) } returns "application/zip"
        every { contentResolver.openInputStream(uri) } answers { ByteArrayInputStream(zipData) }

        val result = WhatsAppExportReader.readLinesFromExport(context, uri)

        assertTrue(result is ReadResult.Success)
        val success = result as ReadResult.Success
        assertEquals("large.txt", success.chosenEntryName)
        assertTrue(success.lines[0].contains("larger"))
    }

    @Test
    fun `readLinesFromExport with ZIP containing no txt returns error`() = runBlocking {
        val zipData = createZipInMemory(mapOf(
            "image.jpg" to "binary",
            "readme.md" to "not a txt"
        ))
        
        every { contentResolver.getType(uri) } returns "application/zip"
        every { contentResolver.openInputStream(uri) } answers { ByteArrayInputStream(zipData) }

        val result = WhatsAppExportReader.readLinesFromExport(context, uri)

        assertTrue(result is ReadResult.Error)
        assertEquals("Nessun file .txt trovato nello ZIP.", (result as ReadResult.Error).message)
    }

    private fun createZipInMemory(entries: Map<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.forEach { (name, content) ->
                val entry = ZipEntry(name)
                // Set size to help the reader logic if it relies on entry.size
                val bytes = content.toByteArray()
                entry.size = bytes.size.toLong()
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
