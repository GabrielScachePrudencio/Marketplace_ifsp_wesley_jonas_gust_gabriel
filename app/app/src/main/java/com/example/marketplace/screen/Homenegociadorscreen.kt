package com.example.marketplace.screen

import androidx.compose.runtime.*
import com.example.marketplace.model.Usuario

private enum class TelaNegociador {
    PRODUTOS,
    CRIAR_PRODUTO,
    DETALHE_PRODUTO,
    MINHAS_VENDAS
}

@Composable
fun HomeNegociadorScreen(
    usuario: Usuario,
    onLogout: () -> Unit
) {
    var telaAtual by remember { mutableStateOf(TelaNegociador.PRODUTOS) }
    var produtoSelecionadoId by remember { mutableStateOf<String?>(null) }

    when (telaAtual) {

        TelaNegociador.PRODUTOS -> {
            ProdutoListScreen(
                usuario = usuario,
                onProdutoClick = { id ->
                    produtoSelecionadoId = id
                    telaAtual = TelaNegociador.DETALHE_PRODUTO
                },
                onCriarProduto = {
                    telaAtual = TelaNegociador.CRIAR_PRODUTO
                },
                onMinhasVendas = {
                    telaAtual = TelaNegociador.MINHAS_VENDAS
                },
                onLogout = onLogout
            )
        }

        TelaNegociador.CRIAR_PRODUTO -> {
            CriarProdutoScreen(
                vendedorId = usuario.uid,
                onCriado = {
                    telaAtual = TelaNegociador.PRODUTOS
                },
                onVoltar = {
                    telaAtual = TelaNegociador.PRODUTOS
                }
            )
        }

        TelaNegociador.DETALHE_PRODUTO -> {
            val id = produtoSelecionadoId
            if (id != null) {
                ProdutoDetalheScreen(
                    usuario = usuario,
                    produtoId = id,
                    onVoltar = {
                        telaAtual = TelaNegociador.PRODUTOS
                    }
                )
            }
        }

        TelaNegociador.MINHAS_VENDAS -> {
            MinhasVendasScreen(
                usuario = usuario,
                onVoltar = {
                    telaAtual = TelaNegociador.PRODUTOS
                }
            )
        }
    }
}