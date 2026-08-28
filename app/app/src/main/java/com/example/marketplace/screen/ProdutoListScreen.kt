package com.example.marketplace.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.ProdutoListViewModel
import com.example.marketplace.controller.ProdutoListViewModelFactory
import com.example.marketplace.model.Produto
import com.example.marketplace.model.Usuario

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
                title = { Text("MarketPlace IFSP") },
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
        Column(modifier = Modifier.padding(16.dp)) {
            Text(produto.titulo, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(produto.categoria, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text("R$ %.2f".format(produto.preco), style = MaterialTheme.typography.bodyLarge)
            Text("Estoque: ${produto.quantidade}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
