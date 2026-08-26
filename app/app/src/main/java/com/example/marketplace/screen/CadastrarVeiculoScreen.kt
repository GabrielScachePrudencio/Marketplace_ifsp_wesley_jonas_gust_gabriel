package com.example.marketplace.screen


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.VeiculoUiState
import com.example.marketplace.controller.VeiculoViewModel
import com.example.marketplace.controller.VeiculoViewModelFactory

@Composable
fun CadastrarVeiculoScreen(
    motoristaId: String,
    onSucesso: () -> Unit,
    onVoltar: () -> Unit
) {

    val context = LocalContext.current

    val viewModel: VeiculoViewModel = viewModel(
        factory = VeiculoViewModelFactory(context)
    )

    var tipo by remember {
        mutableStateOf("")
    }

    var marca by remember {
        mutableStateOf("")
    }

    var modelo by remember {
        mutableStateOf("")
    }

    var ano by remember {
        mutableStateOf("")
    }

    var placa by remember {
        mutableStateOf("")
    }

    var cor by remember {
        mutableStateOf("")
    }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is VeiculoUiState.Sucesso) {
            onSucesso()
            viewModel.resetar()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {

        Text(
            text = "Cadastrar veículo",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        OutlinedTextField(
            value = tipo,
            onValueChange = {
                tipo = it
            },
            label = {
                Text("Tipo")
            },
            placeholder = {
                Text("Carro, moto, caminhão...")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        OutlinedTextField(
            value = marca,
            onValueChange = {
                marca = it
            },
            label = {
                Text("Marca")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        OutlinedTextField(
            value = modelo,
            onValueChange = {
                modelo = it
            },
            label = {
                Text("Modelo")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        OutlinedTextField(
            value = ano,
            onValueChange = { input ->

                if (
                    input.all { it.isDigit() } &&
                    input.length <= 4
                ) {
                    ano = input
                }
            },
            label = {
                Text("Ano")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        OutlinedTextField(
            value = placa,
            onValueChange = { input ->

                if (input.length <= 7) {
                    placa = input.uppercase()
                }
            },
            label = {
                Text("Placa")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        OutlinedTextField(
            value = cor,
            onValueChange = {
                cor = it
            },
            label = {
                Text("Cor")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        if (uiState is VeiculoUiState.Erro) {

            Text(
                text = (
                        uiState as VeiculoUiState.Erro
                        ).mensagem,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )
        }

        Button(
            onClick = {

                viewModel.cadastrarVeiculo(
                    motoristaId = motoristaId,
                    tipo = tipo,
                    marca = marca,
                    modelo = modelo,
                    ano = ano.toIntOrNull() ?: 0,
                    placa = placa,
                    cor = cor
                )
            },
            enabled = uiState !is VeiculoUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {

            if (uiState is VeiculoUiState.Loading) {

                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp)
                )

            } else {

                Text("Cadastrar veículo")
            }
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Button(
            onClick = onVoltar,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Voltar")
        }
    }
}
