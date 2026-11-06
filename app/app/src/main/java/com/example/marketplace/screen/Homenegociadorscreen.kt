package com.example.marketplace.screen

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import com.example.marketplace.model.PontoReferencia
import com.example.marketplace.model.Usuario

private enum class TelaNegociador {
    PRODUTOS,
    CRIAR_PRODUTO,
    DETALHE_PRODUTO,
    MINHAS_VENDAS,
    RASTREAMENTO_ENTREGA,
    PONTOS_REFERENCIA,
    CRIAR_EDITAR_PONTO,
    EDITAR_PERFIL
}

@Composable
fun HomeNegociadorScreen(
    usuario: Usuario,
    vendaIdParaAbrir: String? = null,
    onNotificacaoConsumida: () -> Unit = {},
    onUsuarioAtualizado: (Usuario) -> Unit = {},
    onLogout: () -> Unit
) {
    var telaAtual by remember { mutableStateOf(TelaNegociador.PRODUTOS) }
    var produtoSelecionadoId by remember { mutableStateOf<String?>(null) }
    var pontoSelecionado by remember { mutableStateOf<PontoReferencia?>(null) }
    var vendaRastreioId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vendaIdParaAbrir) {
        vendaIdParaAbrir?.let { vendaId ->
            vendaRastreioId = vendaId
            telaAtual = TelaNegociador.RASTREAMENTO_ENTREGA
            onNotificacaoConsumida()
        }
    }

    BackHandler(enabled = telaAtual != TelaNegociador.PRODUTOS) {
        telaAtual = when (telaAtual) {
            TelaNegociador.RASTREAMENTO_ENTREGA -> TelaNegociador.MINHAS_VENDAS
            TelaNegociador.CRIAR_EDITAR_PONTO -> TelaNegociador.PONTOS_REFERENCIA
            TelaNegociador.CRIAR_PRODUTO,
            TelaNegociador.DETALHE_PRODUTO,
            TelaNegociador.MINHAS_VENDAS,
            TelaNegociador.PONTOS_REFERENCIA,
            TelaNegociador.EDITAR_PERFIL -> TelaNegociador.PRODUTOS
            TelaNegociador.PRODUTOS -> TelaNegociador.PRODUTOS
        }
    }

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
                onEditarPerfil = {
                    telaAtual = TelaNegociador.EDITAR_PERFIL
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
                },
                onRastrearEntrega = { vendaId ->
                    vendaRastreioId = vendaId
                    telaAtual = TelaNegociador.RASTREAMENTO_ENTREGA
                }
            )
        }

        TelaNegociador.RASTREAMENTO_ENTREGA -> {
            val vendaId = vendaRastreioId
            if (vendaId != null) {
                RastreamentoEntregaScreen(
                    usuario = usuario,
                    vendaId = vendaId,
                    onVoltar = { telaAtual = TelaNegociador.MINHAS_VENDAS }
                )
            }
        }

        TelaNegociador.EDITAR_PERFIL -> {
            EditarPerfilScreen(
                usuario = usuario,
                onVoltar = {
                    telaAtual = TelaNegociador.PRODUTOS
                },
                onPerfilAtualizado = { atualizado ->
                    onUsuarioAtualizado(atualizado)
                    telaAtual = TelaNegociador.PRODUTOS
                }
            )
        }
    }
}