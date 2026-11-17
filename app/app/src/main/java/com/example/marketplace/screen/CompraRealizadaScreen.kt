package com.example.marketplace.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.marketplace.util.ImagemProduto
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompraRealizadaScreen(
    resumo: ResumoCompraConfirmada,
    onVerPedido: (String) -> Unit,
    onIrMinhasCompras: () -> Unit,
    onContinuarComprando: () -> Unit
) {
    val venda = resumo.venda
    val corSucesso = Color(0xFF2E7D32)
    val corSucessoClara = Color(0xFFE8F5E9)
    val corDestaqueDourado = Color(0xFFF5A623)

    // Estados da animação de confirmação de compra
    val escalaCirculo = remember { Animatable(0f) }
    val progressoArco = remember { Animatable(0f) }
    val progressoCheck = remember { Animatable(0f) }
    val progressoParticulas = remember { Animatable(0f) }

    var mostrarTitulo by remember { mutableStateOf(false) }
    var mostrarResumoCard by remember { mutableStateOf(false) }
    var mostrarBotoes by remember { mutableStateOf(false) }

    val transicaoPulso = rememberInfiniteTransition(label = "pulsoCompra")
    val escalaPulso by transicaoPulso.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "escalaPulsoCompra"
    )

    LaunchedEffect(venda.id) {
        escalaCirculo.snapTo(0f)
        progressoArco.snapTo(0f)
        progressoCheck.snapTo(0f)
        progressoParticulas.snapTo(0f)
        mostrarTitulo = false
        mostrarResumoCard = false
        mostrarBotoes = false

        launch {
            escalaCirculo.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        progressoArco.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
        )
        mostrarTitulo = true
        launch {
            progressoParticulas.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            )
        }
        progressoCheck.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400, easing = LinearEasing)
        )
        delay(120)
        mostrarResumoCard = true
        delay(150)
        mostrarBotoes = true
    }

    val dataFormatada = remember(venda.dataCriacao) {
        try {
            venda.dataCriacao.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"))
        } catch (_: Exception) {
            ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comprovante da Compra", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==================== CABEÇALHO ANIMADO DE COMPRA CONFIRMADA ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = corSucessoClara),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(144.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Halo pulsante externo
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .scale(escalaPulso * escalaCirculo.value)
                                .background(
                                    color = corSucesso.copy(alpha = 0.14f),
                                    shape = CircleShape
                                )
                        )

                        // Círculo interno
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .scale(escalaCirculo.value)
                                .background(
                                    color = Color.White,
                                    shape = CircleShape
                                )
                        )

                        // Partículas de celebração + Arco 360° + Checkmark desenhado em Canvas
                        Canvas(
                            modifier = Modifier
                                .size(144.dp)
                                .scale(escalaCirculo.value)
                        ) {
                            val centro = Offset(size.width / 2f, size.height / 2f)
                            val raioCirculo = 46.dp.toPx()

                            // Partículas radiais de celebração
                            val part = progressoParticulas.value
                            if (part > 0f && part < 1f) {
                                val alphaParticula = (1f - part).coerceIn(0f, 1f)
                                val totalParticulas = 12
                                for (i in 0 until totalParticulas) {
                                    val anguloRad = Math.toRadians((i * (360.0 / totalParticulas)))
                                    val distInicial = raioCirculo + 6.dp.toPx()
                                    val distAtual = distInicial + (22.dp.toPx() * part)
                                    val px = centro.x + (cos(anguloRad) * distAtual).toFloat()
                                    val py = centro.y + (sin(anguloRad) * distAtual).toFloat()
                                    val corP = if (i % 2 == 0) corSucesso else corDestaqueDourado
                                    drawCircle(
                                        color = corP.copy(alpha = alphaParticula),
                                        radius = (4.dp.toPx() * (1f - part * 0.4f)),
                                        center = Offset(px, py)
                                    )
                                }
                            }

                            // Arco circular ao redor do selo
                            val tamanhoArco = raioCirculo * 2f
                            val topLeftArco = Offset(centro.x - raioCirculo, centro.y - raioCirculo)
                            drawArc(
                                color = corSucesso,
                                startAngle = -90f,
                                sweepAngle = 360f * progressoArco.value,
                                useCenter = false,
                                topLeft = topLeftArco,
                                size = androidx.compose.ui.geometry.Size(tamanhoArco, tamanhoArco),
                                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Checkmark progressivo
                            val p = progressoCheck.value
                            if (p > 0f) {
                                val start = Offset(centro.x - raioCirculo * 0.42f, centro.y + raioCirculo * 0.04f)
                                val mid = Offset(centro.x - raioCirculo * 0.10f, centro.y + raioCirculo * 0.36f)
                                val end = Offset(centro.x + raioCirculo * 0.46f, centro.y - raioCirculo * 0.30f)

                                val path = Path().apply {
                                    moveTo(start.x, start.y)
                                    val firstLeg = (p / 0.45f).coerceIn(0f, 1f)
                                    lineTo(
                                        start.x + (mid.x - start.x) * firstLeg,
                                        start.y + (mid.y - start.y) * firstLeg
                                    )
                                    if (p > 0.45f) {
                                        val secondLeg = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
                                        lineTo(
                                            mid.x + (end.x - mid.x) * secondLeg,
                                            mid.y + (end.y - mid.y) * secondLeg
                                        )
                                    }
                                }

                                drawPath(
                                    path = path,
                                    color = corSucesso,
                                    style = Stroke(
                                        width = 6.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = mostrarTitulo,
                        enter = fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.9f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = corSucesso,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "PAGAMENTO APROVADO • PEDIDO REGISTRADO",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = corSucesso
                                    )
                                }
                            }

                            Text(
                                text = "Compra Realizada com Sucesso!",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = corSucesso,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Seu pagamento via ${resumo.formaPagamentoDescricao} foi confirmado e o pedido #${venda.id.take(8)} já foi enviado para a loja.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF1B5E20),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ==================== DETALHES DO PEDIDO (ENTRADA ANIMADA) ====================
            AnimatedVisibility(
                visible = mostrarResumoCard,
                enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 4 }
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Pedido #${venda.id.take(8)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (dataFormatada.isNotBlank()) {
                                    Text(
                                        text = dataFormatada,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            StatusEntregaBadge(status = venda.statusEntrega)
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ImagemProduto(
                                fotoBase64 = resumo.produtoImagemBase64,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = venda.produtoTitulo,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Quantidade: ${venda.quantidade} unidade(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Total pago: R$ %.2f".format(venda.valorTotal),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Default.CreditCard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Forma de pagamento",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    resumo.formaPagamentoDescricao,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Endereço de entrega",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    resumo.enderecoEntrega,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Default.LocalShipping,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Próximos passos",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Assim que o motorista iniciar o transporte, você verá um aviso com o tempo estimado na tela inicial e poderá acompanhar no mapa.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ==================== BOTÕES DE NAVEGAÇÃO (ENTRADA ANIMADA) ====================
            AnimatedVisibility(
                visible = mostrarBotoes,
                enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 3 }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onVerPedido(venda.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(Icons.Default.ShoppingBag, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ver Detalhes deste Pedido")
                    }

                    FilledTonalButton(
                        onClick = onIrMinhasCompras,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ir para Minhas Compras")
                    }

                    OutlinedButton(
                        onClick = onContinuarComprando,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Continuar Comprando")
                    }
                }
            }
        }
    }
}
