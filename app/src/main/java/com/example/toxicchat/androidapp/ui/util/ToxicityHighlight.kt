package com.example.toxicchat.androidapp.ui.util

import androidx.compose.ui.graphics.Color

enum class ToxicityLevel {
    NONE, MEDIUM, HIGH
}

object ToxicityConstants {
    const val TAU = 0.50 //0.50 DEFAULT
    const val TAU_HIGH = 0.80 //0.80 DEFAULT

    // Colori per lo sfondo (discrezione: ora usati meno)
    val COLOR_MEDIUM = Color(0xFFFFF9C4) // Giallo chiaro
    val COLOR_HIGH = Color(0xFFFFCDD2)   // Rosso chiaro

    // Colori per la stripe laterale (più vivaci)
    val STRIPE_MEDIUM = Color(0xFFFFD600) // Giallo ambra
    val STRIPE_HIGH = Color(0xFFF44336)   // Rosso
}

fun getToxicityLevel(score: Double?): ToxicityLevel {
    if (score == null) return ToxicityLevel.NONE
    return when {
        score >= ToxicityConstants.TAU_HIGH -> ToxicityLevel.HIGH
        score >= ToxicityConstants.TAU -> ToxicityLevel.MEDIUM
        else -> ToxicityLevel.NONE
    }
}

fun getToxicityHighlightColor(score: Double?): Color? {
    return when (getToxicityLevel(score)) {
        ToxicityLevel.HIGH -> ToxicityConstants.COLOR_HIGH
        ToxicityLevel.MEDIUM -> ToxicityConstants.COLOR_MEDIUM
        ToxicityLevel.NONE -> null
    }
}

fun getToxicityStripeColor(level: ToxicityLevel): Color {
    return when (level) {
        ToxicityLevel.HIGH -> ToxicityConstants.STRIPE_HIGH
        ToxicityLevel.MEDIUM -> ToxicityConstants.STRIPE_MEDIUM
        ToxicityLevel.NONE -> Color.Transparent
    }
}
