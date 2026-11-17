package com.example.marketplace.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.marketplace.model.Venda
import com.example.marketplace.util.CampoBusca
import com.example.marketplace.util.correspondeBusca
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinhasVendasScreen(
    usuario: Usuario,
    onVoltar: () -> Unit,
    onSelecionarPedido: (String) -> Unit = {},
    onRastrearEntrega: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: VendaListViewModel = viewModel(
        factory = VendaListViewModelFactory(context)
    )
    val usuarioViewModel: UsuarioViewModel = viewModel(
        factory = UsuarioViewModelFactory(context)
    )

    val ehComprador = usuario.perfil == "comprador"
    val tituloTela = if (ehComprador) "Minhas Compras" else "Minhas Vendas"

    val vendas by viewModel.vendas.collectAsState()
    val listaExibicao = if (ehComprador) {
        vendas.filter { it.compradorId == usuario.uid }
    } else {
        vendas.filter { it.vendedorId == usuario.uid }
    }

    var usuariosRelacionados by remember { mutableStateOf<Map<String, Usuario>>(emptyMap()) }

    LaunchedEffect(listaExibicao, ehComprador) {
        val ids = listaExibicao.flatMap { venda ->
            if (ehComprador) {
                listOf(venda.vendedorId)
            } else {
                listOf(venda.compradorId, venda.motoristaId)
            }
        }.filter { it.isNotBlank() }.distinct()

        ids.forEach { uid ->
            if (!usuariosRelacionados.containsKey(uid)) {
                usuarioViewModel.carregarUsuario(uid) { usuarioRelacionado ->
                    if (usuarioRelacionado != null) {
                        usuariosRelacionados = usuariosRelacionados + (uid to usuarioRelacionado)
                    }
                }
            }
        }
    }

    var busca by rememberSaveable { mutableStateOf("") }
    var statusFiltro by rememberSaveable { mutableStateOf<StatusEntrega?>(null) }

    val listaFiltrada = listaExibicao
        .filter { statusFiltro == null || it.statusEntrega == statusFiltro }
        .filter { venda ->
            correspondeBusca(
                busca,
                venda.produtoTitulo,
                venda.id,
                venda.compradorNome,
                usuariosRelacionados[venda.vendedorId]?.nome,
                usuariosRelacionados[venda.compradorId]?.nome
            )
        }
        .sortedByDescending { it.data }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tituloTela) },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampoBusca(
                        valor = busca,
                        onValorChange = { busca = it },
                        placeholder = "Buscar por produto, pedido ou pessoa",
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = statusFiltro == null,
                                onClick = { statusFiltro = null },
                                label = { Text("Todos") }
                            )
                        }
                        items(StatusEntrega.entries.filter { it != StatusEntrega.SAIU_PARA_ENTREGA }) { status ->
                            FilterChip(
                                selected = statusFiltro == status,
                                onClick = { statusFiltro = if (statusFiltro == status) null else status },
                                label = { Text(status.descricao) }
                            )
                        }
                    }
                }
            }

            if (listaFiltrada.isEmpty()) {
                item {
                    val filtrando = busca.isNotBlank() || statusFiltro != null
                    EstadoVazio(
                        icone = if (filtrando) Icons.Default.SearchOff else Icons.AutoMirrored.Filled.ReceiptLong,
                        titulo = when {
                            filtrando -> "Nenhum pedido encontrado"
                            ehComprador -> "Você ainda não realizou nenhuma compra."
                            else -> "Nenhuma venda registrada ainda."
                        },
                        descricao = if (filtrando) "Tente outra palavra ou limpe os filtros."
                        else "Seus pedidos aparecerão aqui."
                    )
                }
            } else {
                items(listaFiltrada, key = { it.id }) { venda ->
                    VendaCard(
                        venda = venda,
                        usuario = usuario,
                        vendedor = usuariosRelacionados[venda.vendedorId],
                        comprador = usuariosRelacionados[venda.compradorId],
                        onSelecionar = { onSelecionarPedido(venda.id) }
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
        StatusEntrega.SAIU_PARA_ENTREGA -> Triple(Color(0xFFFFE8D6), Color(0xFFD9534F), "Saiu para entrega")
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
    vendedor: Usuario?,
    comprador: Usuario?,
    onSelecionar: () -> Unit
) {
    val ehComprador = usuario.perfil == "comprador"
    val status = venda.statusEntrega

    Card(
        onClick = onSelecionar,
        modifier = Modifier.fillMaxWidth()
    ) {
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
            Text(
                venda.produtoTitulo.ifBlank { "Produto #${venda.produtoId.take(6)}" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${venda.quantidade} unidade(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "R$ %.2f".format(venda.valorTotal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(6.dp))
            if (ehComprador) {
                vendedor?.nome?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "Loja: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val nomeCliente = venda.compradorNome.ifBlank { comprador?.nome.orEmpty() }
                if (nomeCliente.isNotBlank()) {
                    Text(
                        "Cliente: $nomeCliente",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onSelecionar,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (ehComprador) "Ver Pedido" else "Gerenciar Pedido")
            }
        }
    }
}
