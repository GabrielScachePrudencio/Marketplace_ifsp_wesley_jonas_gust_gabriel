package com.example.marketplace.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.PontoReferenciaViewModel
import com.example.marketplace.controller.PontoReferenciaViewModelFactory
import com.example.marketplace.model.PontoReferencia
import com.example.marketplace.model.Venda

@Composable
fun EscolherPontoOrigemDialog(
    negocianteId: String,
    venda: Venda,
    onConfirmar: (PontoReferencia) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: PontoReferenciaViewModel = viewModel(
        factory = PontoReferenciaViewModelFactory(context)
    )

    LaunchedEffect(negocianteId) {
        viewModel.carregarPontosDoNegociante(negocianteId)
    }

    val pontos by viewModel.pontos.collectAsState()
    var selecionado by remember { mutableStateOf<PontoReferencia?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Marcar pronto para entrega") },
        text = {
            Column {
                Text("De qual ponto essa entrega vai sair?")
                Spacer(modifier = Modifier.height(8.dp))

                if (pontos.isEmpty()) {
                    Text("Você ainda não cadastrou nenhum ponto de referência.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(pontos) { ponto ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selecionado?.id == ponto.id,
                                    onClick = { selecionado = ponto }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(ponto.nome, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${ponto.rua}, ${ponto.numero}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selecionado?.let(onConfirmar) },
                enabled = selecionado != null
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}