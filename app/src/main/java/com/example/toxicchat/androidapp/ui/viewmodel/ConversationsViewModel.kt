package com.example.toxicchat.androidapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.toxicchat.androidapp.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    repository: ChatRepository
) : ViewModel() {
    val conversations = repository.getConversations()
}
