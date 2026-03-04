package com.example.toxicchat.androidapp.domain.repository

import com.example.toxicchat.androidapp.domain.model.*
import kotlinx.coroutines.flow.Flow

interface AnalysisRepository {
    fun getAnalysisResult(conversationId: String): Flow<AnalysisResult>

    suspend fun setStatus(conversationId: String, status: AnalysisStatus)
    suspend fun saveMetadata(conversationId: String, metadata: AnalysisMetadata)

    suspend fun saveAggregates(
        conversationId: String,
        weekly: List<WeeklyPoint>,
        heatmap: List<HeatmapCell>,
        response: ResponseTimeStats?
    )

    suspend fun clearAggregates(conversationId: String)

    // --- range support ---
    suspend fun getMinTimestamp(conversationId: String): Long?
    suspend fun getMaxTimestamp(conversationId: String): Long?
    suspend fun getCurrentRange(conversationId: String): Triple<AnalysisRangePreset, Long?, Long?>?
    suspend fun setAnalysisRange(conversationId: String, preset: AnalysisRangePreset, startMillis: Long, endMillis: Long)

    suspend fun countTotalMessagesInRange(conversationId: String, startMillis: Long, endMillis: Long): Int
    suspend fun countAnalyzedMessagesInRange(conversationId: String, startMillis: Long, endMillis: Long): Int
}
