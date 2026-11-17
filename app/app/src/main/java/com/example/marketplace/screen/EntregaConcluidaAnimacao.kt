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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EntregaConcluidaAnimacaoDialog(
    visivel: Boolean,
    vendaId: String,
    produtoTitulo: String = "",
    onDismiss: () -> Unit
) {
    if (!visivel) return

    val corSucesso = Color(0xFF2E7D32)
    val corSucessoClara = Color(0xFFE8F5E9)

    val escalaCirculo = remember { Animatable(0f) }
    val progressoArco = remember { Animatable(0f) }
    val progressoCheck = remember { Animatable(0f) }
    var mostrarTexto by remember { mutableStateOf(false) }

    val transicaoPulso = rememberInfiniteTransition(label = "pulsoEntrega")
    val escalaPulso by transicaoPulso.animateFloat(
        initialValue = 1f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "escalaPulso"
    )

    LaunchedEffect(visivel) {
        escalaCirculo.snapTo(0f)
        progressoArco.snapTo(0f)
        progressoCheck.snapTo(0f)
        mostrarTexto = false

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
            animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
        )
        mostrarTexto = true
        progressoCheck.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 420, easing = LinearEasing)
        )

        // Fecha automaticamente após alguns segundos caso o usuário não clique antes
        delay(4500)
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(128.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Anel pulsante externo
                    Box(
                        modifier = Modifier
                            .size(116.dp)
                            .scale(escalaPulso * escalaCirculo.value)
                            .background(
                                color = corSucesso.copy(alpha = 0.14f),
                                shape = CircleShape
                            )
                    )

                    // Círculo de fundo
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .scale(escalaCirculo.value)
                            .background(
                                color = corSucessoClara,
                                shape = CircleShape
                            )
                    )

                    // Arco + Checkmark desenhados progressivamente
                    Canvas(
                        modifier = Modifier
                            .size(92.dp)
                            .scale(escalaCirculo.value)
                    ) {
                        val strokeWidth = 6.dp.toPx()
                        drawArc(
                            color = corSucesso,
                            startAngle = -90f,
                            sweepAngle = 360f * progressoArco.value,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        val p = progressoCheck.value
                        if (p > 0f) {
                            val start = Offset(size.width * 0.28f, size.height * 0.52f)
                            val mid = Offset(size.width * 0.44f, size.height * 0.68f)
                            val end = Offset(size.width * 0.74f, size.height * 0.34f)

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
                    visible = mostrarTexto,
                    enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.9f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = corSucessoClara,
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = corSucesso,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "REGISTRADO NO SISTEMA COMO ENTREGUE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = corSucesso
                                )
                            }
                        }

                        Text(
                            text = "Entrega Comprovada!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val descricaoPedido = if (produtoTitulo.isNotBlank()) {
                            "\"$produtoTitulo\" (#${vendaId.take(8)})"
                        } else {
                            "Pedido #${vendaId.take(8)}"
                        }

                        Text(
                            text = "A foto do comprovante foi enviada com sucesso e o $descricaoPedido já consta no sistema com o status ENTREGUE.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    tint = corSucesso,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Comprovante fotográfico + GPS salvos ✓",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = corSucesso)
                        ) {
                            Text("Concluir", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
