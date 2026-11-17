package com.example.marketplace.screen

import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.LocalizacaoMotorista
import com.example.marketplace.controller.ProdutoListViewModel
import com.example.marketplace.controller.ProdutoListViewModelFactory
import com.example.marketplace.controller.VendaListViewModel
import com.example.marketplace.controller.VendaListViewModelFactory
import com.example.marketplace.data.local.AppDatabase
import com.example.marketplace.model.PontoReferencia
import com.example.marketplace.model.Produto
import com.example.marketplace.model.RastreioEntrega
import com.example.marketplace.model.Usuario
import com.example.marketplace.model.Venda
import com.example.marketplace.model.enums.StatusEntrega
import com.example.marketplace.repository.PontoReferenciaRepository
import com.example.marketplace.repository.RastreioEntregaRepository
import com.example.marketplace.service.GeocodingService
import com.example.marketplace.service.PontoRota
import com.example.marketplace.service.Rota
import com.example.marketplace.service.RoteamentoService
import com.example.marketplace.util.AvatarUsuario
import com.example.marketplace.util.CampoBusca
import com.example.marketplace.util.ImagemProduto
import com.example.marketplace.util.correspondeBusca
import com.example.marketplace.util.normalizarBusca
import com.google.firebase.firestore.FirebaseFirestore

