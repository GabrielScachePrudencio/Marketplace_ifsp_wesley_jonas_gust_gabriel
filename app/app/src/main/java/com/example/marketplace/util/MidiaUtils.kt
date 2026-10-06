package com.example.marketplace.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.marketplace.domain.ProdutoRegras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object MidiaUtils {

    /** Cria um arquivo vazio no cache e devolve a Uri para a câmera escrever nele */
    fun criarUriTemporaria(context: Context, extensao: String): Uri {
        val pasta = File(context.cacheDir, "midia").apply { mkdirs() }
        val arquivo = File.createTempFile("captura_", ".$extensao", pasta)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", arquivo)
    }

    fun tamanhoEmBytes(context: Context, uri: Uri): Long? = try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
    } catch (e: Exception) {
        null
    }

    fun lerBytes(context: Context, uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        null
    }

    /** Grava o vídeo baixado do Firestore no cache para o VideoView conseguir reproduzir */
    suspend fun salvarVideoEmCache(context: Context, nome: String, bytes: ByteArray): Uri =
        withContext(Dispatchers.IO) {
            val pasta = File(context.cacheDir, "midia").apply { mkdirs() }
            val arquivo = File(pasta, "video_$nome.mp4")
            if (!arquivo.exists() || arquivo.length() != bytes.size.toLong()) {
                arquivo.writeBytes(bytes)
            }
            Uri.fromFile(arquivo)
        }
}

/** Abre a câmera para gravar vídeo já com limite de duração e tamanho */
class GravarVideoContract : ActivityResultContracts.CaptureVideo() {
    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input).apply {
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, ProdutoRegras.DURACAO_MAXIMA_VIDEO_SEGUNDOS)
            putExtra(MediaStore.EXTRA_SIZE_LIMIT, ProdutoRegras.TAMANHO_MAXIMO_VIDEO_BYTES.toLong())
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
        }
}

/** Player simples com controles nativos (play/pause/seek) */
@Composable
fun PlayerVideo(uri: Uri, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
        factory = { ctx ->
            VideoView(ctx).apply {
                val controles = MediaController(ctx)
                controles.setAnchorView(this)
                setMediaController(controles)
                setOnPreparedListener { player ->
                    player.isLooping = false
                    seekTo(1) // mostra o primeiro quadro em vez de tela preta
                }
            }
        },
        update = { videoView ->
            if (videoView.tag != uri) {
                videoView.tag = uri
                videoView.setVideoURI(uri)
            }
        },
        onRelease = { it.stopPlayback() }
    )
}

/**
 * Modal em tela cheia para ver as fotos do produto: deslize para trocar,
 * pinça ou toque duplo para dar zoom.
 */
@Composable
fun GaleriaFotosDialog(
    imagens: List<String>,
    indiceInicial: Int,
    onDismiss: () -> Unit
) {
    if (imagens.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = indiceInicial.coerceIn(0, imagens.lastIndex),
        pageCount = { imagens.size }
    )
    val miniaturasState = rememberLazyListState()
    val escopo = rememberCoroutineScope()
    var zoomAtivo by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        zoomAtivo = false
        miniaturasState.animateScrollToItem(pagerState.currentPage)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !zoomAtivo,
                modifier = Modifier.fillMaxSize()
            ) { pagina ->
                FotoComZoom(
                    base64 = imagens[pagina],
                    onZoomAlterado = { ativo -> if (pagina == pagerState.currentPage) zoomAtivo = ativo }
                )
            }

            // Topo: contador e fechar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        "${pagerState.currentPage + 1} / ${imagens.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                }
            }

            // Rodapé: miniaturas
            if (imagens.size > 1) {
                LazyRow(
                    state = miniaturasState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                ) {
                    itemsIndexed(imagens) { indice, base64 ->
                        val selecionada = indice == pagerState.currentPage
                        ImagemProduto(
                            fotoBase64 = base64,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = 2.dp,
                                    color = if (selecionada) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .graphicsLayer { alpha = if (selecionada) 1f else 0.6f }
                                .clickable { escopo.launch { pagerState.animateScrollToPage(indice) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FotoComZoom(base64: String, onZoomAlterado: (Boolean) -> Unit) {
    var escala by remember { mutableFloatStateOf(1f) }
    var deslocamento by remember { mutableStateOf(Offset.Zero) }

    fun aplicarEscala(nova: Float) {
        escala = nova.coerceIn(1f, 5f)
        if (escala == 1f) deslocamento = Offset.Zero
        onZoomAlterado(escala > 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { aplicarEscala(if (escala > 1f) 1f else 2.5f) })
            }
            .pointerInput(Unit) {
                // Só consome o gesto com dois dedos ou com zoom ativo, para o pager continuar deslizando
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val evento = awaitPointerEvent()
                        if (evento.changes.size > 1 || escala > 1f) {
                            aplicarEscala(escala * evento.calculateZoom())
                            if (escala > 1f) deslocamento += evento.calculatePan()
                            evento.changes.forEach { it.consume() }
                        }
                    } while (evento.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        ImagemProduto(
            fotoBase64 = base64,
            contentScale = ContentScale.Fit,
            placeholderBackground = Color.Black,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = escala
                    scaleY = escala
                    translationX = deslocamento.x
                    translationY = deslocamento.y
                }
        )
    }
}
