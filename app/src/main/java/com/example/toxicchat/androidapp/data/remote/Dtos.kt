package com.example.toxicchat.androidapp.data.remote

const val MODEL_VERSION = "textdetox/xlmr-large-toxicity-classifier-v2"

data class ScoreItemDto(
    val messageLocalId: Long,
    val text: String
)

data class ScoreRequestDto(
    val conversationId: String,
    val modelVersion: String,
    val items: List<ScoreItemDto>
)

fun buildScoreRequest(conversationId: String, items: List<ScoreItemDto>): ScoreRequestDto {
    return ScoreRequestDto(
        conversationId = conversationId,
        modelVersion = MODEL_VERSION,
        items = items
    )
}

data class ScoredItemDto(
    val messageLocalId: Long,
    val score: Double
)

data class ScoreResponseDto(
    val modelVersion: String,
    val results: List<ScoredItemDto>
)

data class HealthResponseDto(
    val ok: Boolean
)