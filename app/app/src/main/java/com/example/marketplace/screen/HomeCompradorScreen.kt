package com.example.marketplace.screen

import androidx.compose.runtime.*
import com.example.marketplace.model.Usuario

private enum class TelaComprador {
    PRODUTOS,
    DETALHE_PRODUTO,
    MINHAS_VENDAS,
    RASTREAMENTO_ENTREGA
}

@Composable
fun HomeCompradorScreen(
    usuario: Usuario,
    onLogout: () -> Unit
) {
    var telaAtual by remember { mutableStateOf(TelaComprador.PRODUTOS) }
    var produtoSelecionadoId by remember { mutableStateOf<String?>(null) }
    var vendaSelecionadaId by remember { mutableStateOf<String?>(null) }

    when (telaAtual) {

        TelaComprador.PRODUTOS -> {
            ProdutoListScreen(
                usuario = usuario,
                onProdutoClick = { id ->
                    produtoSelecionadoId = id
                    telaAtual = TelaComprador.DETALHE_PRODUTO
                },
                onCriarProduto = {
                    // comprador não cria produto — o FAB nem aparece pra ele,
                    // então isso aqui nunca deve ser chamado
                },
                onMinhasVendas = {
                    telaAtual = TelaComprador.MINHAS_VENDAS
                },
                onLogout = onLogout
            )
        }

        TelaComprador.DETALHE_PRODUTO -> {
            val id = produtoSelecionadoId
            if (id != null) {
                ProdutoDetalheScreen(
                    usuario = usuario,
                    produtoId = id,
                    onVoltar = {
                        telaAtual = TelaComprador.PRODUTOS
                    }
                )
            }
        }

        TelaComprador.MINHAS_VENDAS -> {
            MinhasVendasScreen(
                usuario = usuario,
                onVoltar = {
                    telaAtual = TelaComprador.PRODUTOS
                },
                onRastrearEntrega = { vendaId ->
                    vendaSelecionadaId = vendaId
                    telaAtual = TelaComprador.RASTREAMENTO_ENTREGA
                }
            )
        }

        TelaComprador.RASTREAMENTO_ENTREGA -> {
            val vendaId = vendaSelecionadaId
            if (vendaId != null) {
                RastreamentoEntregaScreen(
                    usuario = usuario,
                    vendaId = vendaId,
                    onVoltar = {
                        telaAtual = TelaComprador.MINHAS_VENDAS
                    }
                )
            }
        }
    }
}