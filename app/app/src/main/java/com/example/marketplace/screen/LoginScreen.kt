package com.example.marketplace.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.R
import com.example.marketplace.controller.LoginUiState
import com.example.marketplace.controller.LoginViewModel
import com.example.marketplace.model.Usuario


@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(),
    onLoginSuccess: (Usuario) -> Unit,
    onCriarConta: () -> Unit = {}
) {

    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Sucesso) {
            onLoginSuccess((uiState as LoginUiState.Sucesso).usuario)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_logo_marketplace),
            contentDescription = "Logo MarketPlace IFSP",
            modifier = Modifier.size(112.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "MarketPlace IFSP",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Compras e entregas na sua região",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = senha,
            onValueChange = { senha = it },
            label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        if (uiState is LoginUiState.Erro) {
            Text(
                (uiState as LoginUiState.Erro).mensagem,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = { viewModel.login(email, senha) },
            enabled = uiState !is LoginUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState is LoginUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Entrar")
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onCriarConta) {
            Text("Não tenho conta, criar agora")
        }
    }
}