package com.example.toxicchat.androidapp.data.local

import androidx.room.*
import com.example.toxicchat.androidapp.domain.model.AnalysisRangePreset
import com.example.toxicchat.androidapp.domain.model.AnalysisStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisDao {

    // ---------- status / metadata / range ----------

    @Query("UPDATE conversations SET analysisStatus = :status WHERE id = :conversationId")
    suspend fun updateStatus(conversationId: String, status: AnalysisStatus)

    @Query("""
        UPDATE conversations SET 
            analysisAnalyzedCount = :analyzedCount,
            analysisTotalCount = :totalCount,
            analysisLastAnalyzedAtIso = :lastAtIso,
            analysisModelVersion = :modelVersion,
            analysisRangeStartMillis = :rangeStartMillis,
            analysisRangeEndMillis = :rangeEndMillis,
            analysisRangePreset = :rangePreset
        WHERE id = :conversationId
    """)
    suspend fun updateMetadata(
        conversationId: String,
        analyzedCount: Int,
        totalCount: Int,
        lastAtIso: String?,
        modelVersion: String?,
        rangeStartMillis: Long?,
        rangeEndMillis: Long?,
        rangePreset: AnalysisRangePreset?
    )

    @Query("""
        UPDATE conversations SET
            analysisRangePreset = :preset,
            analysisRangeStartMillis = :startMillis,
            analysisRangeEndMillis = :endMillis
        WHERE id = :conversationId
    """)
    suspend fun setAnalysisRange(
        conversationId: String,
        preset: AnalysisRangePreset,
        startMillis: Long,
        endMillis: Long
    )

    @Query("""
        SELECT 
            analysisStatus            AS analysisStatus,
            analysisAnalyzedCount     AS analysisAnalyzedCount,
            analysisTotalCount        AS analysisTotalCount,
            analysisRangePreset       AS analysisRangePreset,
            analysisRangeStartMillis  AS analysisRangeStartMillis,
            analysisRangeEndMillis    AS analysisRangeEndMillis,
            analysisLastAnalyzedAtIso AS analysisLastAnalyzedAtIso,
            analysisModelVersion      AS analysisModelVersion
        FROM conversations
        WHERE id = :conversationId
        LIMIT 1
    """)
    fun getAnalysisMetadataFlow(conversationId: String): Flow<AnalysisMetadataFields?>

    @Query("""
        SELECT 
            analysisRangePreset      AS preset,
            analysisRangeStartMillis AS startMillis,
            analysisRangeEndMillis   AS endMillis
        FROM conversations
        WHERE id = :conversationId
        LIMIT 1
    """)
    suspend fun getRangeFieldsOnce(conversationId: String): RangeFields?

    data class RangeFields(
        val preset: AnalysisRangePreset?,
        val startMillis: Long?,
        val endMillis: Long?
    )

    // ---------- aggregates tables ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeklyPoints(points: List<WeeklyPointEntity>)

    @Query("DELETE FROM weekly_points WHERE conversationId = :conversationId")
    suspend fun deleteWeeklyPoints(conversationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeatmapCells(cells: List<HeatmapCellEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeakerStats(stats: List<SpeakerStatEntity>)

    @Query("DELETE FROM speaker_stats WHERE conversationId = :conversationId")
    suspend fun deleteSpeakerStats(conversationId: String)

    @Query("SELECT * FROM speaker_stats WHERE conversationId = :conversationId ORDER BY toxicRate DESC, totalCount DESC")
    fun getSpeakerStatsFlow(conversationId: String): Flow<List<SpeakerStatEntity>>

    @Query("DELETE FROM heatmap_cells WHERE conversationId = :conversationId")
    suspend fun deleteHeatmapCells(conversationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertResponseStats(stats: ResponseStatsEntity)

    @Query("DELETE FROM response_stats WHERE conversationId = :conversationId")
    suspend fun deleteResponseStats(conversationId: String)

    @Transaction
    suspend fun replaceAllAggregates(
        conversationId: String,
        weeklyPoints: List<WeeklyPointEntity>,
        heatmapCells: List<HeatmapCellEntity>,
        response: ResponseStatsEntity?,
        speakerStats: List<SpeakerStatEntity>
    ) {
        deleteWeeklyPoints(conversationId)
        if (weeklyPoints.isNotEmpty()) insertWeeklyPoints(weeklyPoints)

        deleteHeatmapCells(conversationId)
        if (heatmapCells.isNotEmpty()) insertHeatmapCells(heatmapCells)

        if (response != null) upsertResponseStats(response) else deleteResponseStats(conversationId)

        deleteSpeakerStats(conversationId)
        if (speakerStats.isNotEmpty()) insertSpeakerStats(speakerStats)
    }

    @Query("SELECT * FROM weekly_points WHERE conversationId = :conversationId ORDER BY weekId ASC")
    fun getWeeklyPointsFlow(conversationId: String): Flow<List<WeeklyPointEntity>>

    @Query("SELECT * FROM heatmap_cells WHERE conversationId = :conversationId ORDER BY dayOfWeek ASC, hour ASC")
    fun getHeatmapCellsFlow(conversationId: String): Flow<List<HeatmapCellEntity>>

    @Query("SELECT * FROM response_stats WHERE conversationId = :conversationId LIMIT 1")
    fun getResponseStatsFlow(conversationId: String): Flow<ResponseStatsEntity?>

    // ---------- range helpers ----------

    @Query("SELECT MIN(timestampEpochMillis) FROM messages WHERE conversationId = :id")
    suspend fun getMinTimestamp(id: String): Long?

    @Query("SELECT MAX(timestampEpochMillis) FROM messages WHERE conversationId = :id")
    suspend fun getMaxTimestamp(id: String): Long?

    @Query("""
    SELECT COUNT(*) FROM messages 
    WHERE conversationId = :id 
      AND timestampEpochMillis BETWEEN :startMillis AND :endMillis
      AND speakerRaw IS NOT NULL
    """)
    suspend fun countTotalMessagesInRange(id: String, startMillis: Long, endMillis: Long): Int

    @Query("""
    SELECT COUNT(*) FROM messages 
    WHERE conversationId = :id 
      AND timestampEpochMillis BETWEEN :startMillis AND :endMillis 
      AND speakerRaw IS NOT NULL
      AND toxScore IS NOT NULL
    """)
    suspend fun countAnalyzedMessagesInRange(id: String, startMillis: Long, endMillis: Long): Int

    @Query("""
    SELECT * FROM messages
    WHERE conversationId = :id
      AND timestampEpochMillis BETWEEN :startMillis AND :endMillis
      AND speakerRaw IS NOT NULL
      AND toxScore IS NULL
    ORDER BY timestampEpochMillis ASC
    LIMIT :limit
    """)
    suspend fun getUnanalyzedChunkInRange(
        id: String,
        startMillis: Long,
        endMillis: Long,
        limit: Int
    ): List<MessageEntity>

    @Query("""
        UPDATE messages
        SET toxScore = :score, isToxic = :isToxic, modelVersion = :version
        WHERE id = :id
    """)
    suspend fun updateToxicity(id: Long, score: Double, isToxic: Boolean, version: String)

    @Transaction
    suspend fun updateToxicityBatch(updates: List<ToxicityUpdate>) {
        updates.forEach { u -> updateToxicity(u.id, u.score, u.isToxic, u.version) }
    }

    data class ToxicityUpdate(val id: Long, val score: Double, val isToxic: Boolean, val version: String)

    @Query("""
        SELECT id, timestampEpochMillis, speakerRaw, textOriginal AS content, toxScore, isToxic
        FROM messages
        WHERE conversationId = :id
          AND timestampEpochMillis BETWEEN :startMillis AND :endMillis
        ORDER BY timestampEpochMillis ASC
    """)
    fun getMessagesLiteInRangeFlow(
        id: String,
        startMillis: Long,
        endMillis: Long
    ): Flow<List<MessageLiteProjection>>

    @Query("""
        SELECT id, timestampEpochMillis, speakerRaw, textOriginal AS content, toxScore, isToxic
        FROM messages
        WHERE conversationId = :id
          AND timestampEpochMillis BETWEEN :startMillis AND :endMillis
        ORDER BY timestampEpochMillis ASC
    """)
    suspend fun getMessagesLiteInRange(
        id: String,
        startMillis: Long,
        endMillis: Long
    ): List<MessageLiteProjection>

    @Query("""
        SELECT id, timestampEpochMillis, speakerRaw, textOriginal AS content, toxScore, isToxic
        FROM messages
        WHERE conversationId = :id
          AND timestampEpochMillis BETWEEN :startMillis AND :endMillis
          AND CAST(strftime('%w', timestampEpochMillis / 1000, 'unixepoch', 'localtime') AS INTEGER) = :dayOfWeekSql
          AND CAST(strftime('%H', timestampEpochMillis / 1000, 'unixepoch', 'localtime') AS INTEGER) = :hour
        ORDER BY timestampEpochMillis ASC
    """)
    fun getMessagesByPatternFlow(
        id: String,
        startMillis: Long,
        endMillis: Long,
        dayOfWeekSql: Int, // 0=Sun, 1=Mon, ..., 6=Sat
        hour: Int
    ): Flow<List<MessageLiteProjection>>

    @Query("""
    SELECT * FROM weekly_points
    WHERE conversationId = :conversationId
    ORDER BY weekId ASC
""")
    suspend fun getWeeklyPointsOnce(conversationId: String): List<WeeklyPointEntity>

    @Query("SELECT COUNT(DISTINCT speakerRaw) FROM messages WHERE conversationId = :id AND speakerRaw IS NOT NULL AND speakerRaw != ''")
    suspend fun countDistinctSpeakers(id: String): Int

    data class MessageLiteProjection(
        val id: Long,
        val timestampEpochMillis: Long,
        val speakerRaw: String?,
        val content: String?,
        val toxScore: Double?,
        val isToxic: Boolean?
    )
}

data class AnalysisMetadataFields(
    val analysisStatus: AnalysisStatus,
    val analysisAnalyzedCount: Int,
    val analysisTotalCount: Int,
    val analysisRangePreset: AnalysisRangePreset?,
    val analysisRangeStartMillis: Long?,
    val analysisRangeEndMillis: Long?,
    val analysisLastAnalyzedAtIso: String?,
    val analysisModelVersion: String?
)
