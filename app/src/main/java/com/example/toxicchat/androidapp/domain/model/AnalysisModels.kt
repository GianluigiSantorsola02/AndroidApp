package com.example.toxicchat.androidapp.domain.model

enum class AnalysisStatus { NON_ANALIZZATA, IN_CORSO, ANALIZZATA, ERRORE, PAUSA }

enum class AnalysisRangePreset { LAST_MONTH, LAST_6_MONTHS, ALL_TIME }

data class AnalysisMetadata(
    val analyzedCount: Int = 0,
    val totalCount: Int = 0,
    val rangePreset: AnalysisRangePreset? = null,
    val rangeStartMillis: Long? = null,
    val rangeEndMillis: Long? = null,
    val lastAnalyzedAtIso: String? = null,
    val modelVersion: String? = null
)

data class WeeklyPoint(
    val weekId: String,
    val totalMessages: Int,
    val toxicMessages: Int,
    val toxicRate: Double
)

data class HeatmapCell(
    val dayOfWeek: Int, // 1..7
    val hour: Int,      // 0..23
    val totalCount: Int,
    val toxicCount: Int,
    val toxicRate: Double
)

data class ResponseTimeStats(
    val medianSelfToOtherMin: Double,
    val medianOtherToSelfMin: Double,
    val meanSelfToOtherMin: Double,
    val meanOtherToSelfMin: Double
)

data class SpeakerToxicityStat(
    val speakerLabel: String,
    val totalCount: Int,
    val toxicCount: Int,
    val toxicRate: Double
)

data class AnalysisResult(
    val status: AnalysisStatus,
    val metadata: AnalysisMetadata,
    val weeklySeries: List<WeeklyPoint> = emptyList(),
    val heatmap: List<HeatmapCell> = emptyList(),
    val responseStats: ResponseTimeStats? = null,
    val speakerStats: List<SpeakerToxicityStat> = emptyList()
)
