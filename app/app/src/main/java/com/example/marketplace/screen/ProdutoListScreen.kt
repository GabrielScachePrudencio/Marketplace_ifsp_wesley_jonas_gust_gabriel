package com.example.marketplace.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.ProdutoListViewModel
import com.example.marketplace.controller.ProdutoListViewModelFactory
import com.example.marketplace.model.Produto
import com.example.marketplace.model.Usuario
import com.example.marketplace.util.AvatarUsuario
import com.example.marketplace.util.ImagemProduto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProdutoListScreen(
    usuario: Usuario,
    onProdutoClick: (String) -> Unit,
    onCriarProduto: () -> Unit,
    onMinhasVendas: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ProdutoListViewModel = viewModel(
        factory = ProdutoListViewModelFactory(context)
    )
    val todosProdutos by viewModel.produtos.collectAsState()

    val produtos = if (usuario.perfil == "negociador") {
        todosProdutos.filter { it.vendedorId == usuario.uid }
    } else {
        todosProdutos
    }
    Scaffold(
        topBar = {
            val textoMinhasVendas = if (usuario.perfil == "comprador") "Minhas compras" else "Minhas vendas"
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarUsuario(
                            fotoBase64 = usuario.fotoPerfil,
                            nome = usuario.nome,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("MarketPlace IFSP", style = MaterialTheme.typography.titleMedium)
                            Text(usuario.nome, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onMinhasVendas) { Text(textoMinhasVendas) }
                    TextButton(onClick = onLogout) { Text("Sair") }
                }
            )
        },
        floatingActionButton = {
            if (usuario.perfil == "negociador") {
                FloatingActionButton(onClick = onCriarProduto) {
                    Icon(Icons.Filled.Add, contentDescription = "Criar produto")
                }
            }
        }
    ) { padding ->
        if (produtos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Nenhum produto cadastrado ainda")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(produtos, key = { it.id }) { produto ->
                    ProdutoCard(produto = produto, onClick = { onProdutoClick(produto.id) })
                }
            }
        }
    }
}

@Composable
private fun ProdutoCard(produto: Produto, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ImagemProduto(
                fotoBase64 = produto.imagens.firstOrNull(),
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(produto.titulo, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(produto.categoria, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("R$ %.2f".format(produto.preco), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("Estoque: ${produto.quantidade}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

