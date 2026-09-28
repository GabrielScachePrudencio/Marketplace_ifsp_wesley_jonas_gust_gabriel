package com.example.marketplace.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.marketplace.repository.VendaRepository
import com.example.marketplace.model.Venda
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class AtualizarStatusUiState {
    object Idle : AtualizarStatusUiState()
    object Loading : AtualizarStatusUiState()
    object Sucesso : AtualizarStatusUiState()
    data class Erro(val mensagem: String) : AtualizarStatusUiState()
}

class VendaListViewModel(
    private val repository: VendaRepository
) : ViewModel() {

    val vendas: StateFlow<List<Venda>> = repository.buscarVendas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _statusUiState = MutableStateFlow<AtualizarStatusUiState>(AtualizarStatusUiState.Idle)
    val statusUiState: StateFlow<AtualizarStatusUiState> = _statusUiState

    fun avancarStatus(
        vendaId: String,
        novoStatus: String,
        perfil: String? = null,
        motoristaId: String? = null,
        veiculoId: String? = null
    ) {
        viewModelScope.launch {
            _statusUiState.value = AtualizarStatusUiState.Loading
            try {
                repository.atualizarStatusVenda(
                    id = vendaId,
                    novoStatus = novoStatus,
                    perfil = perfil,
                    motoristaId = motoristaId,
                    veiculoId = veiculoId
                )
                _statusUiState.value = AtualizarStatusUiState.Sucesso
            } catch (e: Exception) {
                _statusUiState.value = AtualizarStatusUiState.Erro(e.message ?: "Erro ao atualizar status")
            }
        }
    }

    fun atualizarStatus(
        vendaId: String,
        novoStatus: String,
        perfil: String? = null,
        motoristaId: String? = null,
        veiculoId: String? = null
    ) = avancarStatus(vendaId, novoStatus, perfil, motoristaId, veiculoId)

    // NOVO: marca pronto para entrega + já vincula o ponto de origem e o destino
    fun marcarProntoParaEntrega(
        venda: Venda,
        pontoOrigemId: String,
        enderecoDestinoTexto: String,
        destinoLat: Double,
        destinoLng: Double
    ) {
        viewModelScope.launch {
            _statusUiState.value = AtualizarStatusUiState.Loading
            try {
                repository.atualizarStatusVenda(
                    id = venda.id,
                    novoStatus = "PRONTO_PARA_ENTREGA",
                    perfil = "negociador",
                    pontoOrigemId = pontoOrigemId,
                    enderecoDestino = enderecoDestinoTexto,
                    destinoLat = destinoLat,
                    destinoLng = destinoLng
                )
                _statusUiState.value = AtualizarStatusUiState.Sucesso
            } catch (e: Exception) {
                _statusUiState.value = AtualizarStatusUiState.Erro(e.message ?: "Erro ao marcar pronto para entrega")
            }
        }
    }

    fun resetarStatus() {
        _statusUiState.value = AtualizarStatusUiState.Idle
    }
}