private enum class OrdemProdutos(val rotulo: String) {
    RECENTES("Mais recentes"),
    MENOR_PRECO("Menor preço"),
    MAIOR_PRECO("Maior preço")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProdutoListScreen(
    usuario: Usuario,
    onProdutoClick: (String) -> Unit,
    onCriarProduto: () -> Unit,
    onMinhasVendas: () -> Unit,
    onRastrearEntrega: (String) -> Unit = {},
    onPontosReferencia: () -> Unit = {},
    onEditarPerfil: () -> Unit = {},
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ProdutoListViewModel = viewModel(
        factory = ProdutoListViewModelFactory(context)
    )
    val todosProdutos by viewModel.produtos.collectAsState()

    val vendaViewModel: VendaListViewModel = viewModel(
        factory = VendaListViewModelFactory(context)
    )
    val todasVendas by vendaViewModel.vendas.collectAsState()
    val entregasACaminho = remember(todasVendas, usuario.uid, usuario.perfil) {
        if (usuario.perfil != "comprador") {
            emptyList()
        } else {
            todasVendas.filter { venda ->
                venda.compradorId == usuario.uid &&
                    (venda.statusEntrega == StatusEntrega.A_CAMINHO ||
                        venda.statusEntrega == StatusEntrega.SAIU_PARA_ENTREGA)
            }.sortedByDescending { it.data }
        }
    }

    var busca by rememberSaveable { mutableStateOf("") }
    var categoriaSelecionada by rememberSaveable { mutableStateOf<String?>(null) }
    var ordem by rememberSaveable { mutableStateOf(OrdemProdutos.RECENTES) }

    val produtosDoPerfil = if (usuario.perfil == "negociador") {
        todosProdutos.filter { it.vendedorId == usuario.uid }
    } else {
        todosProdutos
    }

    // Categorias digitadas livremente: agrupa ignorando acento/caixa
    val categorias = produtosDoPerfil
        .map { it.categoria.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.normalizarBusca() }
        .sortedBy { it.normalizarBusca() }

    val produtos = produtosDoPerfil
        .filter { categoriaSelecionada == null || it.categoria.normalizarBusca() == categoriaSelecionada!!.normalizarBusca() }
        .filter { correspondeBusca(busca, it.titulo, it.descricao, it.categoria) }
        .let { lista ->
            when (ordem) {
                OrdemProdutos.RECENTES -> lista.sortedByDescending { it.dataCriacao }
                OrdemProdutos.MENOR_PRECO -> lista.sortedBy { it.preco }
                OrdemProdutos.MAIOR_PRECO -> lista.sortedByDescending { it.preco }
            }
        }

    Scaffold(
        topBar = {
            val textoMinhasVendas = if (usuario.perfil == "comprador") "Minhas compras" else "Minhas vendas"
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onEditarPerfil() }
                    ) {
                        AvatarUsuario(
                            fotoBase64 = usuario.fotoPerfil,
                            nome = usuario.nome,
                            modifier = Modifier.size(38.dp),
                            onClick = onEditarPerfil
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("MarketPlace IFSP", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Olá, ${usuario.nome.substringBefore(" ")} • Editar perfil",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (usuario.perfil == "negociador") {
                        AcaoTopo(Icons.Default.Place, "Pontos de referência", onPontosReferencia)
                    }
                    AcaoTopo(Icons.AutoMirrored.Filled.ReceiptLong, textoMinhasVendas, onMinhasVendas)
                    AcaoTopo(Icons.Default.Person, "Editar perfil", onEditarPerfil)
                    AcaoTopo(Icons.AutoMirrored.Filled.Logout, "Sair", onLogout)
                }
            )
        },
        floatingActionButton = {
            if (usuario.perfil == "negociador") {
                ExtendedFloatingActionButton(
                    onClick = onCriarProduto,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Novo produto") }
                )
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = entregasACaminho,
                key = { "aviso_entrega_${it.id}" },
                span = { GridItemSpan(maxLineSpan) }
            ) { venda ->
                AvisoEntregaACaminhoCard(
                    venda = venda,
                    usuario = usuario,
                    onClick = { onRastrearEntrega(venda.id) }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampoBusca(
                        valor = busca,
                        onValorChange = { busca = it },
                        placeholder = "Buscar por nome, descrição ou categoria",
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (categorias.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = categoriaSelecionada == null,
                                    onClick = { categoriaSelecionada = null },
                                    label = { Text("Todas") }
                                )
                            }
                            items(categorias) { categoria ->
                                FilterChip(
                                    selected = categoriaSelecionada?.normalizarBusca() == categoria.normalizarBusca(),
                                    onClick = {
                                        categoriaSelecionada =
                                            if (categoriaSelecionada?.normalizarBusca() == categoria.normalizarBusca()) null
                                            else categoria
                                    },
                                    label = { Text(categoria) }
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${produtos.size} produto(s)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SeletorOrdem(ordem = ordem, onOrdemChange = { ordem = it })
                    }
                }
            }

            if (produtos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val filtrando = busca.isNotBlank() || categoriaSelecionada != null
                    EstadoVazio(
                        icone = if (filtrando) Icons.Default.SearchOff else Icons.Default.Inventory2,
                        titulo = if (filtrando) "Nenhum produto encontrado" else "Nenhum produto cadastrado ainda",
                        descricao = if (filtrando) "Tente outra palavra ou limpe os filtros."
                        else if (usuario.perfil == "negociador") "Toque em \"Novo produto\" para começar a vender."
                        else "Volte mais tarde para ver as novidades."
                    )
                }
            } else {
                items(produtos, key = { it.id }) { produto ->
                    ProdutoCard(produto = produto, onClick = { onProdutoClick(produto.id) })
                }
            }
        }
    }
}

@Composable
private fun AcaoTopo(icone: ImageVector, descricao: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icone, contentDescription = descricao)
    }
}

@Composable
private fun SeletorOrdem(ordem: OrdemProdutos, onOrdemChange: (OrdemProdutos) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { aberto = true }) { Text(ordem.rotulo) }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            OrdemProdutos.entries.forEach { opcao ->
                DropdownMenuItem(
                    text = { Text(opcao.rotulo) },
                    onClick = {
                        onOrdemChange(opcao)
                        aberto = false
                    }
                )
            }
        }
    }
}

