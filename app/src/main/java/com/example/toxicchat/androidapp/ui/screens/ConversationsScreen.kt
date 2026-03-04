package com.example.toxicchat.androidapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.toxicchat.androidapp.ui.viewmodel.ConversationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onNavigateToChat: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel()
) {
    val list by viewModel.conversations.collectAsState(initial = emptyList())

    Scaffold(
        topBar = { 
            CenterAlignedTopAppBar(
                title = { Text("Conversazioni") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            ) 
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(list) { conv ->
                ListItem(
                    headlineContent = { Text(conv.title) },
                    supportingContent = { Text("Msg: ${conv.parsedMessagesCount} (System: ${conv.systemMessagesCount}) - ${conv.createdAt}") },
                    modifier = Modifier.clickable { onNavigateToChat(conv.id) }
                )
                HorizontalDivider()
            }
        }
    }
}
