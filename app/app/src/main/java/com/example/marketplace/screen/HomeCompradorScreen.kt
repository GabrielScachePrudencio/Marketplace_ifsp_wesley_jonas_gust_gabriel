package com.example.marketplace.screen

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import com.example.marketplace.model.Usuario

private enum class TelaComprador {
    PRODUTOS,
    DETALHE_PRODUTO,
    MINHAS_VENDAS,
    RASTREAMENTO_ENTREGA,
    EDITAR_PERFIL
}

@Composable
fun HomeCompradorScreen(
    usuario: Usuario,
    vendaIdParaAbrir: String? = null,
    onNotificacaoConsumida: () -> Unit = {},
    onUsuarioAtualizado: (Usuario) -> Unit = {},
    onLogout: () -> Unit
) {
    var telaAtual by remember { mutableStateOf(TelaComprador.PRODUTOS) }
    var produtoSelecionadoId by remember { mutableStateOf<String?>(null) }
    var vendaSelecionadaId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vendaIdParaAbrir) {
        vendaIdParaAbrir?.let { vendaId ->
            vendaSelecionadaId = vendaId
            telaAtual = TelaComprador.RASTREAMENTO_ENTREGA
            onNotificacaoConsumida()
        }
    }

    BackHandler(enabled = telaAtual != TelaComprador.PRODUTOS) {
        telaAtual = when (telaAtual) {
            TelaComprador.RASTREAMENTO_ENTREGA -> TelaComprador.MINHAS_VENDAS
            TelaComprador.DETALHE_PRODUTO,
            TelaComprador.MINHAS_VENDAS,
            TelaComprador.EDITAR_PERFIL -> TelaComprador.PRODUTOS
            TelaComprador.PRODUTOS -> TelaComprador.PRODUTOS
        }
    }

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
                onEditarPerfil = {
                    telaAtual = TelaComprador.EDITAR_PERFIL
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

        TelaComprador.EDITAR_PERFIL -> {
            EditarPerfilScreen(
                usuario = usuario,
                onVoltar = {
                    telaAtual = TelaComprador.PRODUTOS
                },
                onPerfilAtualizado = { atualizado ->
                    onUsuarioAtualizado(atualizado)
                    telaAtual = TelaComprador.PRODUTOS
                }
            )
        }
    }
}