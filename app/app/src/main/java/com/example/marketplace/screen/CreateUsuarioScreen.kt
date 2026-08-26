package com.example.marketplace.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.CadastroUiState
import com.example.marketplace.controller.CadastroViewModel
import com.example.marketplace.controller.CadastroViewModelFactory

private val PERFIS = listOf("comprador", "motorista", "negociador")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateUsuarioScreen(
    onCadastroSuccess: () -> Unit,
    onVoltarLogin: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: CadastroViewModel = viewModel(
        factory = CadastroViewModelFactory(context)
    )
    val uiState by viewModel.uiState.collectAsState()

    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var confirmarSenha by remember { mutableStateOf("") }
    var cpf by remember { mutableStateOf("") }
    var perfil by remember { mutableStateOf(PERFIS[0]) }

    var rua by remember { mutableStateOf("") }
    var numero by remember { mutableStateOf("") }
    var cidade by remember { mutableStateOf("") }
    var estado by remember { mutableStateOf("") }
    var cep by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is CadastroUiState.Sucesso) {
            onCadastroSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Criar conta") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text("Dados pessoais", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = nome,
                onValueChange = { nome = it },
                label = { Text("Nome completo") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-mail") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = senha,
                onValueChange = { senha = it },
                label = { Text("Senha") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = confirmarSenha,
                onValueChange = { confirmarSenha = it },
                label = { Text("Confirmar senha") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = cpf,
                onValueChange = { cpf = it },
                label = { Text("CPF (somente números)") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Perfil", style = MaterialTheme.typography.titleMedium)

            Column {
                PERFIS.forEach { opcao ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = perfil == opcao,
                            onClick = { perfil = opcao }
                        )
                        Text(opcao.replaceFirstChar { it.uppercase() })
                    }
                }
            }

            Text("Endereço", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = rua,
                onValueChange = { rua = it },
                label = { Text("Rua") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = numero,
                    onValueChange = { numero = it },
                    label = { Text("Número") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = cep,
                    onValueChange = { cep = it },
                    label = { Text("CEP") },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = cidade,
                    onValueChange = { cidade = it },
                    label = { Text("Cidade") },
                    modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = estado,
                    onValueChange = { estado = it },
                    label = { Text("UF") },
                    modifier = Modifier.weight(1f)
                )
            }

            if (uiState is CadastroUiState.Erro) {
                Text(
                    text = (uiState as CadastroUiState.Erro).mensagem,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.cadastrar(
                        nome = nome,
                        email = email,
                        senha = senha,
                        confirmarSenha = confirmarSenha,
                        perfil = perfil,
                        cpf = cpf,
                        rua = rua,
                        numero = numero,
                        cidade = cidade,
                        estado = estado,
                        cep = cep
                    )
                },
                enabled = uiState !is CadastroUiState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState is CadastroUiState.Loading) "Cadastrando..." else "Cadastrar")
            }

            TextButton(
                onClick = onVoltarLogin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Já tenho conta")
            }
        }
    }
}