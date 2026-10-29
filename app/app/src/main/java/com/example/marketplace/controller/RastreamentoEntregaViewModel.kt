package com.example.marketplace.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.marketplace.repository.PontoReferenciaRepository
import com.example.marketplace.repository.RastreioEntregaRepository
import com.example.marketplace.repository.VendaRepository
import com.example.marketplace.model.PontoReferencia
import com.example.marketplace.model.RastreioEntrega
import com.example.marketplace.model.Venda
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed class RastreamentoEntregaUiState {
    object Idle : RastreamentoEntregaUiState()
    object Loading : RastreamentoEntregaUiState()
    data class Carregado(
        val venda: Venda,
        val rastreio: RastreioEntrega?,
        val pontoOrigem: PontoReferencia?
    ) : RastreamentoEntregaUiState()
    data class Erro(val mensagem: String) : RastreamentoEntregaUiState()
}

data class LocalizacaoMotorista(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

class RastreamentoEntregaViewModel(
    private val vendaRepository: VendaRepository,
    private val rastreioEntregaRepository: RastreioEntregaRepository,
    private val pontoReferenciaRepository: PontoReferenciaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RastreamentoEntregaUiState>(RastreamentoEntregaUiState.Idle)
    val uiState: StateFlow<RastreamentoEntregaUiState> = _uiState.asStateFlow()

    private val _localizacaoMotorista = MutableStateFlow<LocalizacaoMotorista?>(null)
    val localizacaoMotorista: StateFlow<LocalizacaoMotorista?> = _localizacaoMotorista.asStateFlow()

    private var listenerLocalizacao: ListenerRegistration? = null
    private var observacaoJob: Job? = null

    fun carregarRastreamento(vendaId: String) {
        observacaoJob?.cancel()
        observacaoJob = viewModelScope.launch {
            _uiState.value = RastreamentoEntregaUiState.Loading
            try {
                val vendaInicial = vendaRepository.buscarVendaPorId(vendaId)
                if (vendaInicial == null) {
                    _uiState.value = RastreamentoEntregaUiState.Erro("Venda não encontrada")
                    return@launch
                }

                val rastreioInicial = rastreioEntregaRepository.buscarPorVendaIdUmaVez(vendaId)
                var pontoOrigemCache: PontoReferencia? = null
                var ultimoPontoOrigemId = ""

                if (rastreioInicial != null && rastreioInicial.pontoOrigemId.isNotBlank()) {
                    pontoOrigemCache = pontoReferenciaRepository.buscarPontoPorId(rastreioInicial.pontoOrigemId)
                    ultimoPontoOrigemId = rastreioInicial.pontoOrigemId
                }

                _uiState.value = RastreamentoEntregaUiState.Carregado(vendaInicial, rastreioInicial, pontoOrigemCache)

                // Sempre escuta localização em tempo real (mesmo sem rastreio ainda)
                escutarLocalizacaoTempoReal(vendaId)

                // Observa em tempo real mudanças no status da venda e no rastreio (ex: posição na fila, início da entrega)
                combine(
                    vendaRepository.observarVendaPorId(vendaId),
                    rastreioEntregaRepository.buscarPorVendaId(vendaId)
                ) { venda, rastreio ->
                    Pair(venda ?: vendaInicial, rastreio ?: rastreioInicial)
                }.collect { (vendaAtual, rastreioAtual) ->
                    val novoPontoId = rastreioAtual?.pontoOrigemId ?: ""
                    if (novoPontoId.isNotBlank() && novoPontoId != ultimoPontoOrigemId) {
                        pontoOrigemCache = pontoReferenciaRepository.buscarPontoPorId(novoPontoId)
                        ultimoPontoOrigemId = novoPontoId
                    }
                    _uiState.value = RastreamentoEntregaUiState.Carregado(
                        venda = vendaAtual,
                        rastreio = rastreioAtual,
                        pontoOrigem = pontoOrigemCache
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = RastreamentoEntregaUiState.Erro(e.message ?: "Erro ao carregar rastreamento")
            }
        }
    }

    private fun escutarLocalizacaoTempoReal(vendaId: String) {
        listenerLocalizacao?.remove()
        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            listenerLocalizacao = firestore.collection("localizacoes_tempo_real")
                .document(vendaId)
                .addSnapshotListener { snapshot, erro ->
                    if (erro != null) {
                        android.util.Log.e("Rastreamento", "Erro ao escutar localização: ${erro.message}")
                        return@addSnapshotListener
                    }

                    android.util.Log.d("Rastreamento", "Snapshot recebido: existe=${snapshot?.exists()}, dados=${snapshot?.data}")

                    val lat = snapshot?.getDouble("latitude")
                    val lng = snapshot?.getDouble("longitude")
                    val timestamp = snapshot?.getLong("timestamp")

                    if (lat != null && lng != null && timestamp != null) {
                        android.util.Log.d("Rastreamento", "Localização recebida: lat=$lat, lng=$lng")
                        _localizacaoMotorista.value = LocalizacaoMotorista(lat, lng, timestamp)
                    } else {
                        android.util.Log.d("Rastreamento", "Localização ainda não disponível")
                        _localizacaoMotorista.value = null
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("Rastreamento", "Erro ao configurar listener: ${e.message}")
        }
    }

    fun pararEscutaLocalizacao() {
        listenerLocalizacao?.remove()
        listenerLocalizacao = null
        observacaoJob?.cancel()
        observacaoJob = null
        _localizacaoMotorista.value = null
    }

    fun resetar() {
        pararEscutaLocalizacao()
        _uiState.value = RastreamentoEntregaUiState.Idle
    }

    override fun onCleared() {
        pararEscutaLocalizacao()
        super.onCleared()
    }
}
