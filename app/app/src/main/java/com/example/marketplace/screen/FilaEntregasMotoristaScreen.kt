package com.example.marketplace.screen

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.FilaEntregasUiState
import com.example.marketplace.controller.FilaEntregasViewModel
import com.example.marketplace.controller.FilaEntregasViewModelFactory
import com.example.marketplace.model.RastreioEntrega
import com.example.marketplace.model.Usuario
import com.example.marketplace.service.LocationTrackingService
import com.example.marketplace.util.hasLocationPermission
import com.example.marketplace.util.hasPreciseLocationPermission

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilaEntregasMotoristaScreen(
    usuario: Usuario,
    onVoltar: () -> Unit,
    onSelecionarPedido: (String) -> Unit = {},
    onVerRota: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: FilaEntregasViewModel = viewModel(
        factory = FilaEntregasViewModelFactory(context)
    )

    LaunchedEffect(usuario.uid) {
        viewModel.carregarFilaDoMotorista(usuario.uid)
    }

    val fila by viewModel.fila.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Gerenciamento de permissões de localização
    var temLocalizacaoPrecisa by remember {
        mutableStateOf(hasPreciseLocationPermission(context))
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult (
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        temLocalizacaoPrecisa =
            permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                hasPreciseLocationPermission(context)
        if (!temLocalizacaoPrecisa && hasLocationPermission(context)) {
            Toast.makeText(
                context,
                "Para detectar a cerca de 10 m, permita a localização precisa do motorista.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Minha Fila de Entregas") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                "Organize a ordem das suas entregas",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (fila.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nenhuma entrega na fila",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(fila) { index, rastreio ->
                        FilaEntregaCard(
                            rastreio = rastreio,
                            motoristaId = usuario.uid,
                            posicao = index + 1,
                            onMoverParaCima = if (index > 0) {
                                { moverItem(viewModel, fila, index, index - 1) }
                            } else null,
                            onMoverParaBaixo = if (index < fila.size - 1) {
                                { moverItem(viewModel, fila, index, index + 1) }
                            } else null,
                            onSelecionarPedido = {
                                if (rastreio.vendaId.isNotBlank()) {
                                    onSelecionarPedido(rastreio.vendaId)
                                }
                            },
                            onVerRota = {
                                if (rastreio.vendaId.isNotBlank()) {
                                    onVerRota(rastreio.vendaId)
                                }
                            },
                            onIniciar = {
                                if (!temLocalizacaoPrecisa) {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                } else {
                                    viewModel.iniciarEntrega(rastreio.id, usuario.uid, index + 1) {
                                        // Iniciar serviço de rastreamento
                                        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
                                            action = LocationTrackingService.ACTION_START_TRACKING
                                            putExtra(LocationTrackingService.EXTRA_VENDA_ID, rastreio.vendaId)
                                        }
                                        androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
                                    }
                                }
                            },
                            onFinalizar = {
                                if (rastreio.vendaId.isNotBlank()) onVerRota(rastreio.vendaId)
                            }
                        )
                    }
                }
            }

            when (uiState) {
                is FilaEntregasUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is FilaEntregasUiState.Erro -> {
                    val erro = (uiState as FilaEntregasUiState.Erro).mensagem
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.resetar() }) {
                                Text("OK")
                            }
                        }
                    ) {
                        Text(erro)
                    }
                }
                is FilaEntregasUiState.Sucesso -> {
                    val msg = (uiState as FilaEntregasUiState.Sucesso).mensagem
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.resetar() }) {
                                Text("OK")
                            }
                        }
                    ) {
                        Text(msg)
                    }
                }
                else -> {}
            }
        }
    }
}

private fun moverItem(
    viewModel: FilaEntregasViewModel,
    fila: List<RastreioEntrega>,
    deIndex: Int,
    paraIndex: Int
) {
    val item = fila[deIndex]
    viewModel.atualizarPosicaoNaFila(item.id, paraIndex + 1)
}

@Composable
fun FilaEntregaCard(
    rastreio: RastreioEntrega,
    motoristaId: String,
    posicao: Int,
    onMoverParaCima: (() -> Unit)?,
    onMoverParaBaixo: (() -> Unit)?,
    onSelecionarPedido: () -> Unit = {},
    onVerRota: () -> Unit = {},
    onIniciar: () -> Unit,
    onFinalizar: () -> Unit
) {
    Card(
        onClick = onSelecionarPedido,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "#$posicao",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "Venda: ${rastreio.vendaId.take(8)}...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    rastreio.enderecoDestinoTexto,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (rastreio.iniciadoEm != null) {
                    Text(
                        "Em andamento",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row {
                    IconButton(
                        onClick = { onMoverParaCima?.invoke() },
                        enabled = onMoverParaCima != null,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Mover para cima",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { onMoverParaBaixo?.invoke() },
                        enabled = onMoverParaBaixo != null,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Mover para baixo",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (rastreio.iniciadoEm == null) {
                    ConfirmarRetiradaButton(
                        vendaId = rastreio.vendaId,
                        motoristaId = motoristaId,
                        textoBotao = "Coletar",
                        modifier = Modifier.width(92.dp).height(32.dp),
                        onConfirmado = onIniciar
                    )
                } else {
                    Button(
                        onClick = onFinalizar,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Entregar", style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedButton(
                    onClick = onVerRota,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Rota", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
