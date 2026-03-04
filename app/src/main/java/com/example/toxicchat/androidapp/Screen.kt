package com.example.toxicchat.androidapp

sealed class Screen(val route: String) {
    object PinGate : Screen("pin_gate")
    object Disclaimer : Screen("disclaimer")
    object PrivacyDetails : Screen("privacy_details")
    object Import : Screen("import")
    object Conversations : Screen("conversations")
    object IdentitySelection : Screen("identity_selection/{conversationId}?suggestedName={suggestedName}") {
        fun createRoute(conversationId: String, suggestedName: String? = null) = 
            "identity_selection/$conversationId" + if (suggestedName != null) "?suggestedName=$suggestedName" else ""
    }
    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/$conversationId"
    }
    object CreatePin : Screen("create_pin")
    object EnterPin : Screen("enter_pin")
}
