package com.example.toxicchat.androidapp

sealed class Screen(val route: String) {
    object PinGate : Screen("pin_gate")
    object Disclaimer : Screen("disclaimer")
    object PrivacyDetails : Screen("privacy_details")
    object Import : Screen("import")
    object Conversations : Screen("conversations")
    object CreatePin : Screen("create_pin")
    object EnterPin : Screen("enter_pin")
}
