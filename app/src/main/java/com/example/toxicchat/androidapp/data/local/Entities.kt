package com.example.toxicchat.androidapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.toxicchat.androidapp.domain.model.AnalysisRangePreset
import com.example.toxicchat.androidapp.domain.model.AnalysisStatus

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val deviceTimezoneId: String,
    val dateOrderUsed: String,
    val parsedMessagesCount: Int,
    val systemMessagesCount: Int,
    val multilineAppendsCount: Int,
    val skippedLinesCount: Int,
    val invalidDatesCount: Int,
    val examplesSkippedLines: List<String>,

    // --- ANALYSIS (MVP v1.1) ---
    val analysisStatus: AnalysisStatus = AnalysisStatus.NON_ANALIZZATA,

    // progress + metadata
    val analysisAnalyzedCount: Int = 0,
    val analysisTotalCount: Int = 0,
    val analysisLastAnalyzedAtIso: String? = null,
    val analysisModelVersion: String? = null,

    // range
    val analysisRangePreset: AnalysisRangePreset? = null,
    val analysisRangeStartMillis: Long? = null,
    val analysisRangeEndMillis: Long? = null,

    // --- METADATA AGGIUNTIVI ---
    val isGroup: Boolean = false
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["conversationId", "timestampEpochMillis"]),
        Index(value = ["conversationId", "timestampEpochMillis", "toxScore"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val messageId: Long,
    val timestampIso8601: String,
    val timestampEpochMillis: Long,
    val speakerRaw: String?,
    val textOriginal: String,
    val isSystem: Boolean,

    // analysis output (nullable finché non analizzato)
    val toxScore: Double? = null,
    val isToxic: Boolean? = null,
    val modelVersion: String? = null
)
