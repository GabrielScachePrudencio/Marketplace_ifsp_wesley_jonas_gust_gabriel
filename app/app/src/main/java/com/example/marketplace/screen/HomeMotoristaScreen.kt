package com.example.marketplace.screen

import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.FilaEntregasViewModel
import com.example.marketplace.controller.FilaEntregasViewModelFactory
import com.example.marketplace.controller.UsuarioViewModel
import com.example.marketplace.controller.UsuarioViewModelFactory
import com.example.marketplace.controller.VeiculoViewModel
import com.example.marketplace.controller.VeiculoViewModelFactory
import com.example.marketplace.controller.VendaListViewModel
import com.example.marketplace.controller.VendaListViewModelFactory
import com.example.marketplace.domain.VeiculoRegras
import com.example.marketplace.model.LocalizacaoMotoristaTracker
import com.example.marketplace.util.AvatarUsuario
import com.example.marketplace.util.CampoBusca
import com.example.marketplace.util.correspondeBusca
import com.example.marketplace.util.ImageUtils
import com.example.marketplace.model.enums.StatusEntrega
import com.example.marketplace.model.Usuario
import com.example.marketplace.model.Veiculo
import com.example.marketplace.model.Venda
import java.time.format.DateTimeFormatter
import java.util.jar.Manifest

private enum class FiltroEntrega(val titulo: String) {
    TODAS("Todas"),
    DISPONIVEIS("Disponíveis"),
    EM_ANDAMENTO("Em andamento"),
    CONCLUIDAS("Concluídas")
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMotoristaScreen(
    usuario: Usuario,
    veiculos: List<Veiculo>,
    onCadastrarVeiculo: () -> Unit,
    onFilaEntregas: () -> Unit = {},
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    val usuarioViewModel: UsuarioViewModel = viewModel(
        factory = UsuarioViewModelFactory(context)
    )
    val vendaViewModel: VendaListViewModel = viewModel(
        factory = VendaListViewModelFactory(context)
    )
    val veiculoViewModel: VeiculoViewModel = viewModel(
        factory = VeiculoViewModelFactory(context)
    )

    val negociantes by usuarioViewModel.negociantes.collectAsState()
    val vinculando by usuarioViewModel.vinculando.collectAsState()
    val vendas by vendaViewModel.vendas.collectAsState()

    // Conjunto reativo com os IDs de todos os parceiros vinculados (ilimitado)
    var parceirosVinculadosIds by remember(usuario) {
        mutableStateOf(usuario.todosNegociantesIds().toSet())
    }
    var entregasOcultas by remember { mutableStateOf<Set<String>>(emptySet()) }

    var fotoPerfilAtual by remember(usuario.fotoPerfil) {
        mutableStateOf(usuario.fotoPerfil)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val base64 = ImageUtils.uriParaBase64(context, uri, maxDimensao = 300, qualidade = 70)
            if (base64 != null) {
                usuarioViewModel.atualizarFotoPerfil(usuario.uid, base64) {
                    fotoPerfilAtual = base64
                }
            }
        }
    }

    // Estados para controle de modais e ações
    var veiculoEmEdicao by remember { mutableStateOf<Veiculo?>(null) }
    var veiculoParaExcluir by remember { mutableStateOf<Veiculo?>(null) }
    var vendaDetalheModal by remember { mutableStateOf<Venda?>(null) }

    // Cache local de clientes compradores para exibição de nome e endereço de entrega
    var clientesMap by remember { mutableStateOf<Map<String, Usuario>>(emptyMap()) }

    LaunchedEffect(Unit) {
        usuarioViewModel.carregarNegociantes()
    }

    // Carrega dados completos dos clientes compradores das vendas em exibição
    LaunchedEffect(vendas) {
        val compradorIds = vendas.map { it.compradorId }.filter { it.isNotBlank() }.distinct()
        compradorIds.forEach { uid ->
            if (!clientesMap.containsKey(uid)) {
                usuarioViewModel.carregarUsuario(uid) { cliente ->
                    if (cliente != null) {
                        clientesMap = clientesMap + (uid to cliente)
                    }
                }
            }
        }
    }
    val filaViewModel: FilaEntregasViewModel = viewModel(
        factory = FilaEntregasViewModelFactory(context)
    )

