package com.example.marketplace.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.marketplace.domain.AvaliacaoProdutoRegras
import com.example.marketplace.repository.AvaliacaoProdutoRepository
import com.example.marketplace.repository.ProdutoRepository
import com.example.marketplace.repository.VendaRepository
import com.example.marketplace.model.AvaliacaoProduto
import com.example.marketplace.model.Produto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class CompraUiState {
    object Idle : CompraUiState()
    object Loading : CompraUiState()
    object Sucesso : CompraUiState()
    data class Erro(val mensagem: String) : CompraUiState()
}

sealed class VideoUiState {
    object SemVideo : VideoUiState()
    object Carregando : VideoUiState()
    data class Pronto(val bytes: ByteArray) : VideoUiState()
    object Erro : VideoUiState()
}

sealed class AvaliacaoUiState {
    object Idle : AvaliacaoUiState()
    object Loading : AvaliacaoUiState()
    object Sucesso : AvaliacaoUiState()
    data class Erro(val mensagem: String) : AvaliacaoUiState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProdutoDetalheViewModel(
    private val produtoRepository: ProdutoRepository,
    private val vendaRepository: VendaRepository,
    private val avaliacaoRepository: AvaliacaoProdutoRepository
) : ViewModel() {

    private val _produto = MutableStateFlow<Produto?>(null)
    val produto: StateFlow<Produto?> = _produto

    private val _produtoIdAtual = MutableStateFlow<String?>(null)
    private val _usuarioIdAtual = MutableStateFlow<String?>(null)

    /** true quando o usuário tem algum pedido deste produto entregue */
    val podeAvaliar: StateFlow<Boolean> = combine(_produtoIdAtual, _usuarioIdAtual) { produtoId, usuarioId ->
        produtoId to usuarioId
    }
        .flatMapLatest { (produtoId, usuarioId) ->
            if (produtoId == null || usuarioId == null) flowOf(false)
            else vendaRepository.observarVendasDoCompradorPorProduto(usuarioId, produtoId)
                .map { AvaliacaoProdutoRegras.podeAvaliar(it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _videoUiState = MutableStateFlow<VideoUiState>(VideoUiState.SemVideo)
    val videoUiState: StateFlow<VideoUiState> = _videoUiState

    val avaliacoes: StateFlow<List<AvaliacaoProduto>> = _produtoIdAtual
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else avaliacaoRepository.buscarAvaliacoesDoProduto(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _compraUiState = MutableStateFlow<CompraUiState>(CompraUiState.Idle)
    val compraUiState: StateFlow<CompraUiState> = _compraUiState

    private val _avaliacaoUiState = MutableStateFlow<AvaliacaoUiState>(AvaliacaoUiState.Idle)
    val avaliacaoUiState: StateFlow<AvaliacaoUiState> = _avaliacaoUiState

    fun carregarProduto(id: String, usuarioId: String? = null) {
        _produtoIdAtual.value = id
        _usuarioIdAtual.value = usuarioId
        viewModelScope.launch {
            val produto = produtoRepository.buscarProdutoPorId(id)
            _produto.value = produto
            if (produto != null && produto.videoPartes > 0) {
                _videoUiState.value = VideoUiState.Carregando
                val bytes = produtoRepository.carregarVideo(produto)
                _videoUiState.value = if (bytes != null) VideoUiState.Pronto(bytes) else VideoUiState.Erro
            } else {
                _videoUiState.value = VideoUiState.SemVideo
            }
        }
    }

    fun comprar(compradorId: String, quantidade: Int, compradorNome: String = "") {
        val produtoAtual = _produto.value ?: return
        viewModelScope.launch {
            _compraUiState.value = CompraUiState.Loading
            try {
                vendaRepository.criarVenda(
                    compradorId = compradorId,
                    vendedorId = produtoAtual.vendedorId,
                    motoristaId = "",
                    produtoId = produtoAtual.id,
                    quantidade = quantidade,
                    compradorNome = compradorNome,
                    produtoTitulo = produtoAtual.titulo
                )
                _produto.value = produtoRepository.buscarProdutoPorId(produtoAtual.id)
                _compraUiState.value = CompraUiState.Sucesso
            } catch (e: Exception) {
                _compraUiState.value = CompraUiState.Erro(e.message ?: "Erro ao comprar")
            }
        }
    }

    fun avaliar(usuarioId: String, nota: Int, comentario: String) {
        val produtoAtual = _produto.value ?: return
        viewModelScope.launch {
            _avaliacaoUiState.value = AvaliacaoUiState.Loading
            try {
                AvaliacaoProdutoRegras.validarPedidos(
                    vendaRepository.buscarVendasDoCompradorPorProduto(usuarioId, produtoAtual.id)
                )
                avaliacaoRepository.avaliarProduto(produtoAtual.id, usuarioId, nota, comentario)
                _avaliacaoUiState.value = AvaliacaoUiState.Sucesso
            } catch (e: Exception) {
                _avaliacaoUiState.value = AvaliacaoUiState.Erro(e.message ?: "Erro ao avaliar")
            }
        }
    }

    fun resetarCompra() {
        _compraUiState.value = CompraUiState.Idle
    }

    fun resetarAvaliacao() {
        _avaliacaoUiState.value = AvaliacaoUiState.Idle
    }
}
