package com.example.marketplace

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.LoginUiState
import com.example.marketplace.controller.LoginViewModel
import com.example.marketplace.model.Usuario
import com.example.marketplace.screen.CreateUsuarioScreen
import com.example.marketplace.screen.LoginScreen

// Controla qual tela mostrar quando o usuário NÃO está logado
enum class TelaAuth {
    LOGIN, CADASTRO
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val loginViewModel: LoginViewModel = viewModel()
            val uiState by loginViewModel.uiState.collectAsState()

            var telaAtual by remember { mutableStateOf(TelaAuth.LOGIN) }

            when (uiState) {
                is LoginUiState.Sucesso -> {
                    val usuario = (uiState as LoginUiState.Sucesso).usuario
                    TelaBoasVindas(
                        usuario = usuario,
                        onLogout = {
                            loginViewModel.logout()
                            telaAtual = TelaAuth.LOGIN // garante que volta pro login, não pro cadastro
                            Toast.makeText(context, "Você saiu da conta", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                else -> {
                    when (telaAtual) {
                        TelaAuth.LOGIN -> {
                            LoginScreen(
                                viewModel = loginViewModel,
                                onLoginSuccess = {
                                    Toast.makeText(context, "Login realizado com sucesso!", Toast.LENGTH_SHORT).show()
                                },
                                onCriarConta = {
                                    telaAtual = TelaAuth.CADASTRO
                                }
                            )
                        }
                        TelaAuth.CADASTRO -> {
                            CreateUsuarioScreen(
                                onCadastroSuccess = { usuario ->
                                    Toast.makeText(context, "Conta criada com sucesso!", Toast.LENGTH_SHORT).show()
                                    // Loga automaticamente após o cadastro
                                    loginViewModel.login(usuario.email, "") // ver observação abaixo
                                    telaAtual = TelaAuth.LOGIN
                                },
                                onVoltarLogin = {
                                    telaAtual = TelaAuth.LOGIN
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun TelaBoasVindas(usuario: Usuario, onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Sucesso",
            tint = Color(0xFF2E7D32),
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))

        Text(
            "Login realizado com sucesso!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Nome: ${usuario.nome}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text("Email: ${usuario.email}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("Perfil: ${usuario.perfil}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Button(
            onClick = onLogout
        ) {
            Text("Sair")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    LoginScreen(onLoginSuccess = {}, onCriarConta = {})
}

@Preview(showBackground = true)
@Composable
fun TelaBoasVindasPreview() {
    TelaBoasVindas(
        usuario = Usuario(
            uid = "abc123",
            nome = "Gabriel Cache",
            email = "gabriel@teste.com",
            perfil = "negociante"
        ),
        onLogout = {}
    )
}