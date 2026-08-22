package com.example.marketplace.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.AvaliacaoUiState
import com.example.marketplace.controller.CompraUiState
import com.example.marketplace.controller.ProdutoDetalheViewModel
import com.example.marketplace.controller.ProdutoDetalheViewModelFactory
import com.example.marketplace.model.AvaliacaoProduto
import com.example.marketplace.model.Usuario

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProdutoDetalheScreen(
    usuario: Usuario,
    produtoId: String,
    onVoltar: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ProdutoDetalheViewModel = viewModel(
        factory = ProdutoDetalheViewModelFactory(context)
    )

    LaunchedEffect(produtoId) {
        viewModel.carregarProduto(produtoId)
    }

    val produtoAtual by viewModel.produto.collectAsState()
    val avaliacoes by viewModel.avaliacoes.collectAsState()
    val compraUiState by viewModel.compraUiState.collectAsState()
    val avaliacaoUiState by viewModel.avaliacaoUiState.collectAsState()

    var quantidadeCompra by remember { mutableStateOf("1") }
    var nota by remember { mutableStateOf(5) }
    var comentario by remember { mutableStateOf("") }

    LaunchedEffect(compraUiState) {
        if (compraUiState is CompraUiState.Sucesso) viewModel.resetarCompra()
    }
    LaunchedEffect(avaliacaoUiState) {
        if (avaliacaoUiState is AvaliacaoUiState.Sucesso) {
            comentario = ""
            viewModel.resetarAvaliacao()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(produtoAtual?.titulo ?: "Produto") })
        }
    ) { padding ->
        val produto = produtoAtual
        if (produto == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(produto.titulo, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(produto.descricao, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Categoria: ${produto.categoria}", style = MaterialTheme.typography.bodySmall)
                    Text("R$ %.2f".format(produto.preco), style = MaterialTheme.typography.titleLarge)
                    Text("Estoque: ${produto.quantidade}", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (usuario.perfil == "comprador" && produto.vendedorId != usuario.uid) {
                item {
                    Column {
                        Text("Comprar", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = quantidadeCompra,
                            onValueChange = { input -> if (input.all { it.isDigit() }) quantidadeCompra = input },
                            label = { Text("Quantidade") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        if (compraUiState is CompraUiState.Erro) {
                            Text(
                                (compraUiState as CompraUiState.Erro).mensagem,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(
                            onClick = { viewModel.comprar(usuario.uid, quantidadeCompra.toIntOrNull() ?: 0) },
                            enabled = compraUiState !is CompraUiState.Loading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (compraUiState is CompraUiState.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Text("Comprar")
                            }
                        }
                    }
                }

                item {
                    Column {
                        Text("Avaliar produto", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (1..5).forEach { valor ->
                                FilterChip(
                                    selected = nota == valor,
                                    onClick = { nota = valor },
                                    label = { Text(valor.toString()) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = comentario,
                            onValueChange = { comentario = it },
                            label = { Text("Comentário") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        if (avaliacaoUiState is AvaliacaoUiState.Erro) {
                            Text(
                                (avaliacaoUiState as AvaliacaoUiState.Erro).mensagem,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(
                            onClick = { viewModel.avaliar(usuario.uid, nota, comentario) },
                            enabled = avaliacaoUiState !is AvaliacaoUiState.Loading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (avaliacaoUiState is AvaliacaoUiState.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Text("Enviar avaliação")
                            }
                        }
                    }
                }
            }

            item {
                Text("Avaliações", style = MaterialTheme.typography.titleMedium)
            }
            if (avaliacoes.isEmpty()) {
                item { Text("Nenhuma avaliação ainda") }
            } else {
                items(avaliacoes, key = { it.id }) { avaliacao ->
                    AvaliacaoCard(avaliacao)
                }
            }

            item {
                TextButton(onClick = onVoltar) { Text("Voltar") }
            }
        }
    }
}

@Composable
private fun AvaliacaoCard(avaliacao: AvaliacaoProduto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Nota: ${avaliacao.nota}/5", style = MaterialTheme.typography.bodyMedium)
            if (avaliacao.comentario.isNotBlank()) {
                Text(avaliacao.comentario, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
