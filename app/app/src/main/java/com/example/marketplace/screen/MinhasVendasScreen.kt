package com.example.marketplace.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.VendaListViewModel
import com.example.marketplace.controller.VendaListViewModelFactory
import com.example.marketplace.model.Usuario
import com.example.marketplace.model.Venda

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinhasVendasScreen(
    usuario: Usuario,
    onVoltar: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: VendaListViewModel = viewModel(
        factory = VendaListViewModelFactory(context)
    )

    val vendas by viewModel.vendas.collectAsState()
    val minhasVendas = vendas.filter { it.compradorId == usuario.uid || it.vendedorId == usuario.uid }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Minhas vendas") },
                navigationIcon = {
                    TextButton(onClick = onVoltar) { Text("Voltar") }
                }
            )
        }
    ) { padding ->
        if (minhasVendas.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nenhuma venda ainda")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(minhasVendas, key = { it.id }) { venda ->
                    VendaCard(
                        venda = venda,
                        souVendedor = venda.vendedorId == usuario.uid,
                        onAvancarStatus = { novoStatus -> viewModel.avancarStatus(venda.id, novoStatus) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VendaCard(venda: Venda, souVendedor: Boolean, onAvancarStatus: (String) -> Unit) {
    val proximoStatus = when (venda.status) {
        "PENDENTE" -> "EM_TRANSPORTE"
        "EM_TRANSPORTE" -> "ENTREGUE"
        else -> null
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Produto: ${venda.produtoId}", style = MaterialTheme.typography.bodyMedium)
            Text("Quantidade: ${venda.quantidade}", style = MaterialTheme.typography.bodySmall)
            Text("Total: R$ %.2f".format(venda.valorTotal), style = MaterialTheme.typography.bodyMedium)
            Text("Status: ${venda.status}", style = MaterialTheme.typography.bodySmall)

            if (souVendedor && proximoStatus != null) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onAvancarStatus(proximoStatus) }) {
                    Text("Avançar para $proximoStatus")
                }
            }
        }
    }
}
