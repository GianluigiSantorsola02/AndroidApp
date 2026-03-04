package com.example.toxicchat.androidapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "weekly_points",
    primaryKeys = ["conversationId", "weekId"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId", "weekId"], unique = true)]
)
data class WeeklyPointEntity(
    val conversationId: String,
    val weekId: String,
    val totalMessages: Int,
    val toxicMessages: Int,
    val toxicRate: Double
)

@Entity(
    tableName = "heatmap_cells",
    primaryKeys = ["conversationId", "dayOfWeek", "hour"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId", "dayOfWeek", "hour"], unique = true)]
)
data class HeatmapCellEntity(
    val conversationId: String,
    val dayOfWeek: Int,
    val hour: Int,
    val totalCount: Int,
    val toxicCount: Int,
    val toxicRate: Double
)

@Entity(
    tableName = "response_stats",
    primaryKeys = ["conversationId"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ResponseStatsEntity(
    val conversationId: String,
    val medianSelfToOtherMin: Double,
    val medianOtherToSelfMin: Double,
    val meanSelfToOtherMin: Double,
    val meanOtherToSelfMin: Double
)

@Entity(
    tableName = "speaker_stats",
    primaryKeys = ["conversationId", "speakerKey"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId", "speakerKey"], unique = true)]
)
data class SpeakerStatEntity(
    val conversationId: String,
    val speakerKey: String,       // chiave stabile (es: "self", "other", oppure normalize(name))
    val speakerLabel: String,     // label mostrata in UI (es: "IO", "ALTRO", "Mario Rossi")
    val totalCount: Int,
    val toxicCount: Int,
    val toxicRate: Double
)
