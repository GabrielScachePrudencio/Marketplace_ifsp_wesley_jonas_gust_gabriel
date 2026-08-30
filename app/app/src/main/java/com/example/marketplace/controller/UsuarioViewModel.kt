package com.example.marketplace.controller

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.marketplace.data.local.AppDatabase
import com.example.marketplace.data.repository.UsuarioRepository
import com.example.marketplace.model.Usuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UsuarioViewModel(
    private val repository: UsuarioRepository
) : ViewModel() {

    private val _negociantes = MutableStateFlow<List<Usuario>>(emptyList())
    val negociantes: StateFlow<List<Usuario>> = _negociantes

    private val _vinculando = MutableStateFlow(false)
    val vinculando: StateFlow<Boolean> = _vinculando

    fun carregarNegociantes() {
        viewModelScope.launch {
            _negociantes.value = repository.listarNegociantes()
        }
    }

    fun vincularNegociante(
        motoristaUid: String,
        negocianteId: String,
        onSucesso: () -> Unit
    ) {
        viewModelScope.launch {
            _vinculando.value = true
            try {
                repository.vincularNegociante(motoristaUid, negocianteId)
                onSucesso()
            } finally {
                _vinculando.value = false
            }
        }
    }
}

class UsuarioViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getDatabase(context)
        val repository = UsuarioRepository(db.usuarioDao(),db.pendenteSycronizacaoDao())
        @Suppress("UNCHECKED_CAST")
        return UsuarioViewModel(repository) as T
    }
}