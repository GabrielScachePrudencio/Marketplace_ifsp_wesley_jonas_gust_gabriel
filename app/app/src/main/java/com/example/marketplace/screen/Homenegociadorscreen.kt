package com.example.marketplace.screen

import androidx.compose.runtime.*
import com.example.marketplace.model.PontoReferencia
import com.example.marketplace.model.Usuario

private enum class TelaNegociador {
    PRODUTOS,
    CRIAR_PRODUTO,
    DETALHE_PRODUTO,
    MINHAS_VENDAS,
    PONTOS_REFERENCIA,
    CRIAR_EDITAR_PONTO
}

@Composable
fun HomeNegociadorScreen(
    usuario: Usuario,
    onLogout: () -> Unit
) {
    var telaAtual by remember { mutableStateOf(TelaNegociador.PRODUTOS) }
    var produtoSelecionadoId by remember { mutableStateOf<String?>(null) }
    var pontoSelecionado by remember { mutableStateOf<PontoReferencia?>(null) }

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
                onPontosReferencia = {
                    telaAtual = TelaNegociador.PONTOS_REFERENCIA
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

        TelaNegociador.PONTOS_REFERENCIA -> {
            PontosReferenciaScreen(
                usuario = usuario,
                onVoltar = {
                    telaAtual = TelaNegociador.PRODUTOS
                },
                onCriarPonto = {
                    pontoSelecionado = null
                    telaAtual = TelaNegociador.CRIAR_EDITAR_PONTO
                },
                onEditarPonto = { ponto ->
                    pontoSelecionado = ponto
                    telaAtual = TelaNegociador.CRIAR_EDITAR_PONTO
                }
            )
        }

        TelaNegociador.CRIAR_EDITAR_PONTO -> {
            CriarEditarPontoReferenciaScreen(
                usuario = usuario,
                pontoExistente = pontoSelecionado,
                onVoltar = {
                    telaAtual = TelaNegociador.PONTOS_REFERENCIA
                },
                onSucesso = {
                    telaAtual = TelaNegociador.PONTOS_REFERENCIA
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