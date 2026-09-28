package com.example.marketplace.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.PontoReferenciaUiState
import com.example.marketplace.controller.PontoReferenciaViewModel
import com.example.marketplace.controller.PontoReferenciaViewModelFactory
import com.example.marketplace.model.PontoReferencia
import com.example.marketplace.model.Usuario

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PontosReferenciaScreen(
    usuario: Usuario,
    onVoltar: () -> Unit,
    onCriarPonto: () -> Unit,
    onEditarPonto: (PontoReferencia) -> Unit
) {
    val context = LocalContext.current
    val viewModel: PontoReferenciaViewModel = viewModel(
        factory = PontoReferenciaViewModelFactory(context)
    )

    LaunchedEffect(usuario.uid) {
        viewModel.carregarPontosDoNegociante(usuario.uid)
    }

    val pontos by viewModel.pontos.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meus Pontos de Referência") },
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
                "Cadastre os locais de onde você envia suas entregas",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onCriarPonto,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Novo Ponto de Referência")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (pontos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nenhum ponto de referência cadastrado",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pontos) { ponto ->
                        PontoReferenciaCard(
                            ponto = ponto,
                            onEditar = { onEditarPonto(ponto) },
                            onExcluir = { viewModel.excluirPonto(ponto) }
                        )
                    }
                }
            }

            when (uiState) {
                is PontoReferenciaUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is PontoReferenciaUiState.Erro -> {
                    val erro = (uiState as PontoReferenciaUiState.Erro).mensagem
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
                else -> {}
            }
        }
    }
}

@Composable
fun PontoReferenciaCard(
    ponto: PontoReferencia,
    onEditar: () -> Unit,
    onExcluir: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    ponto.nome,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (ponto.principal) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "Principal",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                ponto.tipoEnum.name.replace("_", " "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "${ponto.rua}, ${ponto.numero}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "${ponto.cidade} - ${ponto.estado}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (ponto.cep.isNotBlank()) {
                Text(
                    "CEP: ${ponto.cep}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEditar) {
                    Text("Editar")
                }
                TextButton(
                    onClick = onExcluir,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Excluir")
                }
            }
        }
    }
}
