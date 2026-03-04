package com.example.toxicchat.androidapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.toxicchat.androidapp.ui.viewmodel.OnboardingViewModel

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val globalName by viewModel.globalUserName.collectAsState()
    var nameInput by remember { mutableStateOf("") }

    LaunchedEffect(globalName) {
        if (globalName != null) {
            onFinished()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Benvenuto!",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Come ti chiami? Questo aiuterà ad allineare i tuoi messaggi nelle chat.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Tuo Nome") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { if (nameInput.isNotBlank()) viewModel.saveUserName(nameInput) },
                enabled = nameInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Inizia")
            }
        }
    }
}
