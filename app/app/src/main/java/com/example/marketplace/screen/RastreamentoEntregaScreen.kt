package com.example.marketplace.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.RastreamentoEntregaUiState
import com.example.marketplace.controller.RastreamentoEntregaViewModel
import com.example.marketplace.controller.RastreamentoEntregaViewModelFactory
import com.example.marketplace.model.Usuario
import com.example.marketplace.model.enums.StatusEntrega
import com.example.marketplace.util.MapaRastreamento
import com.example.marketplace.util.MarcadorMapa
import com.example.marketplace.util.TipoMarcador

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RastreamentoEntregaScreen(
    usuario: Usuario,
    vendaId: String,
    onVoltar: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: RastreamentoEntregaViewModel = viewModel(
        factory = RastreamentoEntregaViewModelFactory(context)
    )

    LaunchedEffect(vendaId) {
        viewModel.carregarRastreamento(vendaId)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.pararEscutaLocalizacao()
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val localizacaoMotorista by viewModel.localizacaoMotorista.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rastrear Entrega") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (uiState) {
                is RastreamentoEntregaUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is RastreamentoEntregaUiState.Carregado -> {
                    val estado = uiState as RastreamentoEntregaUiState.Carregado
                    val venda = estado.venda
                    val rastreio = estado.rastreio
                    val pontoOrigem = estado.pontoOrigem

                    // ================== STATUS CARD ==================
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when (venda.statusEntrega) {
                                StatusEntrega.PENDENTE -> MaterialTheme.colorScheme.surfaceVariant
                                StatusEntrega.PRONTO_PARA_ENTREGA -> MaterialTheme.colorScheme.primaryContainer
                                StatusEntrega.SAIU_PARA_ENTREGA, StatusEntrega.A_CAMINHO -> MaterialTheme.colorScheme.secondaryContainer
                                StatusEntrega.ENTREGUE -> MaterialTheme.colorScheme.tertiaryContainer
                                StatusEntrega.CANCELADA -> MaterialTheme.colorScheme.errorContainer
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Status: ${venda.statusEntrega.descricao}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Pedido: ${venda.produtoTitulo}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Quantidade: ${venda.quantidade}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // ================== PONTO DE ORIGEM (AZUL) ==================
                    if (pontoOrigem != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Ícone de Loja para Origem
                                Icon(
                                    imageVector = Icons.Default.Store,
                                    contentDescription = "Origem",
                                    tint = MaterialTheme.colorScheme.primary, // Azul/Roxo
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        "PONTO DE ORIGEM",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        pontoOrigem.nome,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${pontoOrigem.rua}, ${pontoOrigem.numero}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "${pontoOrigem.cidade} - ${pontoOrigem.estado}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // ================== DESTINO (VERMELHO) ==================
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Ícone de Casa para Destino
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Destino",
                                tint = MaterialTheme.colorScheme.error, // Vermelho
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    "ENDEREÇO DE ENTREGA",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )

                                // Lógica para obter o texto do endereço
                                val enderecoDestino = when {
                                    // 1. Se o rastreio já tem o texto, usa ele
                                    !rastreio?.enderecoDestinoTexto.isNullOrBlank() -> rastreio!!.enderecoDestinoTexto
                                    // 2. Se o usuário logado for o comprador, usa o endereço dele
                                    usuario.rua.isNotBlank() -> "${usuario.rua}, ${usuario.numero} - ${usuario.cidade}/${usuario.estado}"
                                    else -> "Endereço será configurado quando o pedido estiver pronto para entrega."
                                }

                                Text(
                                    enderecoDestino,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Coordenadas resilientes do destino para o mapa (caso rastreio antigo esteja com 0.0)
                    var latDestinoEfetiva by remember(rastreio?.id) {
                        mutableStateOf(rastreio?.destinoLat?.takeIf { it != 0.0 })
                    }
                    var lngDestinoEfetiva by remember(rastreio?.id) {
                        mutableStateOf(rastreio?.destinoLng?.takeIf { it != 0.0 })
                    }

                    LaunchedEffect(rastreio, enderecoDestino) {
                        if (latDestinoEfetiva == null || lngDestinoEfetiva == null) {
                            if (usuario.latitude != null && usuario.longitude != null && usuario.latitude != 0.0) {
                                latDestinoEfetiva = usuario.latitude
                                lngDestinoEfetiva = usuario.longitude
                            } else if (enderecoDestino.isNotBlank() && !enderecoDestino.startsWith("Endereço será")) {
                                val coord = com.example.marketplace.service.GeocodingService.buscarCoordenadasPorTexto(enderecoDestino)
                                if (coord != null) {
                                    latDestinoEfetiva = coord.latitude
                                    lngDestinoEfetiva = coord.longitude
                                }
                            }
                        }
                    }

                    // ================== MAPA DE VERDADE (osmdroid) ==================
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    ) {
                        // Lógica: O motorista só aparece se o status for A_CAMINHO ou SAIU_PARA_ENTREGA.
                        // Se for PRONTO_PARA_ENTREGA, mostramos apenas Origem e Destino.
                        val mostrarMotorista = venda.statusEntrega == StatusEntrega.A_CAMINHO ||
                                venda.statusEntrega == StatusEntrega.SAIU_PARA_ENTREGA

                        MapaRastreamento(
                            origem = pontoOrigem?.let {
                                MarcadorMapa(
                                    latitude = it.latitude,
                                    longitude = it.longitude,
                                    titulo = it.nome,
                                    tipo = TipoMarcador.ORIGEM
                                )
                            },
                            destino = if (latDestinoEfetiva != null && lngDestinoEfetiva != null) {
                                MarcadorMapa(
                                    latitude = latDestinoEfetiva!!,
                                    longitude = lngDestinoEfetiva!!,
                                    titulo = "Destino",
                                    tipo = TipoMarcador.DESTINO
                                )
                            } else null,
                            // Só passa o motorista se o status for A_CAMINHO ou SAIU_PARA_ENTREGA
                            motorista = if (mostrarMotorista) {
                                localizacaoMotorista?.let {
                                    MarcadorMapa(
                                        latitude = it.latitude,
                                        longitude = it.longitude,
                                        titulo = "Motorista",
                                        tipo = TipoMarcador.MOTORISTA
                                    )
                                }
                            } else {
                                null // Não passa motorista se estiver PRONTO_PARA_ENTREGA
                            }
                        )
                    }

                    // ================== LEGENDA DO MAPA ==================
                    // ================== LEGENDA DINÂMICA ==================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Legenda Origem (sempre aparece)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Store,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Origem", style = MaterialTheme.typography.labelSmall)
                        }

                        // Legenda Destino (sempre aparece)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Destino", style = MaterialTheme.typography.labelSmall)
                        }

                        // Legenda Motorista (SÓ aparece se estiver A_CAMINHO ou SAIU_PARA_ENTREGA)
                        val mostrarMotorista = venda.statusEntrega == StatusEntrega.A_CAMINHO ||
                                venda.statusEntrega == StatusEntrega.SAIU_PARA_ENTREGA

                        if (mostrarMotorista) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.Blue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Motorista", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    // ==================================================================

                    // Texto de última atualização
                    if (localizacaoMotorista == null) {
                        Text(
                            "Aguardando localização do motorista...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Última atualização: ${
                                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                    .format(java.util.Date(localizacaoMotorista!!.timestamp))
                            }",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Posição na fila (se aplicável)
                    if (rastreio != null && rastreio.posicaoNaFila != null && rastreio.posicaoNaFila!! > 0) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Sua entrega é a #${rastreio.posicaoNaFila} da rota",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }

                    // Mensagem baseada no status
                    when (venda.statusEntrega) {
                        StatusEntrega.PENDENTE -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    "Seu pedido está pendente. Aguardando o vendedor preparar.",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        StatusEntrega.PRONTO_PARA_ENTREGA -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Text(
                                    "Seu pedido está pronto e aguardando o motorista.",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        StatusEntrega.SAIU_PARA_ENTREGA, StatusEntrega.A_CAMINHO -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Text(
                                    "O motorista está a caminho! Acompanhe a localização em tempo real.",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        StatusEntrega.ENTREGUE -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "Entrega Concluída! ✓",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        StatusEntrega.CANCELADA -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    "Esta entrega foi cancelada.",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                is RastreamentoEntregaUiState.Erro -> {
                    val erro = (uiState as RastreamentoEntregaUiState.Erro).mensagem
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            erro,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                else -> {}
            }
        }
    }
}