package com.example.marketplace

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel

import com.example.marketplace.controller.LoginUiState
import com.example.marketplace.controller.LoginViewModel
import com.example.marketplace.controller.LoginViewModelFactory
import com.example.marketplace.controller.VeiculoViewModel
import com.example.marketplace.controller.VeiculoViewModelFactory

import com.example.marketplace.screen.CadastrarVeiculoScreen
import com.example.marketplace.screen.HomeCompradorScreen
import com.example.marketplace.screen.HomeMotoristaScreen
import com.example.marketplace.screen.CreateUsuarioScreen
import com.example.marketplace.screen.CriarProdutoScreen
import com.example.marketplace.screen.LoginScreen
import com.example.marketplace.screen.MinhasVendasScreen
import com.example.marketplace.screen.ProdutoDetalheScreen
import com.example.marketplace.screen.ProdutoListScreen


// ============================================================
// TELAS DE AUTENTICAÇÃO
// ============================================================

enum class TelaAuth {
    LOGIN,
    CADASTRO
}


// ============================================================
// TELAS DO NEGOCIADOR
// ============================================================

enum class TelaApp {
    PRODUTOS,
    CRIAR_PRODUTO,
    DETALHE_PRODUTO,
    MINHAS_VENDAS
}


// ============================================================
// TELAS DO MOTORISTA
// ============================================================

