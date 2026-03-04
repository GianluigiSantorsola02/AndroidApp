package com.example.toxicchat.androidapp.data.parser

import com.example.toxicchat.androidapp.domain.model.DateOrderUsed
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class WhatsAppTxtParserTest {

    private val parser = WhatsAppTxtParser()

    @Test
    fun `parses standard line with hyphen`() {
        val line = "01/01/24, 10:00 - User1: Hello"
        val result = parser.parseStreaming(listOf(line).asSequence(), dateOrder = DateOrderUsed.DMY, conversationId = UUID.randomUUID().toString()) {}
        val msg = result.first()
        assertEquals("User1", msg.speakerRaw)
        assertEquals("Hello", msg.textOriginal)
        assertFalse(msg.isSystem)
    }

    @Test
    fun `parses line with brackets and normalizes it`() {
        val line = "[01/01/24, 10:00] User1: Hello there"
        val result = parser.parseStreaming(listOf(line).asSequence(), dateOrder = DateOrderUsed.DMY, conversationId = UUID.randomUUID().toString()) {}
        val msg = result.first()
        assertEquals("User1", msg.speakerRaw)
        assertEquals("Hello there", msg.textOriginal)
    }

    @Test
    fun `parses line with brackets and hyphen and normalizes it`() {
        val line = "[01/01/24, 10:00] - User1: Hello there"
        val result = parser.parseStreaming(listOf(line).asSequence(), dateOrder = DateOrderUsed.DMY, conversationId = UUID.randomUUID().toString()) {}
        val msg = result.first()
        assertEquals("User1", msg.speakerRaw)
    }

    @Test
    fun `handles multiline messages`() {
        val lines = listOf(
            "01/01/24, 10:00 - User1: First line",
            "Second line",
            "Third line"
        )
        val result = parser.parseStreaming(lines.asSequence(), dateOrder = DateOrderUsed.DMY, conversationId = UUID.randomUUID().toString()) {}
        val msg = result.first()
        assertEquals("First line\nSecond line\nThird line", msg.textOriginal)
    }

    @Test
    fun `identifies system messages without speaker`() {
        val line = "01/01/24, 10:05 - You were added"
        val result = parser.parseStreaming(listOf(line).asSequence(), dateOrder = DateOrderUsed.DMY, conversationId = UUID.randomUUID().toString()) {}
        val msg = result.first()
        assertTrue(msg.isSystem)
        assertNull(msg.speakerRaw)
        assertEquals("You were added", msg.textOriginal)
    }

    @Test
    fun `forces system for E2EE notice even if exported with speaker`() {
        val line = "08/09/24, 18:01:24 - Vincenzo Morelli: \u200EI messaggi e le chiamate sono crittografati end-to-end. Solo le persone in questa chat possono leggerne, ascoltarne o condividerne il contenuto."
        val result = parser.parseStreaming(listOf(line).asSequence(), dateOrder = DateOrderUsed.DMY, conversationId = UUID.randomUUID().toString()) {}
        val msg = result.first()
        assertTrue(msg.isSystem)
        assertNull(msg.speakerRaw)
        assertTrue(msg.textOriginal.startsWith("I messaggi e le chiamate sono crittografati end-to-end"))
    }

    @Test
    fun `forces system for disappearing settings line without speaker`() {
        val line = "04/02/2026, 07:57 - Hai cambiato le impostazioni dei messaggi effimeri: 7 giorni"
        val result = parser.parseStreaming(listOf(line).asSequence(), dateOrder = DateOrderUsed.DMY, conversationId = UUID.randomUUID().toString()) {}
        val msg = result.first()
        assertTrue(msg.isSystem)
        assertNull(msg.speakerRaw)
        assertEquals("Hai cambiato le impostazioni dei messaggi effimeri: 7 giorni", msg.textOriginal)
    }

    @Test
    fun `forces system for disappearing settings line even if exported with speaker`() {
        val line = "04/02/2026, 07:57 - Mario: Hai cambiato le impostazioni dei messaggi effimeri: 7 giorni"
        val result = parser.parseStreaming(listOf(line).asSequence(), dateOrder = DateOrderUsed.DMY, conversationId = UUID.randomUUID().toString()) {}
        val msg = result.first()
        assertTrue(msg.isSystem)
        assertNull(msg.speakerRaw)
        assertEquals("Hai cambiato le impostazioni dei messaggi effimeri: 7 giorni", msg.textOriginal)
    }

    @Test
    fun `detects DMY date format`() {
        val lines = listOf("13/01/24, 10:00 - User1: Msg")
        val result = parser.detectDateOrder(lines.asSequence())
        assertEquals(DateOrderUsed.DMY, result)
    }

    @Test
    fun `detects MDY date format`() {
        val lines = listOf("01/13/24, 10:00 - User1: Msg")
        val result = parser.detectDateOrder(lines.asSequence())
        assertEquals(DateOrderUsed.MDY, result)
    }

    @Test
    fun `returns ambiguous for undecidable date format`() {
        val lines = listOf("01/02/24, 10:00 - User1: Msg")
        val result = parser.detectDateOrder(lines.asSequence())
        assertNull(result)
    }

    @Test
    fun `parses time with AM PM conversion`() {
        val lineAM = "01/01/24, 12:30 AM - User1: Hi"
        val resultAM = parser.parseStreaming(listOf(lineAM).asSequence(), dateOrder = DateOrderUsed.DMY, conversationId = UUID.randomUUID().toString()) {}
        assertTrue(resultAM.first().timestampIso8601.contains("T00:30:00"))

        val linePM = "01/01/24, 12:30 PM - User1: Hi"
        val resultPM = parser.parseStreaming(listOf(linePM).asSequence(), dateOrder = DateOrderUsed.DMY, conversationId = UUID.randomUUID().toString()) {}
        assertTrue(resultPM.first().timestampIso8601.contains("T12:30:00"))

        val linePM2 = "01/01/24, 1:30 PM - User1: Hi"
        val resultPM2 = parser.parseStreaming(listOf(linePM2).asSequence(), dateOrder = DateOrderUsed.DMY, conversationId = UUID.randomUUID().toString()) {}
        assertTrue(resultPM2.first().timestampIso8601.contains("T13:30:00"))
    }

    @Test
    fun `seconds default to 00 in ISO`() {
        val line = "01/01/24, 10:00 - User1: Hi"
        val result = parser.parseStreaming(listOf(line).asSequence(), dateOrder = DateOrderUsed.DMY, conversationId = UUID.randomUUID().toString()) {}
        assertTrue(result.first().timestampIso8601.contains("T10:00:00"))
    }

    @Test
    fun `does not glue media or file header to previous message`() {
        val lines = listOf(
            "01/01/24, 10:00 - User1: Hello",
            "\u200E01/01/24, 10:01 – User1:",
            "IMG-20240101-WA0001.jpg (file allegato)"
        )

        val result = parser.parseStreaming(
            lines.asSequence(),
            dateOrder = DateOrderUsed.DMY,
            conversationId = UUID.randomUUID().toString()
        ) {}.toList()

        assertEquals(2, result.size)
        assertEquals("User1", result[0].speakerRaw)
        assertEquals("Hello", result[0].textOriginal)

        assertEquals("User1", result[1].speakerRaw)
        assertFalse(result[1].isSystem)
        assertEquals("IMG-20240101-WA0001.jpg (file allegato)", result[1].textOriginal)
    }
}