    // Filtra entregas relevantes para o motorista:
    // - Pedidos em PRONTO_PARA_ENTREGA pertencentes a QUALQUER negociante vinculado
    // - Pedidos assumidos por este motorista em A_CAMINHO ou ENTREGUE
    val entregasMotorista = vendas.filter { venda ->
        val status = venda.statusEntrega
        val ehDeParceiro = parceirosVinculadosIds.contains(venda.vendedorId)
        val ehDesteMotorista = venda.motoristaId == usuario.uid
        val foiOcultada = entregasOcultas.contains(venda.id)

        if (foiOcultada) return@filter false

        (ehDeParceiro && status == StatusEntrega.PRONTO_PARA_ENTREGA) ||
                (ehDesteMotorista && (status == StatusEntrega.A_CAMINHO || status == StatusEntrega.SAIU_PARA_ENTREGA)) ||
                (ehDesteMotorista && status == StatusEntrega.ENTREGUE)
    }

    var buscaEntregas by rememberSaveable { mutableStateOf("") }
    var filtroSelecionado by rememberSaveable { mutableStateOf(FiltroEntrega.TODAS) }
    var parceirosExpandidos by rememberSaveable { mutableStateOf(false) }
    var veiculosExpandidos by rememberSaveable { mutableStateOf(false) }
    var menuAcoesAberto by remember { mutableStateOf(false) }
    val entregasFiltradas = entregasMotorista.filter { venda ->
        val cliente = clientesMap[venda.compradorId]
        correspondeBusca(
            buscaEntregas,
            venda.produtoTitulo,
            venda.id,
            venda.compradorNome,
            cliente?.nome,
            cliente?.rua,
            cliente?.cidade,
            negociantes.find { it.uid == venda.vendedorId }?.nome
        )
    }
    val entregasDoFiltro = entregasFiltradas.filter { venda ->
        when (filtroSelecionado) {
            FiltroEntrega.TODAS -> true
            FiltroEntrega.DISPONIVEIS -> venda.statusEntrega == StatusEntrega.PRONTO_PARA_ENTREGA
            FiltroEntrega.EM_ANDAMENTO -> venda.statusEntrega in setOf(
                StatusEntrega.A_CAMINHO,
                StatusEntrega.SAIU_PARA_ENTREGA
            )
            FiltroEntrega.CONCLUIDAS -> venda.statusEntrega == StatusEntrega.ENTREGUE
        }
    }
    // ------------------------------------------------
    // RASTREAMENTO GPS EM TEMPO REAL (SOMENTE SE HOUVER VENDA ATIVA)
    // ------------------------------------------------
    val tracker = remember { LocalizacaoMotoristaTracker(context) }

    var permissaoConcedida by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { concedida ->
        permissaoConcedida = concedida
    }

