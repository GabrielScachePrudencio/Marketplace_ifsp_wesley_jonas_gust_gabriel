package com.example.marketplace.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.AtualizarStatusUiState
import com.example.marketplace.controller.UsuarioViewModel
import com.example.marketplace.controller.UsuarioViewModelFactory
import com.example.marketplace.controller.VeiculoViewModel
import com.example.marketplace.controller.VeiculoViewModelFactory
import com.example.marketplace.controller.VendaListViewModel
import com.example.marketplace.controller.VendaListViewModelFactory
import com.example.marketplace.data.local.AppDatabase
import com.example.marketplace.model.ComprovanteEntrega
import com.example.marketplace.model.ComprovanteRetirada
import com.example.marketplace.model.PontoReferencia
import com.example.marketplace.model.RastreioEntrega
import com.example.marketplace.model.Usuario
import com.example.marketplace.model.Veiculo
import com.example.marketplace.model.Venda
import com.example.marketplace.model.enums.StatusEntrega
import com.example.marketplace.repository.ComprovanteEntregaRepository
import com.example.marketplace.repository.ComprovanteRetiradaRepository
import com.example.marketplace.repository.PontoReferenciaRepository
import com.example.marketplace.repository.ProdutoRepository
import com.example.marketplace.repository.RastreioEntregaRepository
import com.example.marketplace.repository.VendaRepository
import com.example.marketplace.service.Coordenadas
import com.example.marketplace.service.GeocodingService
import com.example.marketplace.service.LocationTrackingService
import com.example.marketplace.util.hasLocationPermission
import com.example.marketplace.util.hasPreciseLocationPermission
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedidoDetalheScreen(
    usuario: Usuario,
    vendaId: String,
    onVoltar: () -> Unit,
    onRastrearEntrega: (String) -> Unit = {},
    onAbrirOutroPedido: (String) -> Unit = {},
    onCadastrarVeiculo: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val vendaViewModel: VendaListViewModel = viewModel(
        factory = VendaListViewModelFactory(context)
    )
    val usuarioViewModel: UsuarioViewModel = viewModel(
        factory = UsuarioViewModelFactory(context)
    )
    val veiculoViewModel: VeiculoViewModel = viewModel(
        factory = VeiculoViewModelFactory(context)
    )

    val todasVendas by vendaViewModel.vendas.collectAsState()
    val statusUiState by vendaViewModel.statusUiState.collectAsState()
    val veiculosMotorista by veiculoViewModel.veiculos.collectAsState()

    val db = remember(context) { AppDatabase.getDatabase(context) }
    val comprovanteRetiradaRepo = remember(context) { ComprovanteRetiradaRepository(context) }
    val comprovanteEntregaRepo = remember(context) { ComprovanteEntregaRepository(context) }
    val rastreioRepo = remember(db) {
        RastreioEntregaRepository(db.rastreioEntregaDao(), db.pendenteSycronizacaoDao())
    }
    val pontoReferenciaRepo = remember(db) {
        PontoReferenciaRepository(db.pontoReferenciaDao(), db.pendenteSycronizacaoDao())
    }
    val vendaRepo = remember(db) {
        VendaRepository(
            db.vendaDao(),
            db.pendenteSycronizacaoDao(),
            ProdutoRepository(db.produtoDao(), db.pendenteSycronizacaoDao()),
            rastreioRepo,
            comprovanteRetiradaRepo
        )
    }

    var carregandoVenda by remember(vendaId) { mutableStateOf(true) }
    val venda by produceState<Venda?>(initialValue = null, key1 = vendaId) {
        carregandoVenda = true
        value = vendaRepo.buscarVendaPorId(vendaId)
        carregandoVenda = false
        vendaRepo.observarVendaPorId(vendaId).collect { atualizada ->
            if (atualizada != null) {
                value = atualizada
            }
        }
    }

    val rastreio by produceState<RastreioEntrega?>(initialValue = null, key1 = vendaId) {
        value = rastreioRepo.buscarPorVendaIdUmaVez(vendaId)
        rastreioRepo.buscarPorVendaId(vendaId).collect { atualizado ->
            value = atualizado
        }
    }

    val pontoOrigem by produceState<PontoReferencia?>(initialValue = null, key1 = rastreio?.pontoOrigemId) {
        val id = rastreio?.pontoOrigemId.orEmpty()
        value = if (id.isNotBlank()) pontoReferenciaRepo.buscarPontoPorId(id) else null
    }

    val comprovanteRetirada by produceState<ComprovanteRetirada?>(initialValue = null, key1 = vendaId) {
        comprovanteRetiradaRepo.observarPorVenda(vendaId).collect { value = it }
    }

    val comprovanteEntrega by produceState<ComprovanteEntrega?>(initialValue = null, key1 = vendaId) {
        comprovanteEntregaRepo.observarPorVenda(vendaId).collect { value = it }
    }

    var comprador by remember { mutableStateOf<Usuario?>(null) }
    var vendedor by remember { mutableStateOf<Usuario?>(null) }
    var motorista by remember { mutableStateOf<Usuario?>(null) }

    LaunchedEffect(venda?.compradorId) {
        val id = venda?.compradorId.orEmpty()
        if (id.isNotBlank()) {
            usuarioViewModel.carregarUsuario(id) { comprador = it }
        }
    }

    LaunchedEffect(venda?.vendedorId) {
        val id = venda?.vendedorId.orEmpty()
        if (id.isNotBlank()) {
            usuarioViewModel.carregarUsuario(id) { vendedor = it }
        }
    }

    LaunchedEffect(venda?.motoristaId, usuario.uid, usuario.perfil) {
        val motId = venda?.motoristaId.orEmpty()
        if (motId.isNotBlank()) {
            usuarioViewModel.carregarUsuario(motId) { motorista = it }
        } else {
            motorista = null
        }
        val idParaVeiculos = if (usuario.perfil == "motorista") usuario.uid else motId
        if (idParaVeiculos.isNotBlank()) {
            veiculoViewModel.carregarVeiculosDoMotorista(idParaVeiculos)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            vendaViewModel.resetarStatus()
        }
    }

    var mostrarDialogoPontoOrigem by remember { mutableStateOf(false) }
    var mostrarDialogoCancelar by remember { mutableStateOf(false) }

    // Estados para confirmação de entrega com foto pelo motorista diretamente nesta tela
    var fotoEntregaPendente by remember(vendaId) { mutableStateOf<Uri?>(null) }
    var confirmandoEntrega by remember(vendaId) { mutableStateOf(false) }
    var erroEntrega by remember(vendaId) { mutableStateOf<String?>(null) }
    var mostrarAnimacaoEntregue by remember(vendaId) { mutableStateOf(false) }

    val permissaoLocalizacaoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissoes ->
        val concedida = permissoes[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissoes[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            hasLocationPermission(context)
        if (!concedida) {
            erroEntrega = "A permissão de localização é necessária para registrar a entrega."
        }
    }

    val cameraEntregaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { fotoCapturada ->
        val uri = fotoEntregaPendente
        fotoEntregaPendente = null
        if (!fotoCapturada || uri == null) {
            if (fotoCapturada) erroEntrega = "A foto não pôde ser lida."
            return@rememberLauncherForActivityResult
        }
        val vendaAtual = venda ?: return@rememberLauncherForActivityResult
        confirmandoEntrega = true
        erroEntrega = null
        scope.launch {
            try {
                val localizacao = LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token
                    ).await()
                    ?: error("Não foi possível obter a localização atual. Ative o GPS e tente novamente.")

                comprovanteEntregaRepo.salvar(
                    vendaId = vendaAtual.id,
                    motoristaId = usuario.uid,
                    fotoUri = uri,
                    latitude = localizacao.latitude,
                    longitude = localizacao.longitude
                )
                rastreio?.let { r ->
                    rastreioRepo.finalizarEntrega(r.id, localizacao.latitude, localizacao.longitude)
                }
                vendaRepo.atualizarStatusVenda(
                    id = vendaAtual.id,
                    novoStatus = StatusEntrega.ENTREGUE.name,
                    perfil = "motorista",
                    motoristaId = usuario.uid,
                    veiculoId = vendaAtual.veiculoId
                )
                mostrarAnimacaoEntregue = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                erroEntrega = e.message ?: "Não foi possível confirmar a entrega."
            } finally {
                confirmandoEntrega = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        venda?.let { "Pedido #${it.id.take(8)}" } ?: "Detalhes do Pedido",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        if (carregandoVenda && venda == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val vendaAtual = venda
        if (vendaAtual == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Pedido não encontrado.", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }

        val status = vendaAtual.statusEntrega
        val ehComprador = usuario.perfil == "comprador" && vendaAtual.compradorId == usuario.uid
        val ehNegociador = usuario.perfil == "negociador" && vendaAtual.vendedorId == usuario.uid
        val ehMotorista = usuario.perfil == "motorista"

        val outraEntregaAtiva = remember(todasVendas, vendaAtual.id, usuario.uid, ehMotorista) {
            if (!ehMotorista) null
            else todasVendas.firstOrNull {
                it.id != vendaAtual.id &&
                    it.motoristaId == usuario.uid &&
                    (it.statusEntrega == StatusEntrega.A_CAMINHO ||
                        it.statusEntrega == StatusEntrega.SAIU_PARA_ENTREGA)
            }
        }

        var veiculoSelecionado by remember(vendaAtual.id, veiculosMotorista, vendaAtual.veiculoId) {
            mutableStateOf(
                veiculosMotorista.find { it.id == vendaAtual.veiculoId }
                    ?: veiculosMotorista.singleOrNull()
            )
        }
        var menuVeiculoAberto by remember { mutableStateOf(false) }

        val tituloProduto = vendaAtual.produtoTitulo.ifBlank {
            "Produto #${vendaAtual.produtoId.take(6)}"
        }
        val nomeCliente = vendaAtual.compradorNome.ifBlank {
            comprador?.nome?.takeIf { it.isNotBlank() } ?: "Cliente #${vendaAtual.compradorId.take(6)}"
        }
        val dataFormatada = remember(vendaAtual.dataCriacao) {
            try {
                vendaAtual.dataCriacao.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"))
            } catch (_: Exception) {
                ""
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==================== 1. CABEÇALHO E STATUS ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Pedido #${vendaAtual.id.take(8)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (dataFormatada.isNotBlank()) {
                                Text(
                                    text = "Realizado em $dataFormatada",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        StatusEntregaBadge(status = status)
                    }

                    HorizontalDivider()

                    val descricaoStatus = when {
                        ehComprador -> when (status) {
                            StatusEntrega.PENDENTE -> "Aguardando o vendedor preparar seu pedido."
                            StatusEntrega.PRONTO_PARA_ENTREGA -> "Seu pedido está pronto no ponto de referência e aguardando coleta pelo motorista."
                            StatusEntrega.SAIU_PARA_ENTREGA, StatusEntrega.A_CAMINHO -> "Seu pedido está a caminho com o motorista! Você pode acompanhar no mapa em tempo real."
                            StatusEntrega.ENTREGUE -> "Pedido entregue! Confira abaixo os detalhes e comprovantes."
                            StatusEntrega.CANCELADA -> "Este pedido foi cancelado."
                        }
                        ehNegociador -> when (status) {
                            StatusEntrega.PENDENTE -> "Pedido recebido. Prepare o pacote e marque como 'Pronto para Entrega' escolhendo o ponto de coleta."
                            StatusEntrega.PRONTO_PARA_ENTREGA -> "Pedido aguardando coleta pelo motorista parceiro."
                            StatusEntrega.SAIU_PARA_ENTREGA, StatusEntrega.A_CAMINHO -> "Pedido coletado e em transporte pelo motorista."
                            StatusEntrega.ENTREGUE -> "Entrega finalizada pelo motorista."
                            StatusEntrega.CANCELADA -> "Este pedido foi cancelado."
                        }
                        else -> when (status) {
                            StatusEntrega.PENDENTE -> "Pedido ainda em preparação pela loja."
                            StatusEntrega.PRONTO_PARA_ENTREGA -> "Disponível para coleta! Escolha seu veículo e tire a foto da retirada para iniciar a entrega."
                            StatusEntrega.SAIU_PARA_ENTREGA, StatusEntrega.A_CAMINHO -> "Entrega em andamento! Siga até o endereço do cliente e registre a foto de entrega."
                            StatusEntrega.ENTREGUE -> "Entrega concluída com sucesso!"
                            StatusEntrega.CANCELADA -> "Pedido cancelado."
                        }
                    }

                    Text(
                        text = descricaoStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ==================== 2. AÇÕES E PERMISSÕES DO USUÁRIO ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val tituloAcoes = when {
                        ehComprador -> "Suas ações (Comprador)"
                        ehNegociador -> "Gerenciar pedido (Negociador)"
                        ehMotorista -> "Gerenciar entrega (Motorista)"
                        else -> "Ações do pedido"
                    }
                    Text(
                        text = tituloAcoes,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // ---------- PERMISSÕES DO COMPRADOR ----------
                    if (ehComprador) {
                        if (status in setOf(
                                StatusEntrega.PRONTO_PARA_ENTREGA,
                                StatusEntrega.SAIU_PARA_ENTREGA,
                                StatusEntrega.A_CAMINHO,
                                StatusEntrega.ENTREGUE
                            )
                        ) {
                            Button(
                                onClick = { onRastrearEntrega(vendaAtual.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (status == StatusEntrega.ENTREGUE) "Ver Mapa e Comprovantes"
                                    else "Rastrear Entrega no Mapa"
                                )
                            }
                        }

                        if (status == StatusEntrega.PENDENTE || status == StatusEntrega.PRONTO_PARA_ENTREGA) {
                            OutlinedButton(
                                onClick = { mostrarDialogoCancelar = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancelar Compra")
                            }
                        } else if (status != StatusEntrega.CANCELADA) {
                            Text(
                                text = "O cancelamento pelo comprador só é permitido antes de o pedido sair para entrega.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // ---------- PERMISSÕES DO NEGOCIADOR ----------
                    if (ehNegociador) {
                        when (status) {
                            StatusEntrega.PENDENTE -> {
                                Button(
                                    onClick = { mostrarDialogoPontoOrigem = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Store, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Marcar como Pronto para Entrega")
                                }
                                OutlinedButton(
                                    onClick = { mostrarDialogoCancelar = true },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Cancelar Pedido")
                                }
                            }

                            StatusEntrega.PRONTO_PARA_ENTREGA -> {
                                Button(
                                    onClick = { onRastrearEntrega(vendaAtual.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Ver Ponto de Coleta no Mapa")
                                }
                                OutlinedButton(
                                    onClick = { mostrarDialogoPontoOrigem = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Alterar Ponto de Coleta")
                                }
                                OutlinedButton(
                                    onClick = {
                                        vendaViewModel.avancarStatus(
                                            vendaId = vendaAtual.id,
                                            novoStatus = StatusEntrega.PENDENTE.name,
                                            perfil = "negociador"
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Voltar Status para Pendente")
                                }
                                OutlinedButton(
                                    onClick = { mostrarDialogoCancelar = true },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Cancelar Pedido")
                                }
                            }

                            StatusEntrega.SAIU_PARA_ENTREGA, StatusEntrega.A_CAMINHO -> {
                                Button(
                                    onClick = { onRastrearEntrega(vendaAtual.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Acompanhar Entrega no Mapa")
                                }
                                Text(
                                    text = "O pedido já está em posse do motorista e em rota de entrega.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            StatusEntrega.ENTREGUE -> {
                                Button(
                                    onClick = { onRastrearEntrega(vendaAtual.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Ver Mapa e Comprovantes")
                                }
                            }

                            StatusEntrega.CANCELADA -> {
                                Text(
                                    text = "Pedido cancelado. Nenhuma alteração de status disponível.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // ---------- PERMISSÕES DO MOTORISTA ----------
                    if (ehMotorista) {
                        if (status in setOf(
                                StatusEntrega.PRONTO_PARA_ENTREGA,
                                StatusEntrega.SAIU_PARA_ENTREGA,
                                StatusEntrega.A_CAMINHO,
                                StatusEntrega.ENTREGUE
                            )
                        ) {
                            FilledTonalButton(
                                onClick = { onRastrearEntrega(vendaAtual.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (status == StatusEntrega.PRONTO_PARA_ENTREGA) "Ver Rota até o Ponto de Coleta"
                                    else "Abrir Mapa e Rota da Entrega"
                                )
                            }
                        }

                        when (status) {
                            StatusEntrega.PENDENTE -> {
                                Text(
                                    text = "Este pedido ainda está pendente com o vendedor. Aguarde a loja marcar como 'Pronto para entrega'.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            StatusEntrega.PRONTO_PARA_ENTREGA -> {
                                if (outraEntregaAtiva != null) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "⚠️ Limite de 1 entrega por vez",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Text(
                                                text = "Você já possui o Pedido #${outraEntregaAtiva.id.take(8)} vinculado em andamento. Conclua ou devolva a entrega atual antes de assumir outro pedido.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Button(
                                                onClick = { onAbrirOutroPedido(outraEntregaAtiva.id) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Ir para minha entrega atual (#${outraEntregaAtiva.id.take(8)})")
                                            }
                                        }
                                    }
                                } else {
                                    if (veiculosMotorista.isEmpty()) {
                                        Text(
                                            text = "Você precisa cadastrar um veículo para iniciar entregas.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        if (onCadastrarVeiculo != null) {
                                            OutlinedButton(
                                                onClick = onCadastrarVeiculo,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.DirectionsCar, contentDescription = null)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Cadastrar Veículo")
                                            }
                                        }
                                    } else {
                                        ExposedDropdownMenuBox(
                                            expanded = menuVeiculoAberto,
                                            onExpandedChange = { menuVeiculoAberto = it }
                                        ) {
                                            OutlinedTextField(
                                                value = veiculoSelecionado?.let { "${it.modelo} - ${it.placa}" } ?: "",
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("Veículo para a entrega") },
                                                trailingIcon = {
                                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuVeiculoAberto)
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                            )
                                            ExposedDropdownMenu(
                                                expanded = menuVeiculoAberto,
                                                onDismissRequest = { menuVeiculoAberto = false }
                                            ) {
                                                veiculosMotorista.forEach { veiculo ->
                                                    DropdownMenuItem(
                                                        text = { Text("${veiculo.modelo} - ${veiculo.placa}") },
                                                        onClick = {
                                                            veiculoSelecionado = veiculo
                                                            menuVeiculoAberto = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    ConfirmarRetiradaButton(
                                        vendaId = vendaAtual.id,
                                        motoristaId = usuario.uid,
                                        enabled = veiculoSelecionado != null,
                                        onConfirmado = {
                                            vendaViewModel.avancarStatus(
                                                vendaId = vendaAtual.id,
                                                novoStatus = StatusEntrega.A_CAMINHO.name,
                                                perfil = "motorista",
                                                motoristaId = usuario.uid,
                                                veiculoId = veiculoSelecionado?.id
                                            )
                                            if (hasPreciseLocationPermission(context)) {
                                                ContextCompat.startForegroundService(
                                                    context,
                                                    Intent(context, LocationTrackingService::class.java).apply {
                                                        action = LocationTrackingService.ACTION_START_TRACKING
                                                        putExtra(LocationTrackingService.EXTRA_VENDA_ID, vendaAtual.id)
                                                    }
                                                )
                                            }
                                        }
                                    )
                                }
                            }

                            StatusEntrega.SAIU_PARA_ENTREGA, StatusEntrega.A_CAMINHO -> {
                                Button(
                                    onClick = {
                                        erroEntrega = null
                                        if (!hasLocationPermission(context)) {
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
                                            erroEntrega = "Não foi possível preparar o arquivo da foto."
                                            return@Button
                                        }
                                        try {
                                            val arquivo = File.createTempFile(
                                                "entrega_${vendaAtual.id}_",
                                                ".jpg",
                                                pastaFotos
                                            )
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                arquivo
                                            )
                                            fotoEntregaPendente = uri
                                            cameraEntregaLauncher.launch(uri)
                                        } catch (e: Exception) {
                                            erroEntrega = e.message ?: "Não foi possível abrir a câmera."
                                        }
                                    },
                                    enabled = !confirmandoEntrega,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (confirmandoEntrega) {
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

                                erroEntrega?.let { msg ->
                                    Text(
                                        text = msg,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        vendaViewModel.avancarStatus(
                                            vendaId = vendaAtual.id,
                                            novoStatus = StatusEntrega.PRONTO_PARA_ENTREGA.name,
                                            perfil = "motorista",
                                            motoristaId = usuario.uid,
                                            veiculoId = vendaAtual.veiculoId
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Cancelar Coleta / Devolver para Loja")
                                }
                            }

                            StatusEntrega.ENTREGUE -> {
                                if (outraEntregaAtiva != null) {
                                    Text(
                                        text = "Não é possível reabrir esta entrega enquanto o Pedido #${outraEntregaAtiva.id.take(8)} estiver em andamento (limite de 1 entrega por vez).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            vendaViewModel.avancarStatus(
                                                vendaId = vendaAtual.id,
                                                novoStatus = StatusEntrega.A_CAMINHO.name,
                                                perfil = "motorista",
                                                motoristaId = usuario.uid,
                                                veiculoId = vendaAtual.veiculoId
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Desfazer Entrega (Voltar para A Caminho)")
                                    }
                                }
                            }

                            StatusEntrega.CANCELADA -> {
                                Text(
                                    text = "Pedido cancelado. Nenhuma ação de entrega disponível.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    if (statusUiState is AtualizarStatusUiState.Erro) {
                        Text(
                            text = (statusUiState as AtualizarStatusUiState.Erro).mensagem,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // ==================== 3. DADOS DO PRODUTO ====================
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ShoppingBag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Itens do Pedido",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider()
                    Text(
                        text = tituloProduto,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Quantidade: ${vendaAtual.quantidade} unidade(s)")
                    if (vendaAtual.valorUnitario > 0.0) {
                        Text("Valor unitário: R$ %.2f".format(vendaAtual.valorUnitario))
                    }
                    Text(
                        text = "Valor total: R$ %.2f".format(vendaAtual.valorTotal),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ==================== 4. COMPRADOR E ENDEREÇO DE ENTREGA ====================
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Destinatário e Endereço de Entrega",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider()
                    Text("Cliente: $nomeCliente", fontWeight = FontWeight.SemiBold)
                    comprador?.email?.takeIf { it.isNotBlank() }?.let {
                        Text("Contato: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    val enderecoTexto = when {
                        !rastreio?.enderecoDestinoTexto.isNullOrBlank() -> rastreio!!.enderecoDestinoTexto
                        comprador != null && comprador!!.rua.isNotBlank() ->
                            "${comprador!!.rua}, nº ${comprador!!.numero} - ${comprador!!.cidade}/${comprador!!.estado} (CEP: ${comprador!!.cep})"
                        ehComprador && usuario.rua.isNotBlank() ->
                            "${usuario.rua}, nº ${usuario.numero} - ${usuario.cidade}/${usuario.estado} (CEP: ${usuario.cep})"
                        else -> "Endereço cadastrado pelo comprador"
                    }
                    Text("Endereço: $enderecoTexto", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // ==================== 5. VENDEDOR E PONTO DE COLETA ====================
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Store,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Loja / Ponto de Coleta",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider()
                    Text(
                        text = "Loja: ${vendedor?.nome ?: "Loja #${vendaAtual.vendedorId.take(6)}"}",
                        fontWeight = FontWeight.SemiBold
                    )
                    vendedor?.email?.takeIf { it.isNotBlank() }?.let {
                        Text("Contato da loja: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    if (pontoOrigem != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Ponto de Referência para Coleta:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(pontoOrigem!!.nome, fontWeight = FontWeight.Medium)
                        Text("${pontoOrigem!!.rua}, ${pontoOrigem!!.numero} - ${pontoOrigem!!.cidade}/${pontoOrigem!!.estado}")
                    } else if (vendedor != null && vendedor!!.rua.isNotBlank()) {
                        Text("Endereço da loja: ${vendedor!!.rua}, nº ${vendedor!!.numero} - ${vendedor!!.cidade}/${vendedor!!.estado}")
                    }
                }
            }

            // ==================== 6. MOTORISTA E VEÍCULO ====================
            if (vendaAtual.motoristaId.isNotBlank() || vendaAtual.veiculoId != null) {
                val veiculoDaVenda = veiculosMotorista.find { it.id == vendaAtual.veiculoId }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocalShipping,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Motorista e Veículo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HorizontalDivider()
                        if (vendaAtual.motoristaId.isNotBlank()) {
                            Text(
                                "Motorista: ${motorista?.nome ?: "Motorista #${vendaAtual.motoristaId.take(6)}"}",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (veiculoDaVenda != null) {
                            Text("Veículo: ${veiculoDaVenda.marca} ${veiculoDaVenda.modelo} (${veiculoDaVenda.placa})")
                            Text(
                                "Tipo: ${veiculoDaVenda.tipo} • Cor: ${veiculoDaVenda.cor}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ==================== 7. COMPROVANTES ====================
            comprovanteRetirada?.let { ComprovanteRetiradaCard(it) }
            comprovanteEntrega?.let { ComprovanteEntregaCard(it) }
        }

        // Diálogo para escolher ponto de origem (Negociador)
        if (mostrarDialogoPontoOrigem) {
            EscolherPontoOrigemDialog(
                negocianteId = usuario.uid,
                venda = vendaAtual,
                onConfirmar = { ponto ->
                    mostrarDialogoPontoOrigem = false
                    scope.launch {
                        val comp = comprador ?: usuarioViewModel.buscarUsuarioPorId(vendaAtual.compradorId)
                        val coordenadas = if (
                            comp?.latitude != null && comp.longitude != null &&
                            comp.latitude != 0.0 && comp.longitude != 0.0
                        ) {
                            Coordenadas(comp.latitude, comp.longitude)
                        } else if (comp != null) {
                            GeocodingService.buscarCoordenadas(
                                rua = comp.rua,
                                numero = comp.numero,
                                cidade = comp.cidade,
                                estado = comp.estado,
                                cep = comp.cep
                            )
                        } else null

                        val enderecoTexto = comp?.let {
                            val partes = listOf(
                                listOf(it.rua, it.numero).filter { s -> s.isNotBlank() }.joinToString(", "),
                                listOf(it.cidade, it.estado).filter { s -> s.isNotBlank() }.joinToString("/")
                            ).filter { s -> s.isNotBlank() }
                            partes.joinToString(" - ")
                        }?.takeIf { it.isNotBlank() } ?: "Endereço do comprador"

                        vendaViewModel.marcarProntoParaEntrega(
                            venda = vendaAtual,
                            pontoOrigemId = ponto.id,
                            enderecoDestinoTexto = enderecoTexto,
                            destinoLat = coordenadas?.latitude ?: 0.0,
                            destinoLng = coordenadas?.longitude ?: 0.0
                        )
                    }
                },
                onDismiss = { mostrarDialogoPontoOrigem = false }
            )
        }

        // Diálogo de confirmação de cancelamento (Comprador / Negociador)
        if (mostrarDialogoCancelar) {
            AlertDialog(
                onDismissRequest = { mostrarDialogoCancelar = false },
                title = { Text("Cancelar pedido") },
                text = {
                    Text("Tem certeza que deseja cancelar o pedido de \"$tituloProduto\"? Essa ação devolverá os itens ao estoque e não poderá ser desfeita.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            vendaViewModel.avancarStatus(
                                vendaId = vendaAtual.id,
                                novoStatus = StatusEntrega.CANCELADA.name,
                                perfil = usuario.perfil
                            )
                            mostrarDialogoCancelar = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Sim, cancelar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarDialogoCancelar = false }) {
                        Text("Voltar")
                    }
                }
            )
        }

        EntregaConcluidaAnimacaoDialog(
            visivel = mostrarAnimacaoEntregue,
            vendaId = vendaAtual.id,
            produtoTitulo = vendaAtual.produtoTitulo,
            onDismiss = { mostrarAnimacaoEntregue = false }
        )
    }
}
