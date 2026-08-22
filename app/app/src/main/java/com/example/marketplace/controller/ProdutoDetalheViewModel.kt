package com.example.marketplace.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.marketplace.data.repository.AvaliacaoProdutoRepository
import com.example.marketplace.data.repository.ProdutoRepository
import com.example.marketplace.data.repository.VendaRepository
import com.example.marketplace.model.AvaliacaoProduto
import com.example.marketplace.model.Produto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    val avaliacoes: StateFlow<List<AvaliacaoProduto>> = _produtoIdAtual
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else avaliacaoRepository.buscarAvaliacoesDoProduto(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _compraUiState = MutableStateFlow<CompraUiState>(CompraUiState.Idle)
    val compraUiState: StateFlow<CompraUiState> = _compraUiState

    private val _avaliacaoUiState = MutableStateFlow<AvaliacaoUiState>(AvaliacaoUiState.Idle)
    val avaliacaoUiState: StateFlow<AvaliacaoUiState> = _avaliacaoUiState

    fun carregarProduto(id: String) {
        _produtoIdAtual.value = id
        viewModelScope.launch {
            _produto.value = produtoRepository.buscarProdutoPorId(id)
        }
    }

    fun comprar(compradorId: String, quantidade: Int) {
        val produtoAtual = _produto.value ?: return
        viewModelScope.launch {
            _compraUiState.value = CompraUiState.Loading
            try {
                vendaRepository.criarVenda(
                    compradorId = compradorId,
                    vendedorId = produtoAtual.vendedorId,
                    motoristaId = "",
                    produtoId = produtoAtual.id,
                    quantidade = quantidade
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
