package com.example.toxicchat.androidapp.domain.analyzer

import com.example.toxicchat.androidapp.domain.model.MessageRecord

interface ToxicityAnalyzer {
    suspend fun analyze(messages: List<MessageRecord>): List<MessageRecord>
    
    companion object {
        const val DEFAULT_THRESHOLD = 0.5
    }
}
