package com.example.toxicchat.androidapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.toxicchat.androidapp.data.importer.SharedImportManager
import com.example.toxicchat.androidapp.ui.screens.*
import com.example.toxicchat.androidapp.ui.screens.import_.ImportCompletedScreen
import com.example.toxicchat.androidapp.ui.screens.privacy.PrivacyDetailsScreen
import com.example.toxicchat.androidapp.ui.screens.results.ResultsScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sharedImportManager: SharedImportManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        
        setContent {
            val colorScheme = lightColorScheme(
                primary = Color(0xFF006064),
                background = Color.White,
                surface = Color.White
            )
            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                sharedImportManager.handleSharedFile(uri)
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    var disclaimerAcceptedInSession by remember { mutableStateOf(false) }

    NavHost(
        navController = navController, 
        startDestination = Screen.PinGate.route
    ) {
        
        composable(Screen.PinGate.route) {
            PinScreen(onAuthenticated = {
                if (disclaimerAcceptedInSession) {
                    navController.navigate(Screen.Import.route) {
                        popUpTo(Screen.PinGate.route) { inclusive = true }
                    }
                } else {
                    navController.navigate(Screen.Disclaimer.route) {
                        popUpTo(Screen.PinGate.route) { inclusive = true }
                    }
                }
            })
        }

        composable(Screen.Disclaimer.route) {
            DisclaimerScreen(onAccepted = {
                disclaimerAcceptedInSession = true
                navController.navigate(Screen.Import.route) {
                    popUpTo(Screen.Disclaimer.route) { inclusive = true }
                }
            })
        }

        composable(Screen.PrivacyDetails.route) {
            PrivacyDetailsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Import.route) {
            ImportScreen(
                onNavigateToImportCompleted = { id ->
                    navController.navigate("import_completed/$id")
                }
            )
        }

        composable("import_completed/{conversationId}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("conversationId") ?: ""
            ImportCompletedScreen(
                conversationId = id,
                onAnalyzeNow = { 
                    navController.navigate("results/$id") {
                        popUpTo("import_completed/$id") { inclusive = true }
                    }
                },
                onShowPrivacy = { navController.navigate(Screen.PrivacyDetails.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "results/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("conversationId") ?: ""
            ResultsScreen(
                conversationId = id,
                onBack = { 
                    navController.popBackStack()
                },
                onOpenHighlighted = { /* TODO */ },
                onExportPdf = { /* TODO */ },
                onRequestPaohVisExport = { /* TODO */ },
                onShowPrivacy = { navController.navigate(Screen.PrivacyDetails.route) }
            )
        }

        composable(Screen.Conversations.route) {
            ConversationsScreen(
                onNavigateToResults = { id ->
                    navController.navigate("results/$id")
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
