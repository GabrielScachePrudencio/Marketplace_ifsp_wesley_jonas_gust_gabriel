package com.example.marketplace.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.UsuarioViewModel
import com.example.marketplace.controller.UsuarioViewModelFactory
import com.example.marketplace.controller.VendaListViewModel
import com.example.marketplace.controller.VendaListViewModelFactory
import com.example.marketplace.model.enums.StatusEntrega
import com.example.marketplace.model.Usuario
import com.example.marketplace.model.Veiculo
import com.example.marketplace.model.Venda

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMotoristaScreen(
    usuario: Usuario,
    veiculos: List<Veiculo>,
    onCadastrarVeiculo: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val usuarioViewModel: UsuarioViewModel = viewModel(
        factory = UsuarioViewModelFactory(context)
    )
    val vendaViewModel: VendaListViewModel = viewModel(
        factory = VendaListViewModelFactory(context)
    )

    val negociantes by usuarioViewModel.negociantes.collectAsState()
    val vinculando by usuarioViewModel.vinculando.collectAsState()
    val vendas by vendaViewModel.vendas.collectAsState()

    var negocianteIdAtual by remember { mutableStateOf(usuario.negocianteId) }

    LaunchedEffect(Unit) {
        usuarioViewModel.carregarNegociantes()
    }

    // Filtra entregas relevantes para o motorista:
    // - Pedidos do negociante vinculado em PRONTO_PARA_ENTREGA
    // - Pedidos atribuídos a este motorista em A_CAMINHO ou ENTREGUE
    val entregasMotorista = vendas.filter { venda ->
        val status = venda.statusEntrega
        val ehDoNegociante = negocianteIdAtual != null && venda.vendedorId == negocianteIdAtual
        val ehDesteMotorista = venda.motoristaId == usuario.uid

        (ehDoNegociante && status == StatusEntrega.PRONTO_PARA_ENTREGA) ||
                (ehDesteMotorista && (status == StatusEntrega.A_CAMINHO || status == StatusEntrega.ENTREGUE)) ||
                (ehDoNegociante && (status == StatusEntrega.A_CAMINHO || status == StatusEntrega.ENTREGUE))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Área do Motorista") },
                actions = {
                    TextButton(onClick = onLogout) { Text("Sair") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCadastrarVeiculo) {
                Text("+")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Olá, ${usuario.nome}!", style = MaterialTheme.typography.headlineSmall)
            }

            // ------------------------------------------------
            // VÍNCULO COM NEGOCIANTE
            // ------------------------------------------------
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Negociante parceiro", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))

                        if (negocianteIdAtual == null) {
                            Text("Você ainda não está vinculado a nenhum negociante.")
                            Spacer(Modifier.height(8.dp))

                            if (negociantes.isEmpty()) {
                                Text(
                                    "Nenhum negociante disponível no momento.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                negociantes.forEach { negociante ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(negociante.nome)
                                        Button(
                                            enabled = !vinculando,
                                            onClick = {
                                                usuarioViewModel.vincularNegociante(
                                                    motoristaUid = usuario.uid,
                                                    negocianteId = negociante.uid
                                                ) {
                                                    negocianteIdAtual = negociante.uid
                                                }
                                            }
                                        ) {
                                            Text("Vincular")
                                        }
                                    }
                                }
                            }
                        } else {
                            val nomeNegociante = negociantes
                                .find { it.uid == negocianteIdAtual }
                                ?.nome ?: negocianteIdAtual

                            Text("Vinculado a: $nomeNegociante")
                        }
                    }
                }
            }

            // ------------------------------------------------
            // MEUS VEÍCULOS
            // ------------------------------------------------
            item {
                Text("Meus veículos", style = MaterialTheme.typography.titleMedium)
            }

            if (veiculos.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Nenhum veículo cadastrado ainda (toque no + para cadastrar)")
                        }
                    }
                }
            } else {
                items(veiculos, key = { it.id }) { veiculo ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(veiculo.modelo, style = MaterialTheme.typography.titleSmall)
                            Text("Placa: ${veiculo.placa} • Ano: ${veiculo.ano}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // ------------------------------------------------
            // ENTREGAS / PEDIDOS
            // ------------------------------------------------
            item {
                Spacer(Modifier.height(8.dp))
                Text("Entregas de Pedidos", style = MaterialTheme.typography.titleMedium)
            }

            if (negocianteIdAtual == null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.padding(16.dp)) {
                            Text("Vincule-se a um negociante acima para visualizar pedidos para entrega.")
                        }
                    }
                }
            } else if (entregasMotorista.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("Nenhuma entrega disponível no momento.")
                        }
                    }
                }
            } else {
                items(entregasMotorista, key = { it.id }) { venda ->
                    MotoristaEntregaCard(
                        venda = venda,
                        usuario = usuario,
                        onAtualizarStatus = { novoStatus ->
                            vendaViewModel.avancarStatus(
                                vendaId = venda.id,
                                novoStatus = novoStatus,
                                perfil = "motorista",
                                motoristaId = usuario.uid
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MotoristaEntregaCard(
    venda: Venda,
    usuario: Usuario,
    onAtualizarStatus: (String) -> Unit
) {
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

            Spacer(Modifier.height(6.dp))
            Text("Produto ID: ${venda.produtoId}", style = MaterialTheme.typography.bodyMedium)
            Text("Quantidade: ${venda.quantidade}", style = MaterialTheme.typography.bodySmall)
            Text("Valor Total: R$ %.2f".format(venda.valorTotal), style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(12.dp))

            when (status) {
StatusEntrega.PENDENTE -> {
Text(
"Pedido pendente. Aguardando liberação para entrega.",
style = MaterialTheme.typography.bodySmall
)
}

StatusEntrega.PRONTO_PARA_ENTREGA -> {
Button(
onClick = {
onAtualizarStatus(StatusEntrega.A_CAMINHO.name)
},
modifier = Modifier.fillMaxWidth()
) {
Text("Coletar e Iniciar Entrega (A caminho)")
}
}

StatusEntrega.A_CAMINHO -> {
Column(
verticalArrangement = Arrangement.spacedBy(6.dp)
) {
Button(
onClick = {
onAtualizarStatus(StatusEntrega.ENTREGUE.name)
},
modifier = Modifier.fillMaxWidth()
) {
Text("Confirmar Entrega ao Cliente")
}

OutlinedButton(
onClick = {
onAtualizarStatus(StatusEntrega.PRONTO_PARA_ENTREGA.name)
},
modifier = Modifier.fillMaxWidth()
) {
Text("Cancelar Coleta / Devolver para Loja")
}
}
}

StatusEntrega.ENTREGUE -> {
Row(
modifier = Modifier.fillMaxWidth(),
horizontalArrangement = Arrangement.SpaceBetween,
verticalAlignment = Alignment.CenterVertically
) {
Text(
"Entrega Concluída ✓",
style = MaterialTheme.typography.bodyMedium,
fontWeight = FontWeight.Bold,
color = MaterialTheme.colorScheme.primary
)

OutlinedButton(
onClick = {
onAtualizarStatus(StatusEntrega.A_CAMINHO.name)
}
) {
Text("Desfazer Entrega")
}
}
}

StatusEntrega.CANCELADA -> {
Text(
"Pedido cancelado. Nenhuma ação de entrega disponível.",
style = MaterialTheme.typography.bodySmall,
color = MaterialTheme.colorScheme.error
)
}
}
}
}
}

