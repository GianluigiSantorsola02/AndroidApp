package com.example.toxicchat.androidapp.ui.util

import androidx.compose.ui.graphics.Color

object WhatsAppColorUtils {
    private val palette = listOf(
        Color(0xFF128C7E), // Teal
        Color(0xFF34B7F1), // Light Blue
        Color(0xFF25D366), // Light Green
        Color(0xFFDCF8C6), // Pale Green (not for names)
        Color(0xFF075E54), // Dark Teal
        Color(0xFFEE1D23), // Red
        Color(0xFFF18F01), // Orange
        Color(0xFF048A81), // Green
        Color(0xFF540D6E), // Purple
        Color(0xFFEE4266), // Pink
        Color(0xFFFFD23F), // Yellow (darkened)
        Color(0xFF3BCEAC), // Mint
        Color(0xFF0EAD69), // Emerald
        Color(0xFF54478C), // Violet
        Color(0xFF2E5EAA), // Blue
        Color(0xFFF25C54)  // Coral
    )

    /**
     * Restituisce un colore deterministico per un nome utente.
     */
    fun getSenderColor(speakerRaw: String?): Color {
        if (speakerRaw == null) return Color.Gray
        val normalized = speakerRaw.trim().lowercase()
        val hash = normalized.hashCode()
        val index = Math.abs(hash) % palette.size
        return palette[index]
    }
}
