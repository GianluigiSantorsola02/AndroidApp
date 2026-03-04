package com.example.toxicchat.androidapp.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsAppColorUtilsTest {
    @Test
    fun `getSenderColor returns same color for same name`() {
        val name = "Gianluca"
        val color1 = WhatsAppColorUtils.getSenderColor(name)
        val color2 = WhatsAppColorUtils.getSenderColor(name)
        val color3 = WhatsAppColorUtils.getSenderColor(" gianluca ")

        assertEquals(color1, color2)
        assertEquals(color1, color3)
    }

    @Test
    fun `getSenderColor returns different colors for different names`() {
        val color1 = WhatsAppColorUtils.getSenderColor("Alice")
        val color2 = WhatsAppColorUtils.getSenderColor("Bob")
        
        // C'è una piccola possibilità di collisione hash, ma per nomi comuni deve differire
        assert(color1 != color2)
    }
}
