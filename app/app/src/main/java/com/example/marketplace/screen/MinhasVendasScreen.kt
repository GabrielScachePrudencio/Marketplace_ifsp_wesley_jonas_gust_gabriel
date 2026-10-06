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
    onRastrearEntrega: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
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
    var vendaSelecionada by remember { mutableStateOf<Venda?>(null) }

    // NOVO: estado do diálogo de escolha de ponto de origem
    var vendaParaMarcarPronta by remember { mutableStateOf<Venda?>(null) }

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
                        onVerDetalhes = { vendaSelecionada = venda },
                        onRastrearEntrega = onRastrearEntrega,
                        onAtualizarStatus = { novoStatus ->
                            viewModel.avancarStatus(
                                vendaId = venda.id,
                                novoStatus = novoStatus,
                                perfil = usuario.perfil
                            )
                        },
                        onMarcarProntoParaEntrega = {
                            vendaParaMarcarPronta = venda
                        }
                    )
                }
            }
        }
    }

    vendaSelecionada?.let { venda ->
        DetalhesMinhaVendaDialog(
            venda = venda,
            ehComprador = ehComprador,
            vendedor = usuariosRelacionados[venda.vendedorId],
            comprador = usuariosRelacionados[venda.compradorId],
            motorista = usuariosRelacionados[venda.motoristaId],
            onDismiss = { vendaSelecionada = null }
        )
    }

    // NOVO: diálogo de escolha do ponto de origem
    vendaParaMarcarPronta?.let { venda ->
        val comprador = usuariosRelacionados[venda.compradorId]

        EscolherPontoOrigemDialog(
            negocianteId = usuario.uid,
            venda = venda,
            onConfirmar = { ponto ->
                vendaParaMarcarPronta = null
                escopo.launch {
                    val comp = comprador ?: run {
                        val db = com.example.marketplace.data.local.AppDatabase.getDatabase(context)
                        db.usuarioDao().buscarPorId(venda.compradorId)
                    }

                    val coordenadas = if (comp?.latitude != null && comp.longitude != null && comp.latitude != 0.0) {
                        com.example.marketplace.service.Coordenadas(comp.latitude, comp.longitude)
                    } else {
                        com.example.marketplace.service.GeocodingService.buscarCoordenadas(
                            rua = comp?.rua ?: "",
                            numero = comp?.numero ?: "",
                            cidade = comp?.cidade ?: "",
                            estado = comp?.estado ?: "",
                            cep = comp?.cep ?: ""
                        )
                    }

                    viewModel.marcarProntoParaEntrega(
                        venda = venda,
                        pontoOrigemId = ponto.id,
                        enderecoDestinoTexto = comp?.let { "${it.rua}, ${it.numero} - ${it.cidade}/${it.estado}" } ?: "",
                        destinoLat = coordenadas?.latitude ?: 0.0,
                        destinoLng = coordenadas?.longitude ?: 0.0
                    )
                }
            },
            onDismiss = { vendaParaMarcarPronta = null }
        )
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
    onVerDetalhes: () -> Unit,
    onAtualizarStatus: (String) -> Unit,
    onRastrearEntrega: (String) -> Unit,
    onMarcarProntoParaEntrega: () -> Unit
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

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onVerDetalhes,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ver mais informações")
            }

            if (usuario.perfil == "comprador" && (
                        status == StatusEntrega.PRONTO_PARA_ENTREGA ||
                                status == StatusEntrega.SAIU_PARA_ENTREGA ||
                                status == StatusEntrega.A_CAMINHO
                        )
            ) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onRastrearEntrega(venda.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Rastrear Entrega")
                }
            }

            // Mensagem explicativa do status para o comprador
            if (usuario.perfil == "comprador") {
                Spacer(Modifier.height(8.dp))
                val mensagemStatus = when (status) {
                    StatusEntrega.PENDENTE -> "Aguardando o vendedor preparar seu pedido."
                    StatusEntrega.PRONTO_PARA_ENTREGA -> "Pedido pronto! Toque em 'Rastrear Entrega' para ver o ponto de coleta no mapa."
                    StatusEntrega.SAIU_PARA_ENTREGA -> "O motorista saiu para entrega. Acompanhe no mapa!"
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
                            onClick = onMarcarProntoParaEntrega,
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
                    StatusEntrega.SAIU_PARA_ENTREGA -> {
                        Text(
                            "Motorista saiu para entrega",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
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

@Composable
private fun DetalhesMinhaVendaDialog(
    venda: Venda,
    ehComprador: Boolean,
    vendedor: Usuario?,
    comprador: Usuario?,
    motorista: Usuario?,
    onDismiss: () -> Unit
) {
    val tituloProduto = venda.produtoTitulo.ifBlank { "Produto #${venda.produtoId.take(6)}" }
    val dataFormatada = try {
        venda.dataCriacao.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    } catch (_: Exception) {
        ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Detalhes do pedido #${venda.id.take(8)}")
                StatusEntregaBadge(status = venda.statusEntrega)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Produto: $tituloProduto", fontWeight = FontWeight.SemiBold)
                Text("Quantidade: ${venda.quantidade}")
                if (venda.valorUnitario > 0.0) {
                    Text("Valor unitário: R$ %.2f".format(venda.valorUnitario))
                }
                Text("Valor total: R$ %.2f".format(venda.valorTotal), fontWeight = FontWeight.Bold)
                if (dataFormatada.isNotBlank()) {
                    Text("Data da compra: $dataFormatada", style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider()
                if (ehComprador) {
                    Text("Onde você comprou", fontWeight = FontWeight.Bold)
                    Text(vendedor?.nome ?: "Loja não identificada")
                    vendedor?.let { loja ->
                        if (loja.rua.isNotBlank()) Text("Endereço: ${loja.rua}, Nº ${loja.numero}")
                        if (loja.cidade.isNotBlank()) Text("${loja.cidade} - ${loja.estado}")
                        if (loja.email.isNotBlank()) Text("Contato: ${loja.email}")
                    }
                } else {
                    Text("Dados do comprador", fontWeight = FontWeight.Bold)
                    Text(comprador?.nome ?: venda.compradorNome.ifBlank { "Cliente não identificado" })
                    comprador?.let { cliente ->
                        if (cliente.email.isNotBlank()) Text("Email: ${cliente.email}")
                        if (cliente.rua.isNotBlank()) Text("Entrega: ${cliente.rua}, Nº ${cliente.numero}")
                        if (cliente.cidade.isNotBlank()) Text("${cliente.cidade} - ${cliente.estado}")
                    }
                    if (motorista != null) {
                        Text("Motorista: ${motorista.nome}")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Fechar") }
        }
    )
}
