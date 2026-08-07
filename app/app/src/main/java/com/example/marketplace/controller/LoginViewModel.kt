package com.example.marketplace.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.marketplace.data.repository.UsuarioRepository
import com.example.marketplace.model.Usuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Sucesso(val usuario: Usuario) : LoginUiState()
    data class Erro(val mensagem: String) : LoginUiState()
}

class LoginViewModel(
    private val repository: UsuarioRepository = UsuarioRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    fun login(email: String, senha: String) {
        if (email.isBlank() || senha.isBlank()) {
            _uiState.value = LoginUiState.Erro("Preencha email e senha")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val usuario = repository.login(email, senha)
                _uiState.value = LoginUiState.Sucesso(usuario)
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Erro(e.message ?: "Erro ao fazer login")
            }
        }
    }
}