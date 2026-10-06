package com.example.marketplace.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.CriarProdutoUiState
import com.example.marketplace.controller.ProdutoListViewModel
import com.example.marketplace.controller.ProdutoListViewModelFactory
import com.example.marketplace.domain.ProdutoRegras
import com.example.marketplace.util.GaleriaFotosDialog
import com.example.marketplace.util.GravarVideoContract
import com.example.marketplace.util.ImageUtils
import com.example.marketplace.util.ImagemProduto
import com.example.marketplace.util.MidiaUtils
import com.example.marketplace.util.PlayerVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAXIMO_FOTOS = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CriarProdutoScreen(
    vendedorId: String,
    onCriado: () -> Unit,
    onVoltar: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ProdutoListViewModel = viewModel(
        factory = ProdutoListViewModelFactory(context)
    )

    var titulo by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("") }
    var preco by remember { mutableStateOf("") }
    var quantidade by remember { mutableStateOf("") }
    var imagensBase64 by remember { mutableStateOf<List<String>>(emptyList()) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var erroMidia by remember { mutableStateOf<String?>(null) }
    var indiceGaleria by remember { mutableStateOf<Int?>(null) }

    // Uri temporária onde a câmera grava a foto/vídeo capturado
    // (saveable: a Activity pode ser recriada enquanto a câmera está aberta)
    var uriCaptura by rememberSaveable { mutableStateOf<Uri?>(null) }
    val escopo = rememberCoroutineScope()

    fun adicionarFotos(uris: List<Uri>) {
        val novasFotos = uris.mapNotNull { uri ->
            ImageUtils.uriParaBase64(context, uri, maxDimensao = 600, qualidade = 65)
        }
        imagensBase64 = (imagensBase64 + novasFotos).take(MAXIMO_FOTOS)
    }

    fun definirVideo(uri: Uri) {
        val tamanho = MidiaUtils.tamanhoEmBytes(context, uri)
        if (tamanho == null || tamanho <= 0L) {
            erroMidia = "Não foi possível ler o vídeo"
        } else if (tamanho > ProdutoRegras.TAMANHO_MAXIMO_VIDEO_BYTES) {
            erroMidia = "Vídeo muito grande (máx. ${ProdutoRegras.TAMANHO_MAXIMO_VIDEO_BYTES / (1024 * 1024)} MB). Grave um vídeo mais curto."
        } else {
            erroMidia = null
            videoUri = uri
        }
    }

    val seletorFotos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAXIMO_FOTOS)
    ) { uris: List<Uri> -> if (uris.isNotEmpty()) adicionarFotos(uris) }

    val tirarFoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { sucesso -> if (sucesso) uriCaptura?.let { adicionarFotos(listOf(it)) } }

    val seletorVideo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> if (uri != null) definirVideo(uri) }

    val gravarVideo = rememberLauncherForActivityResult(
        contract = GravarVideoContract()
    ) { sucesso -> if (sucesso) uriCaptura?.let { definirVideo(it) } }

    val uiState by viewModel.criarUiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is CriarProdutoUiState.Sucesso) {
            viewModel.resetarCriacao()
            onCriado()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Novo produto") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ===== FOTOS =====
            CardFormulario(titulo = "Fotos (${imagensBase64.size}/$MAXIMO_FOTOS)") {
                if (imagensBase64.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(imagensBase64) { index, base64 ->
                            Box(modifier = Modifier.size(96.dp)) {
                                ImagemProduto(
                                    fotoBase64 = base64,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { indiceGaleria = index }
                                )
                                BotaoRemover(
                                    descricao = "Remover foto",
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                ) {
                                    imagensBase64 = imagensBase64.filterIndexed { i, _ -> i != index }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (imagensBase64.size < MAXIMO_FOTOS) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotaoMidia(Icons.Default.PhotoCamera, "Tirar foto", Modifier.weight(1f)) {
                            val uri = MidiaUtils.criarUriTemporaria(context, "jpg")
                            uriCaptura = uri
                            tirarFoto.launch(uri)
                        }
                        BotaoMidia(Icons.Default.PhotoLibrary, "Galeria", Modifier.weight(1f)) {
                            seletorFotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    }
                }
            }

            // ===== VÍDEO =====
            CardFormulario(titulo = "Vídeo (opcional)") {
                val uri = videoUri
                if (uri != null) {
                    Box {
                        PlayerVideo(uri = uri, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                        BotaoRemover(
                            descricao = "Remover vídeo",
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) { videoUri = null }
                    }
                } else {
                    Text(
                        "Até ${ProdutoRegras.DURACAO_MAXIMA_VIDEO_SEGUNDOS}s ou ${ProdutoRegras.TAMANHO_MAXIMO_VIDEO_BYTES / (1024 * 1024)} MB. É preciso estar conectado para enviar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotaoMidia(Icons.Default.Videocam, "Gravar vídeo", Modifier.weight(1f)) {
                            val novaUri = MidiaUtils.criarUriTemporaria(context, "mp4")
                            uriCaptura = novaUri
                            gravarVideo.launch(novaUri)
                        }
                        BotaoMidia(Icons.Default.VideoLibrary, "Galeria", Modifier.weight(1f)) {
                            seletorVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        }
                    }
                }
                erroMidia?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            // ===== DADOS =====
            CardFormulario(titulo = "Informações") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = titulo,
                        onValueChange = { titulo = it },
                        label = { Text("Título") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = descricao,
                        onValueChange = { descricao = it },
                        label = { Text("Descrição") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = categoria,
                        onValueChange = { categoria = it },
                        label = { Text("Categoria") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = preco,
                            onValueChange = { input -> if (input.all { it.isDigit() || it == '.' }) preco = input },
                            label = { Text("Preço") },
                            prefix = { Text("R$ ") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = quantidade,
                            onValueChange = { input -> if (input.all { it.isDigit() }) quantidade = input },
                            label = { Text("Estoque") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (uiState is CriarProdutoUiState.Erro) {
                Text(
                    (uiState as CriarProdutoUiState.Erro).mensagem,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    escopo.launch {
                    val video = videoUri?.let { uri ->
                        withContext(Dispatchers.IO) { MidiaUtils.lerBytes(context, uri) }
                    }
                    if (videoUri != null && video == null) {
                        erroMidia = "Não foi possível ler o vídeo"
                        return@launch
                    }
                    viewModel.criarProduto(
                        vendedorId = vendedorId,
                        titulo = titulo,
                        descricao = descricao,
                        categoria = categoria,
                        preco = preco.toDoubleOrNull() ?: 0.0,
                        quantidade = quantidade.toIntOrNull() ?: 0,
                        imagens = imagensBase64,
                        video = video
                    )
                    }
                },
                enabled = uiState !is CriarProdutoUiState.Loading,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (uiState is CriarProdutoUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    if (videoUri != null) {
                        Spacer(Modifier.width(12.dp))
                        Text("Enviando vídeo...")
                    }
                } else {
                    Text("Publicar produto")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    indiceGaleria?.let { indice ->
        GaleriaFotosDialog(
            imagens = imagensBase64,
            indiceInicial = indice,
            onDismiss = { indiceGaleria = null }
        )
    }
}

@Composable
private fun CardFormulario(titulo: String, conteudo: @Composable ColumnScope.() -> Unit) {
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

@Composable
private fun BotaoMidia(icone: ImageVector, texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(icone, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(texto)
    }
}

@Composable
private fun BotaoRemover(descricao: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(26.dp)
            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
    ) {
        Icon(Icons.Default.Close, contentDescription = descricao, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}