    LaunchedEffect(Unit) {
        if (!permissaoConcedida) {
            permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Encontra todas as vendas ativas DESTE motorista (A_CAMINHO ou SAIU_PARA_ENTREGA)
    val vendasAtivasIds = entregasMotorista.filter {
        it.motoristaId == usuario.uid &&
                (it.statusEntrega == StatusEntrega.A_CAMINHO || it.statusEntrega == StatusEntrega.SAIU_PARA_ENTREGA)
    }.map { it.id }

    // Chave reativa: se as vendas ativas mudarem (iniciar ou finalizar), o efeito dispara
    LaunchedEffect(vendasAtivasIds, permissaoConcedida) {
        if (vendasAtivasIds.isNotEmpty() && permissaoConcedida) {
            // Só rastreia se tiver venda ativa e permissão
            tracker.iniciar(vendasAtivasIds)
        } else {
            // Se não tiver venda ativa ou permissão, para o rastreamento e limpa no servidor
            tracker.pararSeAtivo(limparNoServidor = true)
        }
    }

    DisposableEffect(Unit) {
        // Ao sair apenas da tela (ex: navegar para Fila de Entregas), para o callback local sem apagar a última posição no Firestore
        onDispose { tracker.pararSeAtivo(limparNoServidor = false) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IFSP Entregas", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onFilaEntregas) {
                        Icon(Icons.Default.LocalShipping, contentDescription = "Minha fila de entregas")
                    }
                    IconButton(onClick = { menuAcoesAberto = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
                    }
                    DropdownMenu(
                        expanded = menuAcoesAberto,
                        onDismissRequest = { menuAcoesAberto = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Limpar minha fila") },
                            onClick = {
                                filaViewModel.limparFila(usuario.uid)
                                menuAcoesAberto = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sair da conta") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                            onClick = {
                                menuAcoesAberto = false
                                onLogout()
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCadastrarVeiculo,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Adicionar veículo") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        AvatarUsuario(
                            fotoBase64 = fotoPerfilAtual.ifEmpty { null },
                            nome = usuario.nome,
                            modifier = Modifier.size(58.dp),
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "CENTRAL DO ENTREGADOR",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                            Text(
                                "Olá, ${usuario.nome.substringBefore(' ')}!",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                "Vamos colocar os pedidos em movimento.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResumoMotorista(
                        valor = entregasMotorista.count { it.statusEntrega == StatusEntrega.PRONTO_PARA_ENTREGA },
                        titulo = "Disponíveis",
                        modifier = Modifier.weight(1f)
                    )
                    ResumoMotorista(
                        valor = entregasMotorista.count {
                            it.statusEntrega == StatusEntrega.A_CAMINHO ||
                                    it.statusEntrega == StatusEntrega.SAIU_PARA_ENTREGA
                        },
                        titulo = "Em rota",
                        modifier = Modifier.weight(1f)
                    )
                    ResumoMotorista(
                        valor = entregasMotorista.count { it.statusEntrega == StatusEntrega.ENTREGUE },
                        titulo = "Concluídas",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text("Entregas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "${entregasMotorista.size} pedido(s) na sua área",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onFilaEntregas) { Text("Organizar fila") }
                    }
                    CampoBusca(
                        valor = buscaEntregas,
                        onValorChange = { buscaEntregas = it },
                        placeholder = "Produto, cliente, loja ou endereço",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FiltroEntrega.values().forEach { filtro ->
                            val quantidade = when (filtro) {
                                FiltroEntrega.TODAS -> entregasFiltradas.size
                                FiltroEntrega.DISPONIVEIS -> entregasFiltradas.count {
                                    it.statusEntrega == StatusEntrega.PRONTO_PARA_ENTREGA
                                }
                                FiltroEntrega.EM_ANDAMENTO -> entregasFiltradas.count {
                                    it.statusEntrega == StatusEntrega.A_CAMINHO ||
                                            it.statusEntrega == StatusEntrega.SAIU_PARA_ENTREGA
                                }
                                FiltroEntrega.CONCLUIDAS -> entregasFiltradas.count {
                                    it.statusEntrega == StatusEntrega.ENTREGUE
                                }
                            }
                            FilterChip(
                                selected = filtroSelecionado == filtro,
                                onClick = { filtroSelecionado = filtro },
                                label = { Text("${filtro.titulo} $quantidade") }
                            )
                        }
                    }
                }
            }

            if (parceirosVinculadosIds.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Vincule-se a uma loja parceira para começar a receber pedidos.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (entregasDoFiltro.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.LocalShipping,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                if (buscaEntregas.isNotBlank()) "Nenhum pedido encontrado"
                                else "Nenhuma entrega nesta categoria",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Tente outro filtro ou volte mais tarde.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(entregasDoFiltro, key = { it.id }) { venda ->
                    val cliente = clientesMap[venda.compradorId]
                    val parceiroVendedor = negociantes.find { it.uid == venda.vendedorId }
                    MotoristaEntregaCard(
                        venda = venda,
                        cliente = cliente,
                        parceiroVendedor = parceiroVendedor,
                        usuario = usuario,
                        veiculos = veiculos,
                        onVerDetalhes = { vendaDetalheModal = venda },
                        onAtualizarStatus = { novoStatus, veiculoId ->
                            vendaViewModel.avancarStatus(
                                vendaId = venda.id,
                                novoStatus = novoStatus,
                                perfil = "motorista",
                                motoristaId = usuario.uid,
                                veiculoId = veiculoId
                            )
                        }
                    )
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Lojas parceiras", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${parceirosVinculadosIds.size} vinculada(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { parceirosExpandidos = !parceirosExpandidos }) {
                                Icon(
                                    if (parceirosExpandidos) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (parceirosExpandidos) "Recolher lojas" else "Expandir lojas"
                                )
                            }
                        }
                        if (parceirosExpandidos) {
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            if (parceirosVinculadosIds.isEmpty()) {
                                Text("Nenhuma loja vinculada ainda.", style = MaterialTheme.typography.bodySmall)
                            }
                            parceirosVinculadosIds.forEach { parceiroId ->
                                val parceiro = negociantes.find { it.uid == parceiroId }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(parceiro?.nome ?: "Loja parceira", fontWeight = FontWeight.Medium)
                                        parceiro?.email?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    TextButton(
                                        enabled = !vinculando,
                                        onClick = {
                                            usuarioViewModel.desvincularNegociante(usuario.uid, parceiroId) {
                                                parceirosVinculadosIds = parceirosVinculadosIds - parceiroId
                                            }
                                        }
                                    ) { Text("Remover") }
                                }
                            }
                            val disponiveis = negociantes.filter { it.uid !in parceirosVinculadosIds }
                            if (disponiveis.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Encontrar lojas", style = MaterialTheme.typography.titleSmall)
                                disponiveis.forEach { negociante ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(negociante.nome, fontWeight = FontWeight.Medium)
                                            if (negociante.cidade.isNotBlank()) {
                                                Text(
                                                    "${negociante.cidade} - ${negociante.estado}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        TextButton(
                                            enabled = !vinculando,
                                            onClick = {
                                                usuarioViewModel.vincularNegociante(usuario.uid, negociante.uid) {
                                                    parceirosVinculadosIds = parceirosVinculadosIds + negociante.uid
                                                }
                                            }
                                        ) { Text("Vincular") }
                                    }
                                }
                            } else if (negociantes.isEmpty()) {
                                Text("Carregando lojas parceiras...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Meus veículos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${veiculos.size} cadastrado(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { veiculosExpandidos = !veiculosExpandidos }) {
                                Icon(
                                    if (veiculosExpandidos) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (veiculosExpandidos) "Recolher veículos" else "Expandir veículos"
                                )
                            }
                        }
                        if (veiculosExpandidos) {
                            HorizontalDivider()
                            if (veiculos.isEmpty()) {
                                Text("Cadastre um veículo para iniciar entregas.", modifier = Modifier.padding(vertical = 10.dp))
                            }
                            veiculos.forEach { veiculo ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${veiculo.marca} ${veiculo.modelo}".trim(), fontWeight = FontWeight.Medium)
                                        Text(
                                            "${veiculo.tipo} • ${veiculo.placa} • ${veiculo.cor}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { veiculoEmEdicao = veiculo }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar ${veiculo.modelo}")
                                    }
                                    IconButton(onClick = { veiculoParaExcluir = veiculo }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Excluir ${veiculo.modelo}", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            TextButton(onClick = onCadastrarVeiculo) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Cadastrar veículo")
                            }
                        }
                    }
                }
            }
        }
    }

    // ========================================================
    // DIÁLOGO DE EDIÇÃO DE VEÍCULO
    // ========================================================
    if (veiculoEmEdicao != null) {
        val v = veiculoEmEdicao!!
        EditarVeiculoDialog(
            veiculo = v,
            onDismiss = { veiculoEmEdicao = null },
            onSalvar = { veiculoAtualizado ->
                veiculoViewModel.atualizarVeiculo(veiculoAtualizado) {
                    veiculoEmEdicao = null
                }
            }
        )
    }

    // ========================================================
    // DIÁLOGO DE CONFIRMAÇÃO DE EXCLUSÃO DE VEÍCULO
    // ========================================================
    if (veiculoParaExcluir != null) {
        val v = veiculoParaExcluir!!
        val estaEmUso = vendas.any { it.veiculoId == v.id && it.statusEntrega == StatusEntrega.A_CAMINHO }

        ConfirmarExclusaoVeiculoDialog(
            veiculo = v,
            estaEmUso = estaEmUso,
            onDismiss = { veiculoParaExcluir = null },
            onConfirmar = {
                veiculoViewModel.excluirVeiculo(v) {
                    veiculoParaExcluir = null
                }
            }
        )
    }

    // ========================================================
    // MODAL DE DETALHES COMPLETOS DA VENDA E ENTREGA
    // ========================================================
    if (vendaDetalheModal != null) {
        val v = vendaDetalheModal!!
        val cliente = clientesMap[v.compradorId]
        val parceiroVendedor = negociantes.find { it.uid == v.vendedorId }
        val veiculoAlocado = veiculos.find { it.id == v.veiculoId }

        DetalhesVendaModal(
            venda = v,
            cliente = cliente,
            parceiroVendedor = parceiroVendedor,
            veiculo = veiculoAlocado,
            onDismiss = { vendaDetalheModal = null }
        )
    }
}

@Composable
private fun ResumoMotorista(valor: Int, titulo: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(valor.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(titulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============================================================
// COMPONENTE: CARD DA ENTREGA PARA O MOTORISTA
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MotoristaEntregaCard(
    venda: Venda,
    cliente: Usuario?,
    parceiroVendedor: Usuario?,
    usuario: Usuario,
    veiculos: List<Veiculo>,
    onVerDetalhes: () -> Unit,
    onAtualizarStatus: (String, String?) -> Unit
) {
    val status = venda.statusEntrega
    var veiculoSelecionado by remember(venda.id) { mutableStateOf<Veiculo?>(null) }
    var menuVeiculoAberto by remember { mutableStateOf(false) }

    // Resolução do Nome do Cliente
    val nomeCliente = when {
        venda.compradorNome.isNotBlank() -> venda.compradorNome
        cliente != null && cliente.nome.isNotBlank() -> cliente.nome
        else -> "Cliente #${venda.compradorId.take(6)}"
    }

    // Resolução do Título do Produto
    val tituloProduto = when {
        venda.produtoTitulo.isNotBlank() -> venda.produtoTitulo
        else -> "Produto #${venda.produtoId.take(6)}"
    }

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

            // Destaque claro do nome do cliente
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Cliente: $nomeCliente",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (cliente != null && cliente.cidade.isNotBlank()) {
                        Text(
                            text = "Destino: ${cliente.cidade} - ${cliente.estado}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text("Produto: $tituloProduto", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text("Quantidade: ${venda.quantidade}", style = MaterialTheme.typography.bodySmall)
            Text("Valor Total: R$ %.2f".format(venda.valorTotal), style = MaterialTheme.typography.bodyMedium)

            if (parceiroVendedor != null) {
                Text(
                    "Origem (Loja): ${parceiroVendedor.nome}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            // Botão para abrir o modal de detalhes
            OutlinedButton(
                onClick = onVerDetalhes,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ver Detalhes do Pedido e Endereço")
            }

            Spacer(Modifier.height(8.dp))

            when (status) {
                StatusEntrega.PENDENTE -> {
                    Text(
                        "Pedido pendente. Aguardando liberação para entrega.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                StatusEntrega.PRONTO_PARA_ENTREGA -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (veiculos.isEmpty()) {
                            Text(
                                "Cadastre um veículo para poder iniciar a entrega.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
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
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuVeiculoAberto) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                )
                                ExposedDropdownMenu(
                                    expanded = menuVeiculoAberto,
                                    onDismissRequest = { menuVeiculoAberto = false }
                                ) {
                                    veiculos.forEach { veiculo ->
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

                        Button(
                            onClick = {
                                onAtualizarStatus(StatusEntrega.A_CAMINHO.name, veiculoSelecionado?.id)
                            },
                            enabled = veiculoSelecionado != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Coletar e Iniciar Entrega (A caminho)")
                        }
                    }
                }

                StatusEntrega.SAIU_PARA_ENTREGA -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val veiculoDaEntrega = veiculos.find { it.id == venda.veiculoId }
                        if (veiculoDaEntrega != null) {
                            Text(
                                "Veículo: ${veiculoDaEntrega.modelo} - ${veiculoDaEntrega.placa}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Text(
                            "Saiu para entrega",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Button(
                            onClick = {
                                onAtualizarStatus(StatusEntrega.ENTREGUE.name, venda.veiculoId)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Confirmar Entrega ao Cliente")
                        }

                        OutlinedButton(
                            onClick = {
                                onAtualizarStatus(StatusEntrega.PRONTO_PARA_ENTREGA.name, venda.veiculoId)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancelar Entrega")
                        }
                    }
                }

                StatusEntrega.A_CAMINHO -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val veiculoDaEntrega = veiculos.find { it.id == venda.veiculoId }
                        if (veiculoDaEntrega != null) {
                            Text(
                                "Veículo: ${veiculoDaEntrega.modelo} - ${veiculoDaEntrega.placa}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = {
                                onAtualizarStatus(StatusEntrega.ENTREGUE.name, venda.veiculoId)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Confirmar Entrega ao Cliente")
                        }

                        OutlinedButton(
                            onClick = {
                                onAtualizarStatus(StatusEntrega.PRONTO_PARA_ENTREGA.name, venda.veiculoId)
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
                                onAtualizarStatus(StatusEntrega.A_CAMINHO.name, venda.veiculoId)
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

// ============================================================
// DIÁLOGO: EDITAR VEÍCULO
// ============================================================
@Composable
private fun EditarVeiculoDialog(
    veiculo: Veiculo,
    onDismiss: () -> Unit,
    onSalvar: (Veiculo) -> Unit
) {
    var tipo by remember { mutableStateOf(veiculo.tipo) }
    var marca by remember { mutableStateOf(veiculo.marca) }
    var modelo by remember { mutableStateOf(veiculo.modelo) }
    var ano by remember { mutableStateOf(if (veiculo.ano > 0) veiculo.ano.toString() else "") }
    var placa by remember { mutableStateOf(veiculo.placa) }
    var cor by remember { mutableStateOf(veiculo.cor) }
    var erroMensagem by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Veículo") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = tipo,
                    onValueChange = { tipo = it },
                    label = { Text("Tipo (Carro, Moto, etc)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = marca,
                    onValueChange = { marca = it },
                    label = { Text("Marca") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = modelo,
                    onValueChange = { modelo = it },
                    label = { Text("Modelo") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ano,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 4) ano = input
                    },
                    label = { Text("Ano") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = placa,
                    onValueChange = { input ->
                        if (input.length <= 7) placa = input.uppercase()
                    },
                    label = { Text("Placa") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cor,
                    onValueChange = { cor = it },
                    label = { Text("Cor") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (erroMensagem != null) {
                    Text(
                        text = erroMensagem!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val anoInt = ano.toIntOrNull() ?: 0
                    try {
                        VeiculoRegras.validar(
                            motoristaId = veiculo.motoristaId,
                            placa = placa,
                            ano = anoInt
                        )
                        if (tipo.isBlank() || marca.isBlank() || modelo.isBlank()) {
                            erroMensagem = "Preencha todos os campos obrigatórios."
                            return@Button
                        }
                        onSalvar(
                            veiculo.copy(
                                tipo = tipo.trim(),
                                marca = marca.trim(),
                                modelo = modelo.trim(),
                                ano = anoInt,
                                placa = placa.trim().uppercase(),
                                cor = cor.trim()
                            )
                        )
                    } catch (e: Exception) {
                        erroMensagem = e.message ?: "Dados inválidos."
                    }
                }
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// ============================================================
// DIÁLOGO: CONFIRMAR EXCLUSÃO DE VEÍCULO
// ============================================================
@Composable
private fun ConfirmarExclusaoVeiculoDialog(
    veiculo: Veiculo,
    estaEmUso: Boolean,
    onDismiss: () -> Unit,
    onConfirmar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Excluir Veículo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Deseja realmente remover o veículo ${veiculo.modelo} (${veiculo.placa})?")
                if (estaEmUso) {
                    Text(
                        "Atenção: Este veículo está atualmente atribuído a uma entrega em transporte!",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmar,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Excluir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ============================================================
// MODAL: DETALHES COMPLETOS DA VENDA E ENTREGA
// ============================================================
@Composable
private fun DetalhesVendaModal(
    venda: Venda,
    cliente: Usuario?,
    parceiroVendedor: Usuario?,
    veiculo: Veiculo?,
    onDismiss: () -> Unit
) {
    val nomeCliente = when {
        venda.compradorNome.isNotBlank() -> venda.compradorNome
        cliente != null && cliente.nome.isNotBlank() -> cliente.nome
        else -> "Cliente #${venda.compradorId.take(6)}"
    }

    val tituloProduto = when {
        venda.produtoTitulo.isNotBlank() -> venda.produtoTitulo
        else -> "Produto #${venda.produtoId.take(6)}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pedido #${venda.id.take(8)}", style = MaterialTheme.typography.titleLarge)
                StatusEntregaBadge(status = venda.statusEntrega)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Dados do cliente e entrega", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Nome: $nomeCliente", fontWeight = FontWeight.SemiBold)
                        if (cliente != null && cliente.email.isNotBlank()) {
                            Text("E-mail: ${cliente.email}", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Endereço de entrega", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (cliente != null && cliente.rua.isNotBlank()) {
                            Text("${cliente.rua}, nº ${cliente.numero}")
                            Text("${cliente.cidade} - ${cliente.estado}")
                            Text("CEP: ${cliente.cep}")
                        } else {
                            Text("Endereço detalhado não disponível no cadastro do cliente.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Detalhes do produto", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Item: $tituloProduto", fontWeight = FontWeight.SemiBold)
                        Text("Quantidade: ${venda.quantidade}")
                        if (venda.valorUnitario > 0.0) {
                            Text("Valor unitário: R$ %.2f".format(venda.valorUnitario))
                        }
                        Text(
                            "Valor total do pedido: R$ %.2f".format(venda.valorTotal),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Ponto de coleta", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        if (parceiroVendedor != null) {
                            Text("Loja: ${parceiroVendedor.nome}", fontWeight = FontWeight.SemiBold)
                            if (parceiroVendedor.rua.isNotBlank()) {
                                Text("${parceiroVendedor.rua}, nº ${parceiroVendedor.numero} - ${parceiroVendedor.cidade}")
                            }
                            if (parceiroVendedor.email.isNotBlank()) {
                                Text("Contato: ${parceiroVendedor.email}", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text("ID do vendedor: ${venda.vendedorId}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (veiculo != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Veículo da entrega", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("${veiculo.modelo} - placa ${veiculo.placa}")
                            Text("Tipo: ${veiculo.tipo} • Cor: ${veiculo.cor}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                val dataFormatada = try {
                    venda.dataCriacao.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                } catch (e: Exception) {
                    ""
                }
                if (dataFormatada.isNotBlank()) {
                    Text(
                        "Data do pedido: $dataFormatada",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Fechar") } }
    )
}

// ============================================================
