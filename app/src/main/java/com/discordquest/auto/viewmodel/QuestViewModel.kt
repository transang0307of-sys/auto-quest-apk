package com.discordquest.auto.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.discordquest.auto.model.*
import com.discordquest.auto.network.DiscordApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class UiState {
    object Idle : UiState()
    object Verifying : UiState()
    data class Running(val update: ProgressUpdate) : UiState()
    data class Done(val username: String, val results: List<QuestResult>) : UiState()
    data class Error(val message: String) : UiState()
}

class QuestViewModel : ViewModel() {

    private val _uiState = MutableLiveData<UiState>(UiState.Idle)
    val uiState: LiveData<UiState> = _uiState

    private val _username = MutableLiveData<String>("")
    val username: LiveData<String> = _username

    fun startQuests(token: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Verifying

            // Verify token
            val name = try {
                withContext(Dispatchers.IO) { DiscordApiClient.verifyToken(token) }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Token không hợp lệ hoặc đã hết hạn.\n${e.message}")
                return@launch
            }

            _username.value = name

            // Run quests
            DiscordApiClient.runQuests(token) { progress ->
                withContext(Dispatchers.Main) {
                    _uiState.value = UiState.Running(progress)
                }
            }.collect { results ->
                withContext(Dispatchers.Main) {
                    _uiState.value = UiState.Done(name, results)
                }
            }
        }
    }

    fun reset() {
        _uiState.value = UiState.Idle
        _username.value = ""
    }
}
