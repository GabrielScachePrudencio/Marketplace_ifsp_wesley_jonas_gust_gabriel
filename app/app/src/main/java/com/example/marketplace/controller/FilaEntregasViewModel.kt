package com.example.marketplace.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.marketplace.repository.RastreioEntregaRepository
import com.example.marketplace.model.RastreioEntrega
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class FilaEntregasUiState {
    object Idle : FilaEntregasUiState()
    object Loading : FilaEntregasUiState()
    data class Sucesso(val mensagem: String) : FilaEntregasUiState()
    data class Erro(val mensagem: String) : FilaEntregasUiState()
}

class FilaEntregasViewModel(
    private val repository: RastreioEntregaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<FilaEntregasUiState>(FilaEntregasUiState.Idle)
    val uiState: StateFlow<FilaEntregasUiState> = _uiState.asStateFlow()

    private val _fila = MutableStateFlow<List<RastreioEntrega>>(emptyList())
    val fila: StateFlow<List<RastreioEntrega>> = _fila.asStateFlow()

    fun carregarFilaDoMotorista(motoristaId: String, negociantesIds: List<String> = emptyList()) {
        if (motoristaId.isBlank()) {
            _fila.value = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                // Carrega a fila do motorista
                repository.buscarFilaDoMotorista(motoristaId).collect { filaM ->
                    _fila.value = filaM
                }
            } catch (e: Exception) {
                _uiState.value = FilaEntregasUiState.Erro(e.message ?: "Erro ao carregar fila")
            }
        }
    }

    fun atualizarPosicaoNaFila(rastreioId: String, novaPosicao: Int, onSucesso: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = FilaEntregasUiState.Loading
            try {
                repository.atualizarPosicaoNaFila(rastreioId, novaPosicao)
                _uiState.value = FilaEntregasUiState.Sucesso("Posição atualizada")
                onSucesso()
            } catch (e: Exception) {
                _uiState.value = FilaEntregasUiState.Erro(e.message ?: "Erro ao atualizar posição")
            }
        }
    }

    fun iniciarEntrega(rastreioId: String, motoristaId: String, posicao: Int, onSucesso: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = FilaEntregasUiState.Loading
            try {
                repository.iniciarEntrega(rastreioId, motoristaId, posicao)
                _uiState.value = FilaEntregasUiState.Sucesso("Entrega iniciada")
                onSucesso()
            } catch (e: Exception) {
                _uiState.value = FilaEntregasUiState.Erro(e.message ?: "Erro ao iniciar entrega")
            }
        }
    }


    fun finalizarEntrega(rastreioId: String, ultimaLat: Double?, ultimaLng: Double?, onSucesso: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = FilaEntregasUiState.Loading
            try {
                repository.finalizarEntrega(rastreioId, ultimaLat, ultimaLng)

                // AQUI: remove o item da fila imediatamente
                _fila.value = _fila.value.filter { it.id != rastreioId }

                _uiState.value = FilaEntregasUiState.Sucesso("Entrega finalizada")
                onSucesso()
            } catch (e: Exception) {
                _uiState.value = FilaEntregasUiState.Erro(e.message ?: "Erro ao finalizar entrega")
            }
        }
    }

    fun limparFila(motoristaId: String, onSucesso: () -> Unit = {}) {
        if (motoristaId.isBlank()) return

        viewModelScope.launch {
            _uiState.value = FilaEntregasUiState.Loading
            try {
                repository.limparFilaDoMotorista(motoristaId)

                _fila.value = emptyList()

                _uiState.value = FilaEntregasUiState.Sucesso("Fila limpa")
                onSucesso()
            } catch (e: Exception) {
                _uiState.value = FilaEntregasUiState.Erro(e.message ?: "Erro ao limpar fila")
            }
        }
    }

    fun resetar() {
        _uiState.value = FilaEntregasUiState.Idle
    }
}
