package com.example.toxicchat.androidapp.data.local

import androidx.room.TypeConverter
import com.example.toxicchat.androidapp.domain.model.AnalysisRangePreset
import com.example.toxicchat.androidapp.domain.model.AnalysisStatus
import com.example.toxicchat.androidapp.domain.model.Speaker
import org.json.JSONArray

class Converters {

    @TypeConverter
    fun fromSpeaker(value: Speaker?): String? = value?.name

    @TypeConverter
    fun toSpeaker(value: String?): Speaker? = value?.let { Speaker.valueOf(it) }

    @TypeConverter
    fun fromStringList(value: List<String>): String = JSONArray(value).toString()

    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        val arr = JSONArray(value)
        return List(arr.length()) { i -> arr.getString(i) }
    }

    @TypeConverter
    fun fromAnalysisStatus(value: AnalysisStatus): String = value.name

    @TypeConverter
    fun toAnalysisStatus(value: String): AnalysisStatus = AnalysisStatus.valueOf(value)

    @TypeConverter
    fun fromRangePreset(value: AnalysisRangePreset?): String? = value?.name

    @TypeConverter
    fun toRangePreset(value: String?): AnalysisRangePreset? = value?.let { AnalysisRangePreset.valueOf(it) }
}
