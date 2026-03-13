package com.example.toxicchat.androidapp.domain.model

import com.example.toxicchat.androidapp.data.local.MessageEntity

data class MessageRecord(
    val conversationId: String,
    val messageId: Long,
    val timestampIso8601: String,
    val timestampEpochMillis: Long,
    val speakerRaw: String?,
    val textOriginal: String,
    val source: Source,
    val isSystem: Boolean,
    val isToxic: Boolean? = null,
    val toxScore: Double? = null
)

enum class Source {
    WHATSAPP_TXT
}

data class ImportMetadata(
    val deviceTimezoneId: String,
    val dateOrderUsed: String,
    val parsedMessagesCount: Int,
    val systemMessagesCount: Int,
    val multilineAppendsCount: Int,
    val skippedLinesCount: Int,
    val invalidDatesCount: Int,
    val examplesSkippedLines: List<String>
)

fun MessageRecord.toEntity(): MessageEntity {
    return MessageEntity(
        conversationId = conversationId,
        messageId = messageId,
        timestampIso8601 = timestampIso8601,
        timestampEpochMillis = timestampEpochMillis,
        speakerRaw = speakerRaw,
        textOriginal = textOriginal,
        isSystem = isSystem,
        toxScore = toxScore,
        isToxic = isToxic,
        modelVersion = null
    )
}
