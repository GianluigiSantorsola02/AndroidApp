package com.example.toxicchat.androidapp.data.remote

import android.util.Log

object RemoteSmokeTest {

    suspend fun run() {
        val api = ToxicityClient.createApi()

        // 1. Health check
        try {
            val healthResponse = api.health()
            Log.d("RemoteSmokeTest", "Health ok: ${healthResponse.ok}")
        } catch (e: Exception) {
            Log.e("RemoteSmokeTest", "Health check failed", e)
        }

        // 2. Score request
        try {
            val request = buildScoreRequest(
                conversationId = "test-conversation",
                items = listOf(
                    ScoreItemDto(messageLocalId = 1, text = "I love this!"),
                    ScoreItemDto(messageLocalId = 2, text = "I hate this so much.")
                )
            )
            val scoreResponse = api.score(request)
            val scores = scoreResponse.results.map { it.score }
            Log.d("RemoteSmokeTest", "Received scores: $scores")
        } catch (e: Exception) {
            Log.e("RemoteSmokeTest", "Score request failed", e)
        }
    }
}
