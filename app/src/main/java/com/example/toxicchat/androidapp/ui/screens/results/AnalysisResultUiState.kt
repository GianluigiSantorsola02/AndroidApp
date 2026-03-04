package com.example.toxicchat.androidapp.ui.screens.results

import com.example.toxicchat.androidapp.domain.model.AnalysisResult

sealed class AnalysisResultUiState {
    data object Loading : AnalysisResultUiState()
    data class Success(val result: AnalysisResult) : AnalysisResultUiState()
    data class Error(val message: String) : AnalysisResultUiState()
}
