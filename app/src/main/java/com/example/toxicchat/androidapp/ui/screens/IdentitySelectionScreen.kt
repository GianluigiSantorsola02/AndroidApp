package com.example.toxicchat.androidapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.toxicchat.androidapp.ui.viewmodel.IdentityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentitySelectionScreen(
    conversationId: String,
    suggestedName: String?,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: IdentityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(conversationId) {
        viewModel.loadData(conversationId, suggestedName)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onFinished()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Scaffold(
            containerColor = Color.White,
            topBar = {
                TopAppBar(
                    title = { },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "Chi sei tu?",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = "Seleziona il nome che corrisponde a te nell'export per distinguere i tuoi messaggi da quelli degli altri.",
                    color = Color.Gray,
                    lineHeight = 22.sp,
                    fontSize = 16.sp
                )

                Spacer(Modifier.height(32.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.participants) { participant ->
                        val isSelected = uiState.selectedSelfName == participant
                        ParticipantCard(
                            name = participant,
                            isSelected = isSelected,
                            onSelect = { viewModel.selectParticipant(participant) }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick = { viewModel.saveIdentity() },
                        enabled = uiState.selectedSelfName != null,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(
                            text = "Conferma",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (uiState.selectedSelfName != null) Color.Black else Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticipantCard(name: String, isSelected: Boolean, onSelect: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) Color(0xFF006064) else Color(0xFFEEEEEE)
        ),
        color = Color.White,
        shadowElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color(0xFFF5F5F5)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PersonOutline,
                        contentDescription = null,
                        tint = Color.Gray
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Text(
                text = name,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                modifier = Modifier.weight(1f),
                fontSize = 16.sp
            )
            
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFF006064),
                    unselectedColor = Color.LightGray
                )
            )
        }
    }
}
