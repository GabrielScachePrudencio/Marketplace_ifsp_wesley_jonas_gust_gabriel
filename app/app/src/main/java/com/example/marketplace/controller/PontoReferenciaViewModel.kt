package com.example.marketplace.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.marketplace.repository.PontoReferenciaRepository
import com.example.marketplace.model.PontoReferencia
import com.example.marketplace.model.enums.TipoPontoReferencia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PontoReferenciaUiState {
    object Idle : PontoReferenciaUiState()
    object Loading : PontoReferenciaUiState()
    data class Sucesso(val ponto: PontoReferencia) : PontoReferenciaUiState()
    data class Erro(val mensagem: String) : PontoReferenciaUiState()
}

class PontoReferenciaViewModel(
    private val repository: PontoReferenciaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PontoReferenciaUiState>(PontoReferenciaUiState.Idle)
    val uiState: StateFlow<PontoReferenciaUiState> = _uiState.asStateFlow()

    private val _pontos = MutableStateFlow<List<PontoReferencia>>(emptyList())
    val pontos: StateFlow<List<PontoReferencia>> = _pontos.asStateFlow()

    fun criarPonto(
        negocianteId: String,
        nome: String,
        tipo: TipoPontoReferencia,
        rua: String,
        numero: String,
        cidade: String,
        estado: String,
        cep: String,
        latitude: Double,
        longitude: Double,
        principal: Boolean = false
    ) {
        if (negocianteId.isBlank()) {
            _uiState.value = PontoReferenciaUiState.Erro("Negociante não informado")
            return
        }
        if (nome.isBlank()) {
            _uiState.value = PontoReferenciaUiState.Erro("Informe o nome do ponto")
            return
        }
        if (rua.isBlank() || numero.isBlank()) {
            _uiState.value = PontoReferenciaUiState.Erro("Informe o endereço completo")
            return
        }
        if (latitude == 0.0 || longitude == 0.0) {
            _uiState.value = PontoReferenciaUiState.Erro("Selecione a localização no mapa")
            return
        }

        viewModelScope.launch {
            _uiState.value = PontoReferenciaUiState.Loading
            try {
                val ponto = repository.criarPonto(
                    negocianteId = negocianteId,
                    nome = nome,
                    tipo = tipo,
                    rua = rua,
                    numero = numero,
                    cidade = cidade,
                    estado = estado,
                    cep = cep,
                    latitude = latitude,
                    longitude = longitude,
                    principal = principal
                )
                _uiState.value = PontoReferenciaUiState.Sucesso(ponto)
                carregarPontosDoNegociante(negocianteId)
            } catch (e: Exception) {
                _uiState.value = PontoReferenciaUiState.Erro(e.message ?: "Erro ao criar ponto")
            }
        }
    }

    fun atualizarPonto(ponto: PontoReferencia, onSucesso: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = PontoReferenciaUiState.Loading
            try {
                repository.atualizarPonto(ponto)
                _uiState.value = PontoReferenciaUiState.Sucesso(ponto)
                carregarPontosDoNegociante(ponto.negocianteId)
                onSucesso()
            } catch (e: Exception) {
                _uiState.value = PontoReferenciaUiState.Erro(e.message ?: "Erro ao atualizar ponto")
            }
        }
    }

    fun excluirPonto(ponto: PontoReferencia, onSucesso: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = PontoReferenciaUiState.Loading
            try {
                repository.excluirPonto(ponto)
                _uiState.value = PontoReferenciaUiState.Idle
                carregarPontosDoNegociante(ponto.negocianteId)
                onSucesso()
            } catch (e: Exception) {
                _uiState.value = PontoReferenciaUiState.Erro(e.message ?: "Erro ao excluir ponto")
            }
        }
    }

    fun carregarPontosDoNegociante(negocianteId: String) {
        if (negocianteId.isBlank()) {
            _pontos.value = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                repository.buscarPontosDoNegociante(negocianteId).collect { lista ->
                    _pontos.value = lista
                }
            } catch (e: Exception) {
                _uiState.value = PontoReferenciaUiState.Erro(e.message ?: "Erro ao carregar pontos")
            }
        }
    }

    fun buscarPontoPorId(id: String, onResultado: (PontoReferencia?) -> Unit) {
        viewModelScope.launch {
            try {
                val ponto = repository.buscarPontoPorId(id)
                onResultado(ponto)
            } catch (e: Exception) {
                _uiState.value = PontoReferenciaUiState.Erro(e.message ?: "Erro ao buscar ponto")
                onResultado(null)
            }
        }
    }

    fun resetar() {
        _uiState.value = PontoReferenciaUiState.Idle
    }
}
