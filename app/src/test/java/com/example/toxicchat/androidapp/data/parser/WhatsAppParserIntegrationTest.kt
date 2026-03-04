package com.example.toxicchat.androidapp.data.parser

import com.example.toxicchat.androidapp.domain.model.DateOrderUsed
import com.example.toxicchat.androidapp.domain.model.ImportMetadata
import com.example.toxicchat.androidapp.domain.model.MessageRecord
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WhatsAppParserIntegrationTest {

    private val parser = WhatsAppTxtParser()

    private val testDataPath = "../testdata/whatsapp/"

    private data class Expected(
        val parsed: Int,
        val system: Int,
        val multiline: Int,
        val skipped: Int,
        val invalid: Int,
        val dateOrder: String
    )

    private data class RunResult(
        val messages: List<MessageRecord>,
        val metadata: ImportMetadata
    )

    @Test
    fun parserReport_matchesSpec_forAll4Files() {
        val testFolder = File(testDataPath)
        if (!testFolder.exists()) {
            fail(
                "Cartella testdata non trovata in: ${testFolder.absolutePath}. " +
                        "Verifica la working directory del test."
            )
        }

        val cases = mapOf(
            "WA_TEST_01_1to1_brackets_24h_seconds_missingdash.txt" to Expected(
                parsed = 7, system = 1, multiline = 2, skipped = 0, invalid = 0, dateOrder = "DMY"
            ),
            "WA_TEST_02_1to1_nobrackets_24h_withdash.txt" to Expected(
                parsed = 5, system = 1, multiline = 0, skipped = 0, invalid = 0, dateOrder = "DMY"
            ),
            "WA_TEST_03_group_brackets_ampm.txt" to Expected(
                parsed = 7, system = 1, multiline = 1, skipped = 0, invalid = 0, dateOrder = "DMY"
            ),
            "WA_TEST_04_dirty_multiline_orphan_ambiguous_dateorder.txt" to Expected(
                parsed = 6, system = 1, multiline = 2, skipped = 1, invalid = 0, dateOrder = "DMY"
            )
        )

        cases.forEach { (fileName, exp) ->
            val file = File(testFolder, fileName)
            assertTrue("File di test mancante: $fileName", file.exists())
            val lines = file.readLines()

            val detected = parser.detectDateOrder(lines.asSequence())
            val dateOrder = detected ?: DateOrderUsed.DMY

            val result = runParse(lines, dateOrder, fileName)
            assertReport(fileName, result, exp)
        }
    }

    @Test
    fun spotCheck_WA_TEST_01_content() {
        val testFolder = File(testDataPath)
        val file = File(testFolder, "WA_TEST_01_1to1_brackets_24h_seconds_missingdash.txt")
        if (!file.exists()) fail("File WA_TEST_01 non trovato")

        val lines = file.readLines()
        val result = runParse(lines, dateOrder = DateOrderUsed.DMY, conversationId = "WA_TEST_01")

        val multilineMsg = result.messages.find { it.textOriginal.contains("Ti scrivo una cosa lunga") }
        assertNotNull("Messaggio multilinea non trovato", multilineMsg)
        assertTrue("Deve contenere 'seconda riga'", multilineMsg!!.textOriginal.contains("seconda riga"))
        assertTrue("Deve contenere 'terza.'", multilineMsg.textOriginal.contains("terza."))

        val systemMsg = result.messages.find { it.isSystem }
        assertNotNull("System message non trovato", systemMsg)
        assertTrue("Deve contenere 'crittografati'", systemMsg!!.textOriginal.contains("crittografati"))
    }

    private fun runParse(
        lines: List<String>,
        dateOrder: DateOrderUsed,
        conversationId: String
    ): RunResult {
        var meta: ImportMetadata? = null

        val messages = parser
            .parseStreaming(
                lines = lines.asSequence(),
                dateOrder = dateOrder,
                conversationId = conversationId,
                onMetadataComplete = { meta = it }
            )
            .toList()

        val metadata = requireNotNull(meta) { "Metadata non impostata: la sequence non è stata consumata?" }
        return RunResult(messages = messages, metadata = metadata)
    }

    private fun assertReport(fileName: String, r: RunResult, exp: Expected) {
        val meta = r.metadata

        assertEquals("[$fileName] parsedMessagesCount errato", exp.parsed, meta.parsedMessagesCount)
        assertEquals("[$fileName] systemMessagesCount errato", exp.system, meta.systemMessagesCount)
        assertEquals("[$fileName] multilineAppendsCount errato", exp.multiline, meta.multilineAppendsCount)
        assertEquals("[$fileName] skippedLinesCount errato", exp.skipped, meta.skippedLinesCount)
        assertEquals("[$fileName] invalidDatesCount errato", exp.invalid, meta.invalidDatesCount)
        assertEquals("[$fileName] dateOrderUsed errato", exp.dateOrder, meta.dateOrderUsed)

        assertEquals(
            "[$fileName] systemMessagesCount != count{isSystem}",
            exp.system,
            r.messages.count { it.isSystem }
        )
        assertEquals("[$fileName] messages.size != parsedMessagesCount", exp.parsed, r.messages.size)

        r.messages.forEachIndexed { index, msg ->
            assertEquals("[$fileName] messageId non progressivo", (index + 1).toLong(), msg.messageId)
        }
    }
}
