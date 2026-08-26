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
import com.example.marketplace.controller.UsuarioViewModel
import com.example.marketplace.controller.UsuarioViewModelFactory
import com.example.marketplace.model.Usuario
import com.example.marketplace.model.Veiculo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMotoristaScreen(
    usuario: Usuario,
    veiculos: List<Veiculo>,
    onCadastrarVeiculo: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val usuarioViewModel: UsuarioViewModel = viewModel(
        factory = UsuarioViewModelFactory(context)
    )
    val negociantes by usuarioViewModel.negociantes.collectAsState()
    val vinculando by usuarioViewModel.vinculando.collectAsState()

    // Guarda localmente pra refletir na hora, já que "usuario" só
    // atualiza de verdade depois de um novo login/reload no MainActivity
    var negocianteIdAtual by remember { mutableStateOf(usuario.negocianteId) }

    LaunchedEffect(Unit) {
        usuarioViewModel.carregarNegociantes()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Área do Motorista") },
                actions = {
                    TextButton(onClick = onLogout) { Text("Sair") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCadastrarVeiculo) {
                Text("+")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Text("Olá, ${usuario.nome}!", style = MaterialTheme.typography.headlineSmall)

            // ------------------------------------------------
            // VÍNCULO COM NEGOCIANTE
            // ------------------------------------------------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Negociante parceiro", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    if (negocianteIdAtual == null) {
                        Text("Você ainda não está vinculado a nenhum negociante.")
                        Spacer(Modifier.height(8.dp))

                        if (negociantes.isEmpty()) {
                            Text(
                                "Nenhum negociante disponível no momento.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            negociantes.forEach { negociante ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(negociante.nome)
                                    Button(
                                        enabled = !vinculando,
                                        onClick = {
                                            usuarioViewModel.vincularNegociante(
                                                motoristaUid = usuario.uid,
                                                negocianteId = negociante.uid
                                            ) {
                                                negocianteIdAtual = negociante.uid
                                            }
                                        }
                                    ) {
                                        Text("Vincular")
                                    }
                                }
                            }
                        }
                    } else {
                        val nomeNegociante = negociantes
                            .find { it.uid == negocianteIdAtual }
                            ?.nome ?: negocianteIdAtual

                        Text("Vinculado a: $nomeNegociante")
                    }
                }
            }

            // ------------------------------------------------
            // VEÍCULOS
            // ------------------------------------------------
            Text("Meus veículos", style = MaterialTheme.typography.titleMedium)

            if (veiculos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nenhum veículo cadastrado ainda")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(veiculos, key = { it.id }) { veiculo ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(veiculo.modelo, style = MaterialTheme.typography.titleSmall)
                                Text(veiculo.placa, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}