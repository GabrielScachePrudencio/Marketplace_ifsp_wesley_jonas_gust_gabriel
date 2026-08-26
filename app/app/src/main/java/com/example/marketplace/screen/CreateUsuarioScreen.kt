package com.example.marketplace.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.CadastroUiState
import com.example.marketplace.controller.CadastroViewModel
import com.example.marketplace.controller.CadastroViewModelFactory
import com.example.marketplace.model.Usuario

@Composable
fun CreateUsuarioScreen(
    onCadastroSuccess: (Usuario) -> Unit,
    onVoltarLogin: () -> Unit
) {
    val context = LocalContext.current

    val viewModel: CadastroViewModel = viewModel(
        factory = CadastroViewModelFactory(context)
    )

    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var cpf by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var confirmarSenha by remember { mutableStateOf("") }

    // Mantendo String, como já está no seu projeto
    var perfil by remember {
        mutableStateOf("comprador")
    }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is CadastroUiState.Sucesso) {
            onCadastroSuccess(
                (uiState as CadastroUiState.Sucesso).usuario
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            "Criar Conta",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = nome,
            onValueChange = { nome = it },
            label = { Text("Nome") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = cpf,
            onValueChange = { input ->
                if (
                    input.length <= 11 &&
                    input.all { it.isDigit() }
                ) {
                    cpf = input
                }
            },
            label = { Text("CPF") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = senha,
            onValueChange = { senha = it },
            label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = confirmarSenha,
            onValueChange = { confirmarSenha = it },
            label = { Text("Confirmar senha") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Tipo de usuário",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            FilterChip(
                selected = perfil == "comprador",
                onClick = {
                    perfil = "comprador"
                },
                label = {
                    Text("Comprador")
                }
            )

            FilterChip(
                selected = perfil == "motorista",
                onClick = {
                    perfil = "motorista"
                },
                label = {
                    Text("Motorista")
                }
            )

            FilterChip(
                selected = perfil == "negociador",
                onClick = {
                    perfil = "negociador"
                },
                label = {
                    Text("Negociador")
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        if (uiState is CadastroUiState.Erro) {

            Text(
                text = (uiState as CadastroUiState.Erro).mensagem,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                viewModel.cadastrar(
                    nome = nome,
                    email = email,
                    senha = senha,
                    confirmarSenha = confirmarSenha,
                    perfil = perfil,
                    cpf = cpf
                )
            },
            enabled = uiState !is CadastroUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {

            if (uiState is CadastroUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text("Cadastrar")
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = onVoltarLogin
        ) {
            Text("Já tenho conta, fazer login")
        }
    }
}
