package com.example.toxicchat.androidapp.domain.model
data class MessageLite(
    val id: Long,
    val timestampEpochMillis: Long,
    val speakerRaw: String?,
    val toxScore: Double?,
    val isToxic: Boolean?
)