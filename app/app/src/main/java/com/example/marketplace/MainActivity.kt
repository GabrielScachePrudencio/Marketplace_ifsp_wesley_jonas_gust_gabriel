package com.example.marketplace

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.LoginUiState
import com.example.marketplace.controller.LoginViewModel
import com.example.marketplace.controller.LoginViewModelFactory
import com.example.marketplace.model.Usuario
import com.example.marketplace.screen.CreateUsuarioScreen
import com.example.marketplace.screen.CriarProdutoScreen
import com.example.marketplace.screen.LoginScreen
import com.example.marketplace.screen.MinhasVendasScreen
import com.example.marketplace.screen.ProdutoDetalheScreen
import com.example.marketplace.screen.ProdutoListScreen

// Controla qual tela mostrar quando o usuário NÃO está logado
enum class TelaAuth {
    LOGIN, CADASTRO
}

// Controla qual tela mostrar quando o usuário JÁ está logado
enum class TelaApp {
    PRODUTOS, CRIAR_PRODUTO, DETALHE_PRODUTO, MINHAS_VENDAS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val loginViewModel: LoginViewModel = viewModel(
                factory = LoginViewModelFactory(context)
            )
            val uiState by loginViewModel.uiState.collectAsState()

            var telaAtual by remember { mutableStateOf(TelaAuth.LOGIN) }

            when (uiState) {
                is LoginUiState.Sucesso -> {
                    val usuario = (uiState as LoginUiState.Sucesso).usuario
                    var telaApp by remember { mutableStateOf(TelaApp.PRODUTOS) }
                    var produtoSelecionadoId by remember { mutableStateOf<String?>(null) }

                    val onLogout: () -> Unit = {
                        loginViewModel.logout()
                        telaAtual = TelaAuth.LOGIN // garante que volta pro login, não pro cadastro
                        Toast.makeText(context, "Você saiu da conta", Toast.LENGTH_SHORT).show()
                    }

                    when (telaApp) {
                        TelaApp.PRODUTOS -> ProdutoListScreen(
                            usuario = usuario,
                            onProdutoClick = { id ->
                                produtoSelecionadoId = id
                                telaApp = TelaApp.DETALHE_PRODUTO
                            },
                            onCriarProduto = { telaApp = TelaApp.CRIAR_PRODUTO },
                            onMinhasVendas = { telaApp = TelaApp.MINHAS_VENDAS },
                            onLogout = onLogout
                        )
                        TelaApp.CRIAR_PRODUTO -> CriarProdutoScreen(
                            vendedorId = usuario.uid,
                            onCriado = { telaApp = TelaApp.PRODUTOS },
                            onVoltar = { telaApp = TelaApp.PRODUTOS }
                        )
                        TelaApp.DETALHE_PRODUTO -> {
                            val id = produtoSelecionadoId
                            if (id != null) {
                                ProdutoDetalheScreen(
                                    usuario = usuario,
                                    produtoId = id,
                                    onVoltar = { telaApp = TelaApp.PRODUTOS }
                                )
                            }
                        }
                        TelaApp.MINHAS_VENDAS -> MinhasVendasScreen(
                            usuario = usuario,
                            onVoltar = { telaApp = TelaApp.PRODUTOS }
                        )
                    }
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


@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    LoginScreen(onLoginSuccess = {}, onCriarConta = {})
}