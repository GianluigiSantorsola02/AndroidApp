package com.example.toxicchat.androidapp.domain.model

import org.junit.Assert.*
import org.junit.Test

class MessageRecordTest {

    @Test
    fun `isSelf returns true when speakerRaw matches alias case-insensitively`() {
        val message = MessageRecord(
            conversationId = "conv1",
            messageId = 1,
            timestampIso8601 = "2023-01-01T10:00:00Z",
            timestampEpochMillis = 1672567200000L,
            speaker = null,
            speakerRaw = "Gianluca",
            textOriginal = "Ciao",
            source = Source.WHATSAPP_TXT,
            isSystem = false,
            isToxic = false,
            toxScore = null
        )

        assertTrue(message.isSelf("SOMEONE_ELSE", listOf("gianluca")))
    }

    @Test
    fun `isSelf returns true when speakerRaw matches selectedSelfName`() {
        val message = MessageRecord(
            conversationId = "conv1",
            messageId = 1,
            timestampIso8601 = "2023-01-01T10:00:00Z",
            timestampEpochMillis = 1672567200000L,
            speaker = null,
            speakerRaw = "Gianluca",
            textOriginal = "Ciao",
            source = Source.WHATSAPP_TXT,
            isSystem = false,
            isToxic = false,
            toxScore = null
        )

        assertTrue(message.isSelf("GIANLUCA", emptyList()))
    }

    @Test
    fun `isSelf returns false when speakerRaw does not match alias or name`() {
        val message = MessageRecord(
            conversationId = "conv1",
            messageId = 1,
            timestampIso8601 = "2023-01-01T10:00:00Z",
            timestampEpochMillis = 1672567200000L,
            speaker = null,
            speakerRaw = "Mamma",
            textOriginal = "Ciao",
            source = Source.WHATSAPP_TXT,
            isSystem = false,
            isToxic = false,
            toxScore = null
        )

        assertFalse(message.isSelf("Gianluca", listOf("Gianni")))
    }

    @Test
    fun `isSelf returns false for system messages even if speakerRaw matches`() {
        val message = MessageRecord(
            conversationId = "conv1",
            messageId = 1,
            timestampIso8601 = "2023-01-01T10:00:00Z",
            timestampEpochMillis = 1672567200000L,
            speaker = null,
            speakerRaw = "Gianluca",
            textOriginal = "Tu sei stato aggiunto",
            source = Source.WHATSAPP_TXT,
            isSystem = true,
            isToxic = false,
            toxScore = null
        )

        assertFalse(message.isSelf("Gianluca", listOf("Gianluca")))
    }
}
