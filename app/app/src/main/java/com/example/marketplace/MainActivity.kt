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
import com.example.marketplace.model.Usuario
import com.example.marketplace.screen.LoginScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var usuarioLogado by remember { mutableStateOf<Usuario?>(null) }
            val context = LocalContext.current

            if (usuarioLogado == null) {
                LoginScreen(onLoginSuccess = { usuario ->
                    usuarioLogado = usuario
                    Toast.makeText(context, "Login realizado com sucesso!", Toast.LENGTH_SHORT).show()
                })
            } else {
                TelaBoasVindas(usuario = usuarioLogado!!)
            }
        }
    }
}

@Composable
fun TelaBoasVindas(usuario: Usuario) {
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
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    LoginScreen(onLoginSuccess = {})
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
        )
    )
}