@Composable
fun EstadoVazio(icone: ImageVector, titulo: String, descricao: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icone,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(12.dp))
        Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            descricao,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProdutoCard(produto: Produto, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Box {
            ImagemProduto(
                fotoBase64 = produto.imagens.firstOrNull(),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (produto.quantidade <= 0) {
                    SeloCard("Esgotado", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
                }
                if (produto.videoPartes > 0) {
                    Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(50)) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = "Tem vídeo",
                            tint = Color.White,
                            modifier = Modifier.padding(4.dp).size(16.dp)
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                produto.titulo,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(min = 40.dp)
            )
            Text(
                produto.categoria,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "R$ %.2f".format(produto.preco),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (produto.quantidade > 0) {
                Text(
                    "${produto.quantidade} em estoque",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SeloCard(texto: String, fundo: Color, cor: Color) {
    Surface(color = fundo, shape = RoundedCornerShape(50)) {
        Text(
            texto,
            color = cor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun AvisoEntregaACaminhoCard(
    venda: Venda,
    usuario: Usuario,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val db = remember(context) { AppDatabase.getDatabase(context) }
    val rastreioRepository = remember(db) {
        RastreioEntregaRepository(db.rastreioEntregaDao(), db.pendenteSycronizacaoDao())
    }
    val pontoReferenciaRepository = remember(db) {
        PontoReferenciaRepository(db.pontoReferenciaDao(), db.pendenteSycronizacaoDao())
    }

    val rastreio by produceState<RastreioEntrega?>(initialValue = null, key1 = venda.id) {
        value = rastreioRepository.buscarPorVendaIdUmaVez(venda.id)
        rastreioRepository.buscarPorVendaId(venda.id).collect { atualizado ->
            if (atualizado != null) value = atualizado
        }
    }

    val pontoOrigem by produceState<PontoReferencia?>(initialValue = null, key1 = rastreio?.pontoOrigemId) {
        val id = rastreio?.pontoOrigemId.orEmpty()
        value = if (id.isNotBlank()) pontoReferenciaRepository.buscarPontoPorId(id) else null
    }

    var localizacaoMotorista by remember(venda.id) { mutableStateOf<LocalizacaoMotorista?>(null) }

    DisposableEffect(venda.id) {
        val listener = try {
            FirebaseFirestore.getInstance()
                .collection("localizacoes_tempo_real")
                .document(venda.id)
                .addSnapshotListener { snapshot, erro ->
                    if (erro != null) return@addSnapshotListener
                    val lat = snapshot?.getDouble("latitude")
                    val lng = snapshot?.getDouble("longitude")
                    val timestamp = snapshot?.getLong("timestamp")
                    val precisao = (snapshot?.get("precisaoMetros") as? Number)?.toDouble()
                    if (lat != null && lng != null && timestamp != null && lat != 0.0 && lng != 0.0) {
                        localizacaoMotorista = LocalizacaoMotorista(lat, lng, timestamp, precisao)
                    } else {
                        localizacaoMotorista = null
                    }
                }
        } catch (_: Exception) {
            null
        }
        onDispose {
            listener?.remove()
        }
    }

    var destinoLatGeocodificado by remember(venda.id) { mutableStateOf<Double?>(null) }
    var destinoLngGeocodificado by remember(venda.id) { mutableStateOf<Double?>(null) }

    val destinoLat = rastreio?.destinoLat?.takeIf { it != 0.0 }
        ?: usuario.latitude.takeIf { it != 0.0 }
        ?: destinoLatGeocodificado ?: 0.0
    val destinoLng = rastreio?.destinoLng?.takeIf { it != 0.0 }
        ?: usuario.longitude.takeIf { it != 0.0 }
        ?: destinoLngGeocodificado ?: 0.0

    LaunchedEffect(rastreio?.destinoLat, rastreio?.destinoLng, rastreio?.enderecoDestinoTexto, usuario.uid) {
        if (destinoLat == 0.0 || destinoLng == 0.0) {
            val coord = if (usuario.rua.isNotBlank() || usuario.cidade.isNotBlank() || usuario.cep.isNotBlank()) {
                GeocodingService.buscarCoordenadas(
                    rua = usuario.rua,
                    numero = usuario.numero,
                    cidade = usuario.cidade,
                    estado = usuario.estado,
                    cep = usuario.cep
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

    var rota by remember(venda.id) { mutableStateOf<Rota?>(null) }
    var calculandoRota by remember(venda.id) { mutableStateOf(true) }
    var ultimoPontoCalculado by remember(venda.id) { mutableStateOf<PontoRota?>(null) }
    var ultimoDestinoCalculado by remember(venda.id) { mutableStateOf<PontoRota?>(null) }
    var ultimoModoMotorista by remember(venda.id) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(venda.id) {
        kotlinx.coroutines.delay(6000)
        if (rota == null) {
            calculandoRota = false
        }
    }

    LaunchedEffect(
        localizacaoMotorista,
        rastreio?.ultimaLat,
        rastreio?.ultimaLng,
        pontoOrigem,
        destinoLat,
        destinoLng
    ) {
        if (destinoLat == 0.0 || destinoLng == 0.0) {
            return@LaunchedEffect
        }

        val locMotorista = localizacaoMotorista
        val usandoMotorista = locMotorista != null
        val partida = when {
            locMotorista != null -> PontoRota(locMotorista.latitude, locMotorista.longitude)
            rastreio?.ultimaLat != null && rastreio?.ultimaLng != null &&
                rastreio!!.ultimaLat != 0.0 && rastreio!!.ultimaLng != 0.0 ->
                PontoRota(rastreio!!.ultimaLat!!, rastreio!!.ultimaLng!!)
            pontoOrigem != null && pontoOrigem!!.latitude != 0.0 && pontoOrigem!!.longitude != 0.0 ->
                PontoRota(pontoOrigem!!.latitude, pontoOrigem!!.longitude)
            else -> return@LaunchedEffect
        }
        val destinoRota = PontoRota(destinoLat, destinoLng)

        val anterior = ultimoPontoCalculado
        if (anterior != null && rota != null &&
            ultimoModoMotorista == usandoMotorista &&
            ultimoDestinoCalculado == destinoRota
        ) {
            val d = FloatArray(1)
            Location.distanceBetween(
                anterior.latitude, anterior.longitude,
                partida.latitude, partida.longitude, d
            )
            if (d[0] < 10f) return@LaunchedEffect
        }

        calculandoRota = rota == null
        val novaRota = RoteamentoService.buscarRota(listOf(partida, destinoRota))
        if (novaRota != null) {
            rota = novaRota
            ultimoPontoCalculado = partida
            ultimoModoMotorista = usandoMotorista
            ultimoDestinoCalculado = destinoRota
        } else if (rota == null) {
            val d = FloatArray(1)
            Location.distanceBetween(
                partida.latitude, partida.longitude,
                destinoRota.latitude, destinoRota.longitude, d
            )
            val distanciaEstimada = (d[0].toDouble() * 1.3).coerceAtLeast(50.0)
            val duracaoEstimada = (distanciaEstimada / 8.33).coerceAtLeast(60.0)
            rota = Rota(
                pontos = listOf(partida, destinoRota),
                distanciaMetros = distanciaEstimada,
                duracaoSegundos = duracaoEstimada
            )
            ultimoPontoCalculado = partida
            ultimoModoMotorista = usandoMotorista
            ultimoDestinoCalculado = destinoRota
        }
        calculandoRota = false
    }

    val tituloProduto = venda.produtoTitulo.ifBlank { "Pedido #${venda.id.take(8)}" }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = "Entrega a caminho",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(10.dp).size(26.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Sua entrega está a caminho!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Pedido: $tituloProduto",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val r = rota
                if (r != null) {
                    val chegada = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(System.currentTimeMillis() + (r.duracaoSegundos * 1000).toLong()))
                    Text(
                        text = "Chega em ~${r.duracaoTexto} (${r.distanciaTexto} • previsão ~$chegada)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (calculandoRota) {
                    Text(
                        text = "Calculando tempo estimado...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Text(
                        text = "Tempo estimado disponível no mapa de rastreio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                if (venda.proximidadeNotificada) {
                    Text(
                        text = "📍 O entregador já está próximo do seu endereço!",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "Toque para abrir a tela de rastreio →",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}
