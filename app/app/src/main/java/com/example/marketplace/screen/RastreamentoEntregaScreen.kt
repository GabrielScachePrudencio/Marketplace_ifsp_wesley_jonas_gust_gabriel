package com.example.marketplace.screen

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.RastreamentoEntregaUiState
import com.example.marketplace.controller.RastreamentoEntregaViewModel
import com.example.marketplace.controller.RastreamentoEntregaViewModelFactory
import com.example.marketplace.model.LocalizacaoMotoristaTracker
import com.example.marketplace.model.Usuario
import com.example.marketplace.model.enums.StatusEntrega
import com.example.marketplace.service.GeocodingService
import com.example.marketplace.service.PontoRota
import com.example.marketplace.service.Rota
import com.example.marketplace.service.RoteamentoService
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
    val ehMotorista = usuario.perfil == "motorista"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ehMotorista) "Rota da Entrega" else "Rastrear Entrega") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (uiState) {
                is RastreamentoEntregaUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
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
                    val comprador = estado.comprador
                    val usuarioDestino = if (ehMotorista) comprador else (comprador ?: usuario)

                    // O motorista só aparece se o status for A_CAMINHO ou SAIU_PARA_ENTREGA.
                    val mostrarMotorista = venda.statusEntrega == StatusEntrega.A_CAMINHO ||
                            venda.statusEntrega == StatusEntrega.SAIU_PARA_ENTREGA

                    // Se quem abriu o mapa é o motorista desta entrega, mantém o GPS enviando em tempo real
                    if (ehMotorista) {
                        val tracker = remember { LocalizacaoMotoristaTracker(context) }
                        val temPermissaoGps = remember {
                            ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        }
                        LaunchedEffect(venda.id, mostrarMotorista, temPermissaoGps) {
                            if (mostrarMotorista && venda.motoristaId == usuario.uid && temPermissaoGps) {
                                tracker.iniciar(venda.id)
                            } else {
                                tracker.pararSeAtivo(limparNoServidor = false)
                            }
                        }
                        DisposableEffect(Unit) {
                            onDispose { tracker.pararSeAtivo(limparNoServidor = false) }
                        }
                    }

                    var destinoLatGeocodificado by remember(venda.id) { mutableStateOf<Double?>(null) }
                    var destinoLngGeocodificado by remember(venda.id) { mutableStateOf<Double?>(null) }

                    val destinoLatBase = rastreio?.destinoLat?.takeIf { it != 0.0 }
                        ?: usuarioDestino?.latitude?.takeIf { it != 0.0 }
                        ?: destinoLatGeocodificado ?: 0.0
                    val destinoLngBase = rastreio?.destinoLng?.takeIf { it != 0.0 }
                        ?: usuarioDestino?.longitude?.takeIf { it != 0.0 }
                        ?: destinoLngGeocodificado ?: 0.0

                    // Caso as coordenadas de destino não tenham sido salvas antes, geocodifica automaticamente
                    LaunchedEffect(rastreio?.destinoLat, rastreio?.destinoLng, usuarioDestino?.uid) {
                        if (destinoLatBase == 0.0 || destinoLngBase == 0.0) {
                            val coord = if (usuarioDestino != null && (usuarioDestino.rua.isNotBlank() || usuarioDestino.cidade.isNotBlank() || usuarioDestino.cep.isNotBlank())) {
                                GeocodingService.buscarCoordenadas(
                                    rua = usuarioDestino.rua,
                                    numero = usuarioDestino.numero,
                                    cidade = usuarioDestino.cidade,
                                    estado = usuarioDestino.estado,
                                    cep = usuarioDestino.cep
                                )
                            } else if (!rastreio?.enderecoDestinoTexto.isNullOrBlank()) {
                                GeocodingService.buscarCoordenadasPorTexto("${rastreio!!.enderecoDestinoTexto}, Brasil")
                            } else null

                            if (coord != null) {
                                destinoLatGeocodificado = coord.latitude
                                destinoLngGeocodificado = coord.longitude
                            }
                        }
                    }

                    val destinoLat = destinoLatBase
                    val destinoLng = destinoLngBase

                    // ================== ROTA ==================
                    var rota by remember { mutableStateOf<Rota?>(null) }
                    var ultimoPontoCalculado by remember { mutableStateOf<PontoRota?>(null) }
                    var ultimoModoMotorista by remember { mutableStateOf<Boolean?>(null) }

                    LaunchedEffect(mostrarMotorista, localizacaoMotorista, pontoOrigem, destinoLat, destinoLng) {
                        if (destinoLat == 0.0 || destinoLng == 0.0) return@LaunchedEffect

                        val usandoMotorista = mostrarMotorista && localizacaoMotorista != null

                        // Em rota: motorista -> destino. Antes disso: loja -> destino (prévia).
                        val partida = if (usandoMotorista) {
                            PontoRota(localizacaoMotorista!!.latitude, localizacaoMotorista!!.longitude)
                        } else pontoOrigem?.let { PontoRota(it.latitude, it.longitude) }
                            ?: return@LaunchedEffect

                        // Só recalcula se mudou de modo (loja -> motorista) ou se o ponto de partida andou mais de 50 m
                        val anterior = ultimoPontoCalculado
                        if (anterior != null && rota != null && ultimoModoMotorista == usandoMotorista) {
                            val d = FloatArray(1)
                            android.location.Location.distanceBetween(
                                anterior.latitude, anterior.longitude,
                                partida.latitude, partida.longitude, d
                            )
                            if (d[0] < 50f) return@LaunchedEffect
                        }

                        RoteamentoService.buscarRota(listOf(partida, PontoRota(destinoLat, destinoLng)))?.let {
                            rota = it
                            ultimoPontoCalculado = partida
                            ultimoModoMotorista = usandoMotorista
                        }
                    }

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

                    // ================== TEMPO ESTIMADO DE CHEGADA / TRAJETO ==================
                    if (mostrarMotorista || (ehMotorista && rota != null)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val r = rota
                                if (r != null && (localizacaoMotorista != null || !mostrarMotorista)) {
                                    val chegada = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                        .format(java.util.Date(System.currentTimeMillis() + (r.duracaoSegundos * 1000).toLong()))
                                    Text(
                                        if (mostrarMotorista) "Chega em ~${r.duracaoTexto}" else "Trajeto estimado: ~${r.duracaoTexto}",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        if (mostrarMotorista) "${r.distanciaTexto} de distância • previsão ~$chegada"
                                        else "${r.distanciaTexto} da loja até o cliente",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                } else {
                                    Text(
                                        "Calculando tempo estimado...",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // ================== MAPA (osmdroid) ==================
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    ) {
                        MapaRastreamento(
                            origem = pontoOrigem?.let {
                                MarcadorMapa(
                                    latitude = it.latitude,
                                    longitude = it.longitude,
                                    titulo = it.nome,
                                    tipo = TipoMarcador.ORIGEM
                                )
                            },
                            destino = if (destinoLat != 0.0 && destinoLng != 0.0) {
                                MarcadorMapa(
                                    latitude = destinoLat,
                                    longitude = destinoLng,
                                    titulo = "Destino",
                                    tipo = TipoMarcador.DESTINO
                                )
                            } else null,
                            motorista = if (mostrarMotorista) {
                                localizacaoMotorista?.let {
                                    MarcadorMapa(
                                        latitude = it.latitude,
                                        longitude = it.longitude,
                                        titulo = "Motorista",
                                        tipo = TipoMarcador.MOTORISTA
                                    )
                                }
                            } else null,
                            rota = rota
                        )
                    }

                    // ================== LEGENDA DINÂMICA ==================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                    // Texto de última atualização
                    if (localizacaoMotorista == null) {
                        Text(
                            if (ehMotorista && !mostrarMotorista) "Inicie a entrega para ativar o rastreamento por GPS em tempo real."
                            else "Aguardando localização do motorista...",
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

                    // ================== PONTO DE ORIGEM ==================
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
                                Icon(
                                    imageVector = Icons.Default.Store,
                                    contentDescription = "Origem",
                                    tint = MaterialTheme.colorScheme.primary,
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

                    // ================== DESTINO ==================
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
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Destino",
                                tint = MaterialTheme.colorScheme.error,
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

                                val nomeCliente = venda.compradorNome.ifBlank { comprador?.nome ?: "" }
                                if (ehMotorista && nomeCliente.isNotBlank()) {
                                    Text(
                                        "Cliente: $nomeCliente",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val enderecoDestino = when {
                                    !rastreio?.enderecoDestinoTexto.isNullOrBlank() -> rastreio!!.enderecoDestinoTexto
                                    usuarioDestino != null && usuarioDestino.rua.isNotBlank() ->
                                        "${usuarioDestino.rua}, ${usuarioDestino.numero} - ${usuarioDestino.cidade}/${usuarioDestino.estado}"
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
                                    if (ehMotorista) "Entrega #${rastreio.posicaoNaFila} da sua fila"
                                    else "Sua entrega é a #${rastreio.posicaoNaFila} da rota",
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
                                    if (ehMotorista) "Pedido pendente na loja."
                                    else "Seu pedido está pendente. Aguardando o vendedor preparar.",
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
                                    if (ehMotorista) "Pedido pronto para coleta no ponto de origem acima."
                                    else "Seu pedido está pronto e aguardando o motorista.",
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
                                    if (ehMotorista) "Siga a rota destacada no mapa até o endereço do cliente."
                                    else "O motorista está a caminho! Acompanhe a localização em tempo real.",
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