enum class TelaMotorista {
    HOME,
    CADASTRAR_VEICULO
}


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContent {

            val context = LocalContext.current

            // ====================================================
            // LOGIN VIEWMODEL
            // ====================================================

            val loginViewModel: LoginViewModel =
                viewModel(
                    factory = LoginViewModelFactory(context)
                )

            val uiState by
            loginViewModel.uiState.collectAsState()

            var telaAtual by remember {
                mutableStateOf(
                    TelaAuth.LOGIN
                )
            }


            // ====================================================
            // CONTROLE DE LOGIN
            // ====================================================

            when (uiState) {

                // =================================================
                // USUÁRIO LOGADO
                // =================================================

                is LoginUiState.Sucesso -> {

                    val usuario =
                        (uiState as LoginUiState.Sucesso).usuario


                    // =================================================
                    // LOGOUT
                    // =================================================

                    val onLogout: () -> Unit = {

                        loginViewModel.logout()

                        telaAtual =
                            TelaAuth.LOGIN

                        Toast.makeText(
                            context,
                            "Você saiu da conta",
                            Toast.LENGTH_SHORT
                        ).show()
                    }


                    // =================================================
                    // PERFIL
                    // =================================================

                    when (usuario.perfil) {


                        // =============================================
                        // COMPRADOR
                        // =============================================

                        "comprador" -> {

                            HomeCompradorScreen(

                                usuario = usuario,

                                onLogout = onLogout
                            )
                        }


                        // =============================================
                        // MOTORISTA
                        // =============================================

                        "motorista" -> {

                            // -----------------------------------------
                            // VEICULO VIEWMODEL
                            // -----------------------------------------

                            val veiculoViewModel:
                                    VeiculoViewModel =
                                viewModel(
                                    factory =
                                    VeiculoViewModelFactory(
                                        context
                                    )
                                )

                            val veiculos by
                            veiculoViewModel
                                .veiculos
                                .collectAsState()


                            // -----------------------------------------
                            // TELA ATUAL DO MOTORISTA
                            // -----------------------------------------

                            var telaMotorista by
                            remember {

                                mutableStateOf(
                                    TelaMotorista.HOME
                                )
                            }


                            // -----------------------------------------
                            // SINCRONIZA FIREBASE -> ROOM
                            // -----------------------------------------

                            LaunchedEffect(
                                usuario.uid
                            ) {

                                veiculoViewModel
                                    .sincronizarECarregarVeiculos(
                                        usuario.uid
                                    )
                            }


                            // -----------------------------------------
                            // TELAS
                            // -----------------------------------------

                            when (telaMotorista) {


                                // =====================================
                                // HOME
                                // =====================================

                                TelaMotorista.HOME -> {

                                    HomeMotoristaScreen(

                                        usuario = usuario,

                                        veiculos = veiculos,

                                        onCadastrarVeiculo = {

                                            telaMotorista =
                                                TelaMotorista
                                                    .CADASTRAR_VEICULO
                                        },

                                        onLogout = onLogout
                                    )
                                }


                                // =====================================
                                // CADASTRAR VEÍCULO
                                // =====================================

                                TelaMotorista
                                    .CADASTRAR_VEICULO -> {

                                    CadastrarVeiculoScreen(

                                        motoristaId =
                                        usuario.uid,

                                        onSucesso = {

                                            Toast.makeText(
                                                context,
                                                "Veículo cadastrado com sucesso!",
                                                Toast.LENGTH_SHORT
                                            ).show()

                                            // Atualiza os veículos
                                            veiculoViewModel
                                                .sincronizarECarregarVeiculos(
                                                    usuario.uid
                                                )

                                            // Volta para Home
                                            telaMotorista =
                                                TelaMotorista.HOME
                                        },

                                        onVoltar = {

                                            telaMotorista =
                                                TelaMotorista.HOME
                                        }
                                    )
                                }
                            }
                        }


                        // =============================================
                        // NEGOCIADOR
                        // =============================================

                        "negociador" -> {

                            var telaApp by remember {

                                mutableStateOf(
                                    TelaApp.PRODUTOS
                                )
                            }

                            var produtoSelecionadoId by
                            remember {

                                mutableStateOf<String?>(null)
                            }


                            when (telaApp) {


                                // =====================================
                                // PRODUTOS
                                // =====================================

                                TelaApp.PRODUTOS -> {

                                    ProdutoListScreen(

                                        usuario = usuario,

                                        onProdutoClick = { id ->

                                            produtoSelecionadoId =
                                                id

                                            telaApp =
                                                TelaApp
                                                    .DETALHE_PRODUTO
                                        },

                                        onCriarProduto = {

                                            telaApp =
                                                TelaApp
                                                    .CRIAR_PRODUTO
                                        },

                                        onMinhasVendas = {

                                            telaApp =
                                                TelaApp
                                                    .MINHAS_VENDAS
                                        },

                                        onLogout = onLogout
                                    )
                                }


                                // =====================================
                                // CRIAR PRODUTO
                                // =====================================

                                TelaApp.CRIAR_PRODUTO -> {

                                    CriarProdutoScreen(

                                        vendedorId =
                                        usuario.uid,

                                        onCriado = {

                                            telaApp =
                                                TelaApp
                                                    .PRODUTOS
                                        },

                                        onVoltar = {

                                            telaApp =
                                                TelaApp
                                                    .PRODUTOS
                                        }
                                    )
                                }


                                // =====================================
                                // DETALHE PRODUTO
                                // =====================================

                                TelaApp.DETALHE_PRODUTO -> {

                                    val id =
                                        produtoSelecionadoId

                                    if (id != null) {

                                        ProdutoDetalheScreen(

                                            usuario = usuario,

                                            produtoId = id,

                                            onVoltar = {

                                                telaApp =
                                                    TelaApp
                                                        .PRODUTOS
                                            }
                                        )
                                    }
                                }


                                // =====================================
                                // MINHAS VENDAS
                                // =====================================

                                TelaApp.MINHAS_VENDAS -> {

                                    MinhasVendasScreen(

                                        usuario = usuario,

                                        onVoltar = {

                                            telaApp =
                                                TelaApp
                                                    .PRODUTOS
                                        }
                                    )
                                }
                            }
                        }


                        // =============================================
                        // PERFIL INVÁLIDO
                        // =============================================

                        else -> {

                            Toast.makeText(
                                context,
                                "Perfil de usuário inválido",
                                Toast.LENGTH_LONG
                            ).show()

                            loginViewModel.logout()
                        }
                    }
                }


                // =================================================
                // NÃO LOGADO
                // =================================================

                else -> {

                    when (telaAtual) {


                        // =============================================
                        // LOGIN
                        // =============================================

                        TelaAuth.LOGIN -> {

                            LoginScreen(

                                viewModel =
                                loginViewModel,

                                onLoginSuccess = {

                                    Toast.makeText(
                                        context,
                                        "Login realizado com sucesso!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },

                                onCriarConta = {

                                    telaAtual =
                                        TelaAuth.CADASTRO
                                }
                            )
                        }


                        // =============================================
                        // CADASTRO
                        // =============================================

                        TelaAuth.CADASTRO -> {

                            CreateUsuarioScreen(

                                onCadastroSuccess = {

                                    Toast.makeText(
                                        context,
                                        "Conta criada com sucesso!",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    telaAtual =
                                        TelaAuth.LOGIN
                                },

                                onVoltarLogin = {

                                    telaAtual =
                                        TelaAuth.LOGIN
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


// ============================================================
// PREVIEW
// ============================================================

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {

    LoginScreen(
        onLoginSuccess = {},
        onCriarConta = {}
    )
}
