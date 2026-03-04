package com.example.toxicchat.androidapp.domain.analyzer

import java.util.zip.CRC32

object FakeAnalyzer {
    private const val THRESHOLD = 0.5
    fun analyze(text: String): Pair<Double, Boolean> {
        val score = (text.hashCode().toLong().ushr(1) % 100).toDouble() / 100.0
        return score to (score >= THRESHOLD)
    }
}
