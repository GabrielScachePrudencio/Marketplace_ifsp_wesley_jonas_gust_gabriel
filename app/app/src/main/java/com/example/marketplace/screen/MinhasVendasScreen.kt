package com.example.marketplace.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.VendaListViewModel
import com.example.marketplace.controller.VendaListViewModelFactory
import com.example.marketplace.model.enums.StatusEntrega
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

    val ehComprador = usuario.perfil == "comprador"
    val tituloTela = if (ehComprador) "Minhas Compras" else "Minhas Vendas"

    val vendas by viewModel.vendas.collectAsState()
    val listaExibicao = if (ehComprador) {
        vendas.filter { it.compradorId == usuario.uid }
    } else {
        vendas.filter { it.vendedorId == usuario.uid }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tituloTela) },
                navigationIcon = {
                    TextButton(onClick = onVoltar) { Text("Voltar") }
                }
            )
        }
    ) { padding ->
        if (listaExibicao.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    if (ehComprador) "Você ainda não realizou nenhuma compra."
                    else "Nenhuma venda registrada ainda."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(listaExibicao, key = { it.id }) { venda ->
                    VendaCard(
                        venda = venda,
                        usuario = usuario,
                        onAtualizarStatus = { novoStatus ->
                            viewModel.avancarStatus(
                                vendaId = venda.id,
                                novoStatus = novoStatus,
                                perfil = usuario.perfil
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StatusEntregaBadge(status: StatusEntrega) {
    val (bgColor, textColor, desc) = when (status) {
        StatusEntrega.PENDENTE -> Triple(Color(0xFFFFF3CD), Color(0xFF856404), "Pendente")
        StatusEntrega.PRONTO_PARA_ENTREGA -> Triple(Color(0xFFCCE5FF), Color(0xFF004085), "Pronto para entrega")
        StatusEntrega.A_CAMINHO -> Triple(Color(0xFFFFE8D6), Color(0xFFD9534F), "A caminho")
        StatusEntrega.ENTREGUE -> Triple(Color(0xFFD4EDDA), Color(0xFF155724), "Entregue")
        StatusEntrega.CANCELADA -> Triple(Color(0xFFF8D7DA), Color(0xFF721C24), "Cancelada")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = desc,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun VendaCard(
    venda: Venda,
    usuario: Usuario,
    onAtualizarStatus: (String) -> Unit
) {
    val ehVendedor = venda.vendedorId == usuario.uid
    val status = venda.statusEntrega

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pedido #${venda.id.take(8)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                StatusEntregaBadge(status = status)
            }

            Spacer(Modifier.height(8.dp))
            Text("Produto ID: ${venda.produtoId}", style = MaterialTheme.typography.bodyMedium)
            Text("Quantidade: ${venda.quantidade}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Valor Total: R$ %.2f".format(venda.valorTotal),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )

            // Mensagem explicativa do status para o comprador
            if (usuario.perfil == "comprador") {
                Spacer(Modifier.height(8.dp))
                val mensagemStatus = when (status) {
                    StatusEntrega.PENDENTE -> "Aguardando o vendedor preparar seu pedido."
                    StatusEntrega.PRONTO_PARA_ENTREGA -> "Pedido pronto! Aguardando coleta pelo motorista."
                    StatusEntrega.A_CAMINHO -> "Seu pedido está a caminho com o motorista!"
                    StatusEntrega.ENTREGUE -> "Pedido entregue. Aproveite sua compra!"
                    StatusEntrega.CANCELADA -> "Este pedido foi cancelado."
                }
                Text(
                    text = mensagemStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Ações do Negociador (Vendedor)
            if (ehVendedor && usuario.perfil == "negociador") {
                Spacer(Modifier.height(12.dp))
                when (status) {
                    StatusEntrega.PENDENTE -> {
                        Button(
                            onClick = { onAtualizarStatus(StatusEntrega.PRONTO_PARA_ENTREGA.name) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Marcar como Pronto para Entrega")
                        }
                    }
                    StatusEntrega.PRONTO_PARA_ENTREGA -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Aguardando motorista",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(
                                onClick = { onAtualizarStatus(StatusEntrega.PENDENTE.name) }
                            ) {
                                Text("Voltar para Pendente")
                            }
                        }
                    }
                    StatusEntrega.A_CAMINHO -> {
                        Text(
                            "Em transporte pelo motorista",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    StatusEntrega.ENTREGUE -> {
                        Text(
                            "Entrega finalizada",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    StatusEntrega.CANCELADA -> {}
                }
            }
        }
    }
}
