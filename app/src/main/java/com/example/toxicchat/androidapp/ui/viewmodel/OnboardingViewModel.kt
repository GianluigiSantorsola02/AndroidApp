package com.example.toxicchat.androidapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toxicchat.androidapp.data.local.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {
    val globalUserName = userPrefs.globalUserName.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
    )

    fun saveUserName(name: String) {
        viewModelScope.launch {
            userPrefs.setGlobalUserName(name)
        }
    }
}
