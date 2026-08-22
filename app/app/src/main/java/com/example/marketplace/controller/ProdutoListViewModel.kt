package com.example.marketplace.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.marketplace.data.repository.ProdutoRepository
import com.example.marketplace.model.Produto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class CriarProdutoUiState {
    object Idle : CriarProdutoUiState()
    object Loading : CriarProdutoUiState()
    object Sucesso : CriarProdutoUiState()
    data class Erro(val mensagem: String) : CriarProdutoUiState()
}

class ProdutoListViewModel(
    private val repository: ProdutoRepository
) : ViewModel() {

    val produtos: StateFlow<List<Produto>> = repository.buscarProdutos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _criarUiState = MutableStateFlow<CriarProdutoUiState>(CriarProdutoUiState.Idle)
    val criarUiState: StateFlow<CriarProdutoUiState> = _criarUiState

    fun criarProduto(
        vendedorId: String,
        titulo: String,
        descricao: String,
        categoria: String,
        preco: Double,
        quantidade: Int
    ) {
        viewModelScope.launch {
            _criarUiState.value = CriarProdutoUiState.Loading
            try {
                repository.criarProduto(vendedorId, titulo, descricao, categoria, preco, quantidade, imagens = "")
                _criarUiState.value = CriarProdutoUiState.Sucesso
            } catch (e: Exception) {
                _criarUiState.value = CriarProdutoUiState.Erro(e.message ?: "Erro ao criar produto")
            }
        }
    }

    fun resetarCriacao() {
        _criarUiState.value = CriarProdutoUiState.Idle
    }
}
