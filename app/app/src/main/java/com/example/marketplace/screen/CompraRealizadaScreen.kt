package com.example.marketplace.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.marketplace.util.ImagemProduto
import java.time.format.DateTimeFormatter

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

    val escalaIcone = remember { Animatable(0f) }
    LaunchedEffect(venda.id) {
        escalaIcone.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
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
            // ==================== CABEÇALHO DE SUCESSO ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = corSucessoClara)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .scale(escalaIcone.value)
                            .background(corSucesso, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Compra confirmada",
                            tint = Color.White,
                            modifier = Modifier.size(52.dp)
                        )
                    }

                    Text(
                        text = "Compra Realizada com Sucesso!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = corSucesso,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Seu pagamento foi confirmado e o pedido #${venda.id.take(8)} já foi enviado para a loja preparar a entrega.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1B5E20),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ==================== DETALHES DO PEDIDO ====================
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

            // ==================== BOTÕES DE NAVEGAÇÃO ====================
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
