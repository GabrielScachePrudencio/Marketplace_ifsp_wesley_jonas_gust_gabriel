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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.CriarProdutoUiState
import com.example.marketplace.controller.ProdutoListViewModel
import com.example.marketplace.controller.ProdutoListViewModelFactory
import com.example.marketplace.util.ImageUtils
import com.example.marketplace.util.ImagemProduto

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

    val multiplePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val novasFotos = uris.mapNotNull { uri ->
                ImageUtils.uriParaBase64(context, uri, maxDimensao = 600, qualidade = 65)
            }
            imagensBase64 = (imagensBase64 + novasFotos).take(4)
        }
    }

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
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Criar Produto", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))

        // Seção de fotos
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Fotos do Produto (${imagensBase64.size}/4)",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (imagensBase64.size < 4) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(88.dp)
                                .clickable {
                                    multiplePhotoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                        ) {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddAPhoto,
                                    contentDescription = "Adicionar foto",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Adicionar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                itemsIndexed(imagensBase64) { index, base64 ->
                    Box(modifier = Modifier.size(88.dp)) {
                        ImagemProduto(
                            fotoBase64 = base64,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                        )
                        IconButton(
                            onClick = {
                                imagensBase64 = imagensBase64.filterIndexed { i, _ -> i != index }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remover foto",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        OutlinedTextField(
            value = titulo,
            onValueChange = { titulo = it },
            label = { Text("Título") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = descricao,
            onValueChange = { descricao = it },
            label = { Text("Descrição") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = categoria,
            onValueChange = { categoria = it },
            label = { Text("Categoria") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = preco,
            onValueChange = { input -> if (input.all { it.isDigit() || it == '.' }) preco = input },
            label = { Text("Preço") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = quantidade,
            onValueChange = { input -> if (input.all { it.isDigit() }) quantidade = input },
            label = { Text("Quantidade") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        if (uiState is CriarProdutoUiState.Erro) {
            Text(
                (uiState as CriarProdutoUiState.Erro).mensagem,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                viewModel.criarProduto(
                    vendedorId = vendedorId,
                    titulo = titulo,
                    descricao = descricao,
                    categoria = categoria,
                    preco = preco.toDoubleOrNull() ?: 0.0,
                    quantidade = quantidade.toIntOrNull() ?: 0,
                    imagens = imagensBase64
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

        TextButton(onClick = onVoltar) {
            Text("Voltar")
        }
    }
}

