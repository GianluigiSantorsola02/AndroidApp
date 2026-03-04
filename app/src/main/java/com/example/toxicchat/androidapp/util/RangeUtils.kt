package com.example.toxicchat.androidapp.util

import com.example.toxicchat.androidapp.domain.model.AnalysisRangePreset

object RangeUtils {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun computeRange(
        preset: AnalysisRangePreset,
        minTs: Long,
        maxTs: Long,
        now: Long = maxTs
    ): Pair<Long, Long> {
        val start = when (preset) {
            AnalysisRangePreset.LAST_MONTH -> now - 30L * DAY_MS
            AnalysisRangePreset.LAST_6_MONTHS -> now - 180L * DAY_MS
            AnalysisRangePreset.ALL_TIME -> minTs
        }
        val safeStart = start.coerceAtLeast(minTs)
        val safeEnd = now.coerceIn(minTs, maxTs)
        return safeStart to safeEnd
    }
}
