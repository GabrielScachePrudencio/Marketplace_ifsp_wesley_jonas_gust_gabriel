package com.example.marketplace.screen



import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.marketplace.model.Usuario

@Composable
fun HomeCompradorScreen(
    usuario: Usuario,
    onLogout: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),

        verticalArrangement =
        Arrangement.Center,

        horizontalAlignment =
        Alignment.CenterHorizontally
    ) {

        Text(
            text = "Olá, ${usuario.nome}!",
            style =
            MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Área do Comprador"
        )

        // Vamos colocar aqui depois:
        // - últimas vendas
        // - veículos dos motoristas
        // - meus veículos

        Button(
            onClick = onLogout
        ) {
            Text("Sair")
        }
    }
}
