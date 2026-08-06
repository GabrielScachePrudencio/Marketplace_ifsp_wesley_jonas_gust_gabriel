package com.example.owneravatarmarketplace_ifsp_wesley_jonas_gust_gabriel

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                // Estado para controlar qual tela aparece ("login" ou "cadastro")
                var telaAtual by remember { mutableStateOf("login") }

                if (telaAtual == "login") {
                    LoginScreen(
                        onNavigateToCadastro = { telaAtual = "cadastro" },
                        onLoginSuccess = {
                            // Ações após o login bem-sucedido
                        }
                    )
                } else {
                    CadastroScreen(
                        onNavigateBackToLogin = { telaAtual = "login" }
                    )
                }
            }
        }
    }
}

@Composable
fun LoginScreen(onNavigateToCadastro: () -> Unit, onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var mensagemErro by remember { mutableStateOf<String?>(null) }

    val auth = Firebase.auth

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Marketplace - Login",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = senha,
            onValueChange = { senha = it },
            label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (email.isNotBlank() && senha.isNotBlank()) {
                    auth.signInWithEmailAndPassword(email, senha)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(context, "Login realizado com sucesso!", Toast.LENGTH_SHORT).show()
                                onLoginSuccess()
                            } else {
                                mensagemErro = task.exception?.message ?: "Erro desconhecido"
                            }
                        }
                } else {
                    mensagemErro = "Preencha todos os campos"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Entrar")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Botão para ir para a tela de cadastro
        TextButton(
            onClick = { onNavigateToCadastro() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Não tem uma conta? Cadastre-se")
        }

        mensagemErro?.let { erro ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = erro, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun CadastroScreen(onNavigateBackToLogin: () -> Unit) {
    val context = LocalContext.current

    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var mensagemErro by remember { mutableStateOf<String?>(null) }

    val auth = Firebase.auth
    val db = Firebase.firestore

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Marketplace - Cadastro",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = nome,
            onValueChange = { nome = it },
            label = { Text("Nome") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = senha,
            onValueChange = { senha = it },
            label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (nome.isNotBlank() && email.isNotBlank() && senha.isNotBlank()) {
                    auth.createUserWithEmailAndPassword(email, senha)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val userId = auth.currentUser?.uid ?: ""
                                val dadosUsuario = hashMapOf(
                                    "nome" to nome,
                                    "email" to email
                                )

                                db.collection("usuarios")
                                    .document(userId)
                                    .set(dadosUsuario)
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "Cadastro realizado com sucesso!", Toast.LENGTH_SHORT).show()
                                        onNavigateBackToLogin()
                                    }
                                    .addOnFailureListener { e ->
                                        mensagemErro = "Erro ao salvar dados: ${e.message}"
                                    }
                            } else {
                                mensagemErro = task.exception?.message ?: "Erro ao cadastrar"
                            }
                        }
                } else {
                    mensagemErro = "Por favor, preencha todos os campos."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cadastrar")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Botão para voltar para a tela de login
        TextButton(
            onClick = { onNavigateBackToLogin() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Já tem uma conta? Faça login")
        }

        mensagemErro?.let { erro ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = erro, color = MaterialTheme.colorScheme.error)
        }
    }
}