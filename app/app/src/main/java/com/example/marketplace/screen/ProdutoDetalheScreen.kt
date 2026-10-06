package com.example.marketplace.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.AvaliacaoUiState
import com.example.marketplace.controller.CompraUiState
import com.example.marketplace.controller.ProdutoDetalheViewModel
import com.example.marketplace.controller.ProdutoDetalheViewModelFactory
import com.example.marketplace.controller.VideoUiState
import com.example.marketplace.domain.AvaliacaoProdutoRegras
import com.example.marketplace.model.AvaliacaoProduto
import com.example.marketplace.model.Usuario
import com.example.marketplace.util.GaleriaFotosDialog
import com.example.marketplace.util.ImagemProduto
import com.example.marketplace.util.MidiaUtils
import com.example.marketplace.util.PlayerVideo

private val CorEstrela = Color(0xFFF5A623)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProdutoDetalheScreen(
    usuario: Usuario,
    produtoId: String,
    onVoltar: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ProdutoDetalheViewModel = viewModel(
        factory = ProdutoDetalheViewModelFactory(context)
    )

    LaunchedEffect(produtoId) {
        viewModel.carregarProduto(produtoId, usuario.uid)
    }

    val produtoAtual by viewModel.produto.collectAsState()
    val avaliacoes by viewModel.avaliacoes.collectAsState()
    val compraUiState by viewModel.compraUiState.collectAsState()
    val avaliacaoUiState by viewModel.avaliacaoUiState.collectAsState()
    val podeAvaliar by viewModel.podeAvaliar.collectAsState()
    val videoUiState by viewModel.videoUiState.collectAsState()

    var quantidadeCompra by remember { mutableIntStateOf(1) }
    var nota by remember { mutableIntStateOf(5) }
    var comentario by remember { mutableStateOf("") }
    var indiceGaleria by remember(produtoId) { mutableStateOf<Int?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(compraUiState) {
        if (compraUiState is CompraUiState.Sucesso) {
            quantidadeCompra = 1
            viewModel.resetarCompra()
            snackbarHostState.showSnackbar("Compra realizada! Acompanhe em \"Minhas compras\".")
        }
    }
    LaunchedEffect(avaliacaoUiState) {
        if (avaliacaoUiState is AvaliacaoUiState.Sucesso) {
            comentario = ""
            viewModel.resetarAvaliacao()
            snackbarHostState.showSnackbar("Obrigado pela sua avaliação!")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(produtoAtual?.titulo ?: "Produto", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        val produto = produtoAtual
        if (produto == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val ehComprador = usuario.perfil == "comprador" && produto.vendedorId != usuario.uid
        val jaAvaliou = avaliacoes.any { it.usuarioId == usuario.uid }
        val mediaNotas = avaliacoes.map { it.nota }.average().takeIf { avaliacoes.isNotEmpty() }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CarrosselFotos(
                    imagens = produto.imagens,
                    onAbrirFoto = { indice -> indiceGaleria = indice }
                )
            }

            item {
                Column {
                    if (produto.categoria.isNotBlank()) {
                        AssistChip(onClick = {}, label = { Text(produto.categoria) })
                    }
                    Text(produto.titulo, style = MaterialTheme.typography.headlineSmall)
                    if (mediaNotas != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Estrelas(nota = Math.round(mediaNotas).toInt(), tamanho = 18.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "%.1f (%d)".format(mediaNotas, avaliacoes.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "R$ %.2f".format(produto.preco),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (produto.quantidade > 0) "${produto.quantidade} unidade(s) em estoque" else "Produto esgotado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (produto.quantidade > 0) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Descrição", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(produto.descricao, style = MaterialTheme.typography.bodyLarge)
                }
            }

            if (produto.videoPartes > 0) {
                item {
                    SecaoVideo(produtoId = produto.id, videoUiState = videoUiState)
                }
            }

            if (ehComprador) {
                item {
                    CardSecao(titulo = "Comprar") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Quantidade", modifier = Modifier.weight(1f))
                            FilledTonalIconButton(
                                onClick = { if (quantidadeCompra > 1) quantidadeCompra-- },
                                enabled = quantidadeCompra > 1
                            ) { Icon(Icons.Default.Remove, contentDescription = "Diminuir") }
                            Text(
                                quantidadeCompra.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            FilledTonalIconButton(
                                onClick = { if (quantidadeCompra < produto.quantidade) quantidadeCompra++ },
                                enabled = quantidadeCompra < produto.quantidade
                            ) { Icon(Icons.Default.Add, contentDescription = "Aumentar") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "R$ %.2f".format(produto.preco * quantidadeCompra),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (compraUiState is CompraUiState.Erro) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                (compraUiState as CompraUiState.Erro).mensagem,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.comprar(usuario.uid, quantidadeCompra, usuario.nome) },
                            enabled = compraUiState !is CompraUiState.Loading && produto.quantidade > 0,
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            if (compraUiState is CompraUiState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(if (produto.quantidade > 0) "Comprar agora" else "Esgotado")
                            }
                        }
                    }
                }

                if (!jaAvaliou) {
                    item {
                        CardSecao(titulo = "Avaliar produto") {
                            if (!podeAvaliar) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        AvaliacaoProdutoRegras.MENSAGEM_PEDIDO_NAO_FINALIZADO + ".",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Text("Toque nas estrelas para dar sua nota", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(4.dp))
                                Estrelas(nota = nota, tamanho = 36.dp, onNotaChange = { nota = it })
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = comentario,
                                    onValueChange = { comentario = it },
                                    label = { Text("Comentário (opcional)") },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (avaliacaoUiState is AvaliacaoUiState.Erro) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        (avaliacaoUiState as AvaliacaoUiState.Erro).mensagem,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.avaliar(usuario.uid, nota, comentario) },
                                    enabled = avaliacaoUiState !is AvaliacaoUiState.Loading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (avaliacaoUiState is AvaliacaoUiState.Loading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Text("Enviar avaliação")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("Avaliações (${avaliacoes.size})", style = MaterialTheme.typography.titleMedium)
            }
            if (avaliacoes.isEmpty()) {
                item {
                    Text(
                        "Nenhuma avaliação ainda.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(avaliacoes.sortedByDescending { it.data }, key = { it.id }) { avaliacao ->
                    AvaliacaoCard(avaliacao, ehMinha = avaliacao.usuarioId == usuario.uid)
                }
            }
        }
    }

    indiceGaleria?.let { indice ->
        GaleriaFotosDialog(
            imagens = produtoAtual?.imagens.orEmpty(),
            indiceInicial = indice,
            onDismiss = { indiceGaleria = null }
        )
    }
}

@Composable
private fun CarrosselFotos(imagens: List<String>, onAbrirFoto: (Int) -> Unit) {
    if (imagens.isEmpty()) {
        ImagemProduto(
            fotoBase64 = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(20.dp))
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { imagens.size })

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pagina ->
            ImagemProduto(
                fotoBase64 = imagens[pagina],
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onAbrirFoto(pagina) }
            )
        }

        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clickable { onAbrirFoto(pagerState.currentPage) }
        ) {
            Icon(
                Icons.Default.Fullscreen,
                contentDescription = "Ver fotos em tela cheia",
                tint = Color.White,
                modifier = Modifier.padding(6.dp)
            )
        }

        if (imagens.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(imagens.size) { indice ->
                    val ativo = indice == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (ativo) 8.dp else 6.dp)
                            .background(
                                if (ativo) Color.White else Color.White.copy(alpha = 0.5f),
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun SecaoVideo(produtoId: String, videoUiState: VideoUiState) {
    val context = LocalContext.current
    var uriVideo by remember(produtoId) { mutableStateOf<Uri?>(null) }

    LaunchedEffect(videoUiState) {
        if (videoUiState is VideoUiState.Pronto) {
            uriVideo = MidiaUtils.salvarVideoEmCache(context, produtoId, videoUiState.bytes)
        }
    }

    Column {
        Text("Vídeo do produto", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val uri = uriVideo
        when {
            uri != null -> PlayerVideo(
                uri = uri,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
            videoUiState is VideoUiState.Erro -> Text(
                "Não foi possível carregar o vídeo. Verifique sua conexão.",
                color = MaterialTheme.colorScheme.error
            )
            else -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun CardSecao(titulo: String, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            conteudo()
        }
    }
}

/** Mostra a nota em estrelas; com [onNotaChange] as estrelas viram seletor */
@Composable
private fun Estrelas(
    nota: Int,
    tamanho: androidx.compose.ui.unit.Dp,
    onNotaChange: ((Int) -> Unit)? = null
) {
    Row {
        (1..5).forEach { valor ->
            val preenchida = valor <= nota
            val modificador = Modifier.size(tamanho).let {
                if (onNotaChange != null) it.clickable { onNotaChange(valor) } else it
            }
            Icon(
                if (preenchida) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (onNotaChange != null) "Nota $valor" else null,
                tint = if (preenchida) CorEstrela else MaterialTheme.colorScheme.outline,
                modifier = modificador
            )
        }
    }
}

@Composable
private fun AvaliacaoCard(avaliacao: AvaliacaoProduto, ehMinha: Boolean) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Estrelas(nota = avaliacao.nota, tamanho = 18.dp)
                if (ehMinha) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Sua avaliação",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (avaliacao.comentario.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(avaliacao.comentario, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
