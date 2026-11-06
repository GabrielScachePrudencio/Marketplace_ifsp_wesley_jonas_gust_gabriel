package com.example.marketplace.controller

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.marketplace.repository.PontoReferenciaRepository
import com.example.marketplace.repository.RastreioEntregaRepository
import com.example.marketplace.repository.UsuarioRepository
import com.example.marketplace.repository.VendaRepository
import com.example.marketplace.model.PontoReferencia
import com.example.marketplace.model.RastreioEntrega
import com.example.marketplace.model.Usuario
import com.example.marketplace.model.Venda
import com.example.marketplace.model.enums.StatusEntrega
import com.example.marketplace.model.ComprovanteEntrega
import com.example.marketplace.repository.ComprovanteEntregaRepository
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
        val pontoOrigem: PontoReferencia?,
        val comprador: Usuario? = null
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
    private val pontoReferenciaRepository: PontoReferenciaRepository,
    private val usuarioRepository: UsuarioRepository? = null,
    private val comprovanteEntregaRepository: ComprovanteEntregaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RastreamentoEntregaUiState>(RastreamentoEntregaUiState.Idle)
    val uiState: StateFlow<RastreamentoEntregaUiState> = _uiState.asStateFlow()

    private val _localizacaoMotorista = MutableStateFlow<LocalizacaoMotorista?>(null)
    val localizacaoMotorista: StateFlow<LocalizacaoMotorista?> = _localizacaoMotorista.asStateFlow()

    private val _comprovante = MutableStateFlow<ComprovanteEntrega?>(null)
    val comprovante: StateFlow<ComprovanteEntrega?> = _comprovante.asStateFlow()

    private val _entregaEmAndamento = MutableStateFlow(false)
    val entregaEmAndamento: StateFlow<Boolean> = _entregaEmAndamento.asStateFlow()

    private val _mensagemEntrega = MutableStateFlow<String?>(null)
    val mensagemEntrega: StateFlow<String?> = _mensagemEntrega.asStateFlow()

    private var listenerLocalizacao: ListenerRegistration? = null
    private var observacaoJob: Job? = null
    private var comprovanteJob: Job? = null

    fun carregarRastreamento(vendaId: String) {
        observacaoJob?.cancel()
        comprovanteJob?.cancel()
        _comprovante.value = null
        comprovanteJob = viewModelScope.launch {
            try {
                comprovanteEntregaRepository.observarPorVenda(vendaId).collect {
                    _comprovante.value = it
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _mensagemEntrega.value = e.message ?: "Não foi possível carregar o comprovante."
            }
        }
        observacaoJob = viewModelScope.launch {
            _uiState.value = RastreamentoEntregaUiState.Loading
            try {
                val vendaInicial = vendaRepository.buscarVendaPorId(vendaId)
                if (vendaInicial == null) {
                    _uiState.value = RastreamentoEntregaUiState.Erro("Venda não encontrada")
                    return@launch
                }

                val comprador = if (vendaInicial.compradorId.isNotBlank()) {
                    usuarioRepository?.buscarUsuario(vendaInicial.compradorId)
                } else null

                val rastreioInicial = rastreioEntregaRepository.buscarPorVendaIdUmaVez(vendaId)
                var pontoOrigemCache: PontoReferencia? = null
                var ultimoPontoOrigemId = ""

                if (rastreioInicial != null && rastreioInicial.pontoOrigemId.isNotBlank()) {
                    pontoOrigemCache = pontoReferenciaRepository.buscarPontoPorId(rastreioInicial.pontoOrigemId)
                    ultimoPontoOrigemId = rastreioInicial.pontoOrigemId
                }

                _uiState.value = RastreamentoEntregaUiState.Carregado(
                    venda = vendaInicial,
                    rastreio = rastreioInicial,
                    pontoOrigem = pontoOrigemCache,
                    comprador = comprador
                )

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
                        pontoOrigem = pontoOrigemCache,
                        comprador = comprador
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = RastreamentoEntregaUiState.Erro(e.message ?: "Erro ao carregar rastreamento")
            }
        }
    }

    fun confirmarEntrega(
        vendaId: String,
        motoristaId: String,
        fotoUri: Uri,
        latitude: Double,
        longitude: Double
    ) {
        if (_entregaEmAndamento.value) return
        viewModelScope.launch {
            _entregaEmAndamento.value = true
            _mensagemEntrega.value = null
            try {
                val estado = _uiState.value as? RastreamentoEntregaUiState.Carregado
                    ?: error("Aguarde o carregamento do pedido antes de concluir a entrega.")
                val rastreio = estado.rastreio
                    ?: error("Não foi encontrado o rastreamento desta entrega.")
                check(estado.venda.motoristaId == motoristaId) {
                    "Somente o motorista responsável pode concluir esta entrega."
                }
                check(estado.venda.statusEntrega in setOf(
                    StatusEntrega.A_CAMINHO,
                    StatusEntrega.SAIU_PARA_ENTREGA
                )) {
                    "A entrega precisa estar em andamento para ser concluída."
                }

                comprovanteEntregaRepository.salvar(
                    vendaId = vendaId,
                    motoristaId = motoristaId,
                    fotoUri = fotoUri,
                    latitude = latitude,
                    longitude = longitude
                )
                rastreioEntregaRepository.finalizarEntrega(rastreio.id, latitude, longitude)
                vendaRepository.atualizarStatusVenda(
                    id = vendaId,
                    novoStatus = StatusEntrega.ENTREGUE.name,
                    perfil = "motorista",
                    motoristaId = motoristaId
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _mensagemEntrega.value = e.message ?: "Não foi possível concluir a entrega."
            } finally {
                _entregaEmAndamento.value = false
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
        comprovanteJob?.cancel()
        comprovanteJob = null
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
