package com.example.marketplace.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.CriarProdutoUiState
import com.example.marketplace.controller.ProdutoListViewModel
import com.example.marketplace.controller.ProdutoListViewModelFactory

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

    val uiState by viewModel.criarUiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is CriarProdutoUiState.Sucesso) {
            viewModel.resetarCriacao()
            onCriado()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Criar Produto", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = titulo,
            onValueChange = { titulo = it },
            label = { Text("Título") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = descricao,
            onValueChange = { descricao = it },
            label = { Text("Descrição") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = categoria,
            onValueChange = { categoria = it },
            label = { Text("Categoria") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = preco,
            onValueChange = { input -> if (input.all { it.isDigit() || it == '.' }) preco = input },
            label = { Text("Preço") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = quantidade,
            onValueChange = { input -> if (input.all { it.isDigit() }) quantidade = input },
            label = { Text("Quantidade") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        if (uiState is CriarProdutoUiState.Erro) {
            Text(
                (uiState as CriarProdutoUiState.Erro).mensagem,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                viewModel.criarProduto(
                    vendedorId = vendedorId,
                    titulo = titulo,
                    descricao = descricao,
                    categoria = categoria,
                    preco = preco.toDoubleOrNull() ?: 0.0,
                    quantidade = quantidade.toIntOrNull() ?: 0
                )
            },
            enabled = uiState !is CriarProdutoUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState is CriarProdutoUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Criar produto")
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onVoltar) {
            Text("Voltar")
        }
    }
}
