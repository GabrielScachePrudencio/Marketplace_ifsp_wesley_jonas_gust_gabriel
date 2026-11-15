package com.example.marketplace.screen

import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.example.marketplace.controller.RastreamentoEntregaUiState
import com.example.marketplace.controller.LocalizacaoMotorista
import com.example.marketplace.controller.RastreamentoEntregaViewModel
import com.example.marketplace.controller.RastreamentoEntregaViewModelFactory
import com.example.marketplace.model.LocalizacaoMotoristaTracker
import com.example.marketplace.model.Usuario
import com.example.marketplace.model.enums.StatusEntrega
import com.example.marketplace.service.GeocodingService
import com.example.marketplace.service.LocationTrackingService
import com.example.marketplace.service.PontoRota
import com.example.marketplace.service.ProximidadeEntregaPolicy
import com.example.marketplace.service.Rota
import com.example.marketplace.service.RoteamentoService
import com.example.marketplace.util.MapaRastreamento
import com.example.marketplace.util.MarcadorMapa
import com.example.marketplace.util.TipoMarcador
import com.example.marketplace.util.hasLocationPermission
import com.example.marketplace.util.hasPreciseLocationPermission
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

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
    var raioCercaMetros by remember { mutableStateOf(ProximidadeEntregaPolicy.raioMetros) }

    LaunchedEffect(Unit) {
        ProximidadeEntregaPolicy.carregar(context)
        raioCercaMetros = ProximidadeEntregaPolicy.raioMetros
    }
    val comprovante by viewModel.comprovante.collectAsState()
    val comprovanteRetirada by viewModel.comprovanteRetirada.collectAsState()
    val entregaEmAndamento by viewModel.entregaEmAndamento.collectAsState()
    val mensagemEntrega by viewModel.mensagemEntrega.collectAsState()
    val ehMotorista = usuario.perfil == "motorista"
    val scope = rememberCoroutineScope()
    var temPermissaoLocalizacao by remember {
        mutableStateOf(hasLocationPermission(context))
    }
    var temPermissaoLocalizacaoPrecisa by remember {
        mutableStateOf(hasPreciseLocationPermission(context))
    }
    var erroCaptura by remember { mutableStateOf<String?>(null) }
    var fotoPendente by remember { mutableStateOf<Uri?>(null) }

    val permissaoLocalizacaoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissoes ->
        val concedida = permissoes[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissoes[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                hasLocationPermission(context)
        temPermissaoLocalizacao = concedida
        temPermissaoLocalizacaoPrecisa =
            permissoes[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                hasPreciseLocationPermission(context)
        if (!concedida) erroCaptura = "A permissão de localização é necessária para registrar a posição."
        else if (!temPermissaoLocalizacaoPrecisa) {
            erroCaptura = "A detecção do raio de 10 m exige localização precisa. Ative essa opção nas permissões do app."
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { fotoCapturada ->
        val uri = fotoPendente
        fotoPendente = null
        if (!fotoCapturada || uri == null) {
            erroCaptura = if (fotoCapturada) "A foto não pôde ser lida." else null
            return@rememberLauncherForActivityResult
        }

        val vendaAtual = (uiState as? RastreamentoEntregaUiState.Carregado)?.venda
        if (vendaAtual == null) {
            erroCaptura = "Não foi possível associar a foto ao pedido."
            return@rememberLauncherForActivityResult
        }
        erroCaptura = "Buscando a localização da foto..."
        scope.launch {
            try {
                val localizacaoAtual = LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token
                    ).await()
                val coordenadas = localizacaoAtual?.let { it.latitude to it.longitude }
                    ?: localizacaoMotorista?.takeIf {
                        System.currentTimeMillis() - it.timestamp < 60_000L
                    }?.let { it.latitude to it.longitude }
                if (coordenadas == null) {
                    erroCaptura = "Não foi possível obter a localização atual. Tente novamente em um local com GPS."
                } else {
                    erroCaptura = null
                    viewModel.confirmarEntrega(
                        vendaId = vendaAtual.id,
                        motoristaId = usuario.uid,
                        fotoUri = uri,
                        latitude = coordenadas.first,
                        longitude = coordenadas.second
                    )
                }
            } catch (e: Exception) {
                erroCaptura = e.message ?: "Não foi possível obter a localização da foto."
            }
        }
    }

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
                    val pedidoProntoParaRetirada =
                        venda.statusEntrega == StatusEntrega.PRONTO_PARA_ENTREGA
                    // O motorista só aparece se o status for A_CAMINHO ou SAIU_PARA_ENTREGA.
                    val mostrarMotorista = venda.statusEntrega == StatusEntrega.A_CAMINHO ||
                            venda.statusEntrega == StatusEntrega.SAIU_PARA_ENTREGA
                    var localizacaoMotoristaDispositivo by remember(venda.id) {
                        mutableStateOf<LocalizacaoMotorista?>(null)
                    }

                    LaunchedEffect(venda.id, pedidoProntoParaRetirada, temPermissaoLocalizacao) {
                        if (!ehMotorista || !pedidoProntoParaRetirada) {
                            localizacaoMotoristaDispositivo = null
                            return@LaunchedEffect
                        }
                        if (!temPermissaoLocalizacao) {
                            permissaoLocalizacaoLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                            return@LaunchedEffect
                        }
                        try {
                            val localizacaoAtual = LocationServices.getFusedLocationProviderClient(context)
                                .getCurrentLocation(
                                    Priority.PRIORITY_HIGH_ACCURACY,
                                    CancellationTokenSource().token
                                ).await()
                            localizacaoMotoristaDispositivo = localizacaoAtual?.let {
                                LocalizacaoMotorista(it.latitude, it.longitude, System.currentTimeMillis())
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            erroCaptura = e.message ?: "Não foi possível obter a localização atual do motorista."
                        }
                    }

                    LaunchedEffect(
                        venda.id,
                        venda.status,
                        temPermissaoLocalizacao,
                        temPermissaoLocalizacaoPrecisa
                    ) {
                        if (ehMotorista && venda.motoristaId == usuario.uid) {
                            if (mostrarMotorista) {
                                if (!temPermissaoLocalizacao) {
                                    permissaoLocalizacaoLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                } else if (temPermissaoLocalizacaoPrecisa) {
                                    androidx.core.content.ContextCompat.startForegroundService(
                                        context,
                                        Intent(context, LocationTrackingService::class.java).apply {
                                            action = LocationTrackingService.ACTION_START_TRACKING
                                            putExtra(LocationTrackingService.EXTRA_VENDA_ID, venda.id)
                                        }
                                    )
                                } else if (LocationTrackingService.servicoAtivo) {
                                    context.startService(
                                        Intent(context, LocationTrackingService::class.java).apply {
                                            action = LocationTrackingService.ACTION_REMOVE_VENDA
                                            putExtra(LocationTrackingService.EXTRA_VENDA_ID, venda.id)
                                            putExtra(LocationTrackingService.EXTRA_LIMPAR_SERVIDOR, true)
                                        }
                                    )
                                }
                            } else {
                                LocationTrackingService.limparCacheVenda(venda.id)
                                LocationTrackingService.removerLocalizacaoServidor(venda.id)
                                if (LocationTrackingService.servicoAtivo) {
                                    context.startService(
                                        Intent(context, LocationTrackingService::class.java).apply {
                                            action = LocationTrackingService.ACTION_REMOVE_VENDA
                                            putExtra(LocationTrackingService.EXTRA_VENDA_ID, venda.id)
                                            putExtra(LocationTrackingService.EXTRA_LIMPAR_SERVIDOR, true)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Fallback local caso o ForegroundService ainda não esteja ativo
                    if (ehMotorista) {
                        val tracker = remember { LocalizacaoMotoristaTracker(context) }
                        LaunchedEffect(
                            venda.id,
                            mostrarMotorista,
                            temPermissaoLocalizacao,
                            temPermissaoLocalizacaoPrecisa
                        ) {
                            if (
                                mostrarMotorista &&
                                venda.motoristaId == usuario.uid &&
                                temPermissaoLocalizacao &&
                                temPermissaoLocalizacaoPrecisa
                            ) {
                                tracker.iniciar(venda.id)
                            } else {
                                val limparServidor = venda.motoristaId == usuario.uid &&
                                    (!mostrarMotorista || !temPermissaoLocalizacaoPrecisa)
                                tracker.pararSeAtivo(limparNoServidor = limparServidor)
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
                        if (!pedidoProntoParaRetirada &&
                            (destinoLatBase == 0.0 || destinoLngBase == 0.0)
                        ) {
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
                                if (rastreio != null && (rastreio.destinoLat == 0.0 || rastreio.destinoLng == 0.0)) {
                                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("rastreios_entrega")
                                        .document(rastreio.id)
                                        .update(
                                            mapOf(
                                                "destinoLat" to coord.latitude,
                                                "destinoLng" to coord.longitude
                                            )
                                        )
                                }
                            }
                        }
                    }

                    val destinoLat = destinoLatBase
                    val destinoLng = destinoLngBase
                    val destinoMapaLat = if (pedidoProntoParaRetirada) {
                        pontoOrigem?.latitude?.takeIf { it != 0.0 } ?: 0.0
                    } else destinoLat
                    val destinoMapaLng = if (pedidoProntoParaRetirada) {
                        pontoOrigem?.longitude?.takeIf { it != 0.0 } ?: 0.0
                    } else destinoLng
                    val localizacaoRotaMotorista = when {
                        pedidoProntoParaRetirada -> localizacaoMotoristaDispositivo
                        mostrarMotorista -> localizacaoMotorista
                        else -> null
                    }
                    val localizacaoExibida = localizacaoRotaMotorista

                    // ================== ROTA ==================
                    var rota by remember { mutableStateOf<Rota?>(null) }
                    var ultimoPontoCalculado by remember { mutableStateOf<PontoRota?>(null) }
                    var ultimoModoMotorista by remember { mutableStateOf<Boolean?>(null) }
                    var ultimoDestinoCalculado by remember { mutableStateOf<PontoRota?>(null) }
                    var ruaMotorista by remember(venda.id) { mutableStateOf("") }
                    var ultimaBuscaRuaEm by remember(venda.id) { mutableStateOf(0L) }

                    LaunchedEffect(localizacaoMotorista?.timestamp) {
                        val localizacao = localizacaoMotorista ?: return@LaunchedEffect
                        if (localizacao.timestamp - ultimaBuscaRuaEm < 30_000L) return@LaunchedEffect
                        ultimaBuscaRuaEm = localizacao.timestamp
                        ruaMotorista = GeocodingService.buscarRuaPorCoordenadas(
                            localizacao.latitude,
                            localizacao.longitude
                        ).orEmpty()
                    }

                    LaunchedEffect(
                        mostrarMotorista,
                        pedidoProntoParaRetirada,
                        localizacaoRotaMotorista,
                        pontoOrigem,
                        destinoMapaLat,
                        destinoMapaLng
                    ) {
                        if (destinoMapaLat == 0.0 || destinoMapaLng == 0.0) {
                            rota = null
                            return@LaunchedEffect
                        }

                        val usandoMotorista = localizacaoRotaMotorista != null &&
                                (mostrarMotorista || (ehMotorista && pedidoProntoParaRetirada))

                        // Na retirada: motorista -> ponto de referência. Na entrega: motorista -> cliente.
                        val partida = if (usandoMotorista) {
                            PontoRota(localizacaoRotaMotorista!!.latitude, localizacaoRotaMotorista.longitude)
                        } else if (pedidoProntoParaRetirada) {
                            rota = null
                            return@LaunchedEffect
                        } else pontoOrigem?.let { PontoRota(it.latitude, it.longitude) }
                            ?: return@LaunchedEffect
                        val destinoRota = PontoRota(destinoMapaLat, destinoMapaLng)
                        if (ultimoDestinoCalculado != destinoRota || ultimoModoMotorista != usandoMotorista) {
                            rota = null
                        }

                        // Recalcula se mudar a etapa/destino ou se o ponto de partida andar mais de 10 m.
                        val anterior = ultimoPontoCalculado
                        if (anterior != null && rota != null &&
                            ultimoModoMotorista == usandoMotorista &&
                            ultimoDestinoCalculado == destinoRota
                        ) {
                            val d = FloatArray(1)
                            android.location.Location.distanceBetween(
                                anterior.latitude, anterior.longitude,
                                partida.latitude, partida.longitude, d
                            )
                            if (d[0] < 10f) return@LaunchedEffect
                        }

                        val novaRota = RoteamentoService.buscarRota(listOf(partida, destinoRota))
                        rota = novaRota
                        if (novaRota != null) {
                            ultimoPontoCalculado = partida
                            ultimoModoMotorista = usandoMotorista
                            ultimoDestinoCalculado = destinoRota
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
                            if (venda.proximidadeNotificada && mostrarMotorista) {
                                Text(
                                    "📍 Entregador próximo! (a menos de ${raioCercaMetros.toInt()} m do endereço)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                "Pedido: ${venda.produtoTitulo}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Quantidade: ${venda.quantidade}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        val novoRaio = ProximidadeEntregaPolicy.proximoRaio()
                                        ProximidadeEntregaPolicy.definirRaio(context, novoRaio)
                                        raioCercaMetros = novoRaio
                                    }
                                ) {
                                    Text("Raio da cerca: ${raioCercaMetros.toInt()} m")
                                }
                            }
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
                                        if (mostrarMotorista) "Chega em ~${r.duracaoTexto}" else "Trajeto até a coleta: ~${r.duracaoTexto}",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        if (mostrarMotorista) "${r.distanciaTexto} de distância • previsão ~$chegada"
                                        else "${r.distanciaTexto} até o ponto de referência",
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
                            origem = if (pedidoProntoParaRetirada) null else pontoOrigem?.let {
                                MarcadorMapa(
                                    latitude = it.latitude,
                                    longitude = it.longitude,
                                    titulo = it.nome,
                                    tipo = TipoMarcador.ORIGEM
                                )
                            },
                            destino = if (destinoMapaLat != 0.0 && destinoMapaLng != 0.0) {
                                MarcadorMapa(
                                    latitude = destinoMapaLat,
                                    longitude = destinoMapaLng,
                                    titulo = if (pedidoProntoParaRetirada) {
                                        "Ponto de coleta · ${pontoOrigem?.nome ?: "referência"}"
                                    } else "Endereço de entrega",
                                    tipo = TipoMarcador.DESTINO
                                )
                            } else null,
                            motorista = if (mostrarMotorista || (ehMotorista && pedidoProntoParaRetirada)) {
                                localizacaoRotaMotorista?.let {
                                    MarcadorMapa(
                                        latitude = it.latitude,
                                        longitude = it.longitude,
                                        titulo = if (ruaMotorista.isNotBlank()) {
                                            "Motorista · $ruaMotorista"
                                        } else "Motorista",
                                        tipo = TipoMarcador.MOTORISTA
                                    )
                                }
                            } else null,
                            rota = rota,
                            cercaVirtual = if (!pedidoProntoParaRetirada &&
                                destinoMapaLat != 0.0 && destinoMapaLng != 0.0
                            ) {
                                MarcadorMapa(
                                    latitude = destinoMapaLat,
                                    longitude = destinoMapaLng,
                                    titulo = "Cerca virtual · ${raioCercaMetros.toInt()} m",
                                    tipo = TipoMarcador.DESTINO
                                )
                            } else null,
                            raioCercaMetros = raioCercaMetros.toDouble()
                        )
                    }

                    if (mostrarMotorista) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Rua atual do motorista",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    when {
                                        localizacaoMotorista == null -> "Aguardando localização do motorista..."
                                        ruaMotorista.isBlank() -> "Buscando nome da rua..."
                                        else -> ruaMotorista
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (rota?.etapas?.isNotEmpty() == true) {
                                    val etapas = rota!!.etapas
                                    val etapaAtual = localizacaoMotorista?.let {
                                        encontrarEtapaAtual(rota!!, it.latitude, it.longitude)
                                    }
                                    HorizontalDivider()
                                    Text("Percurso de ruas", style = MaterialTheme.typography.titleSmall)
                                    etapas.forEachIndexed { index, etapa ->
                                        val atual = index == etapaAtual
                                        val concluida = etapaAtual != null && index < etapaAtual
                                        Text(
                                            text = when {
                                                atual -> "●  ${etapa.nomeRua} — motorista aqui"
                                                concluida -> "✓  ${etapa.nomeRua}"
                                                else -> "○  ${etapa.nomeRua}"
                                            },
                                            style = if (atual) MaterialTheme.typography.bodyMedium
                                            else MaterialTheme.typography.bodySmall,
                                            color = if (atual) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (atual) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                } else if (rota != null) {
                                    Text(
                                        "Não foi possível obter os nomes das ruas deste percurso.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    if (ehMotorista && venda.motoristaId == usuario.uid &&
                        mostrarMotorista && rastreio != null
                    ) {
                        Button(
                            onClick = {
                                erroCaptura = null
                                if (!temPermissaoLocalizacao) {
                                    permissaoLocalizacaoLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                    return@Button
                                }
                                val pastaFotos = File(context.cacheDir, "comprovantes")
                                if (!pastaFotos.exists() && !pastaFotos.mkdirs()) {
                                    erroCaptura = "Não foi possível preparar o arquivo da foto."
                                    return@Button
                                }
                                try {
                                    val arquivo = File.createTempFile("entrega_${venda.id}_", ".jpg", pastaFotos)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        arquivo
                                    )
                                    fotoPendente = uri
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    erroCaptura = e.message ?: "Não foi possível abrir a câmera."
                                }
                            },
                            enabled = !entregaEmAndamento,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (entregaEmAndamento) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Enviando comprovante...")
                            } else {
                                Text("Tirar foto e confirmar entrega")
                            }
                        }
                        (erroCaptura ?: mensagemEntrega)?.let { mensagem ->
                            Text(
                                mensagem,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    comprovante?.let { ComprovanteEntregaCard(it) }
                    comprovanteRetirada?.let { ComprovanteRetiradaCard(it) }

                    // ================== LEGENDA DINÂMICA ==================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!pedidoProntoParaRetirada) {
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
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (pedidoProntoParaRetirada) Icons.Default.Store else Icons.Default.Home,
                                contentDescription = null,
                                tint = if (pedidoProntoParaRetirada) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (pedidoProntoParaRetirada) "Ponto de retirada" else "Destino",
                                style = MaterialTheme.typography.labelSmall
                            )
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
                    if (localizacaoExibida == null) {
                        Text(
                            if (ehMotorista && pedidoProntoParaRetirada) "Aguardando a localização atual para traçar a rota até a retirada."
                            else if (ehMotorista && !mostrarMotorista) "Inicie a entrega para ativar o rastreamento por GPS em tempo real."
                            else "Aguardando localização do motorista...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Última atualização: ${
                                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                    .format(java.util.Date(localizacaoExibida.timestamp))
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
                                containerColor = if (pedidoProntoParaRetirada) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else MaterialTheme.colorScheme.surfaceVariant
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
                                        if (pedidoProntoParaRetirada) "PONTO DE REFERÊNCIA PARA RETIRADA"
                                        else "PONTO DE ORIGEM",
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
                                imageVector = if (pedidoProntoParaRetirada) Icons.Default.Store else Icons.Default.Home,
                                contentDescription = if (pedidoProntoParaRetirada) "Ponto de retirada" else "Destino",
                                tint = if (pedidoProntoParaRetirada) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    if (pedidoProntoParaRetirada) "PONTO DE COLETA / REFERÊNCIA"
                                    else "ENDEREÇO DE ENTREGA",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (pedidoProntoParaRetirada) {
                                        MaterialTheme.colorScheme.primary
                                    } else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )

                                if (pedidoProntoParaRetirada) {
                                    Text(
                                        pontoOrigem?.let {
                                            "${it.nome}\n${it.rua}, ${it.numero}\n${it.cidade} - ${it.estado}"
                                        } ?: "Carregando os dados do ponto de referência...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    val nomeCliente = venda.compradorNome.ifBlank { comprador?.nome ?: "" }
                                    if ((ehMotorista || usuario.perfil == "negociador") && nomeCliente.isNotBlank()) {
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
                                    if (ehMotorista) {
                                        "Pedido pronto para retirada no ponto de referência destacado no mapa. " +
                                                "A rota parte da sua localização atual."
                                    } else {
                                        "Seu pedido está pronto no ponto de referência destacado no mapa e aguardando o motorista."
                                    },
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
                    if (ehMotorista && pedidoProntoParaRetirada) {
                        erroCaptura?.let { mensagem ->
                            Text(
                                mensagem,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
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

private fun encontrarEtapaAtual(rota: Rota, latitude: Double, longitude: Double): Int? {
    return rota.etapas.indices.minByOrNull { indice ->
        rota.etapas[indice].pontos.minOfOrNull { ponto ->
            val distancia = FloatArray(1)
            Location.distanceBetween(
                latitude,
                longitude,
                ponto.latitude,
                ponto.longitude,
                distancia
            )
            distancia[0]
        } ?: Float.MAX_VALUE
    }
}
