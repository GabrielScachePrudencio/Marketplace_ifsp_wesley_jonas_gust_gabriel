package com.example.marketplace.screen

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
import com.example.marketplace.controller.ProdutoListViewModel
import com.example.marketplace.controller.ProdutoListViewModelFactory
import com.example.marketplace.model.Produto
import com.example.marketplace.model.Usuario
import com.example.marketplace.util.AvatarUsuario
import com.example.marketplace.util.CampoBusca
import com.example.marketplace.util.ImagemProduto
import com.example.marketplace.util.correspondeBusca
import com.example.marketplace.util.normalizarBusca

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
    onPontosReferencia: () -> Unit = {},
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ProdutoListViewModel = viewModel(
        factory = ProdutoListViewModelFactory(context)
    )
    val todosProdutos by viewModel.produtos.collectAsState()

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarUsuario(
                            fotoBase64 = usuario.fotoPerfil,
                            nome = usuario.nome,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("MarketPlace IFSP", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Olá, ${usuario.nome.substringBefore(" ")}",
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
