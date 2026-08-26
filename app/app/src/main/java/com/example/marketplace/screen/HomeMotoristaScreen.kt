package com.example.marketplace.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.marketplace.model.Usuario
import com.example.marketplace.model.Veiculo

@Composable
fun HomeMotoristaScreen(
    usuario: Usuario,
    veiculos: List<Veiculo>,
    onCadastrarVeiculo: () -> Unit,
    onLogout: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {

        Text(
            text = "Olá, ${usuario.nome}!",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "Área do Motorista",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(
            onClick = onCadastrarVeiculo,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cadastrar veículo")
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Text(
            text = "Meus veículos",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        if (veiculos.isEmpty()) {

            Text(
                text = "Você ainda não possui veículos cadastrados."
            )

        } else {

            veiculos.forEach { veiculo ->

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {

                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {

                        Text(
                            text = "${veiculo.marca} ${veiculo.modelo}",
                            style =
                            MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = "Tipo: ${veiculo.tipo}"
                        )

                        Text(
                            text = "Ano: ${veiculo.ano}"
                        )

                        Text(
                            text = "Placa: ${veiculo.placa}"
                        )

                        Text(
                            text = "Cor: ${veiculo.cor}"
                        )
                    }
                }
            }
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sair")
        }
    }
}
