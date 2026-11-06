package com.example.marketplace.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.marketplace.model.ComprovanteEntrega
import com.example.marketplace.service.GeocodingService
import com.example.marketplace.util.ImageUtils
import com.example.marketplace.util.MapaRastreamento
import com.example.marketplace.util.MarcadorMapa
import com.example.marketplace.util.TipoMarcador
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ComprovanteEntregaCard(comprovante: ComprovanteEntrega) {
    val bitmap = remember(comprovante.fotoBase64) {
        ImageUtils.base64ParaBitmap(comprovante.fotoBase64)?.asImageBitmap()
    }
    var ruaEntrega by remember(comprovante.latitude, comprovante.longitude) {
        mutableStateOf("Buscando endereço...")
    }
    LaunchedEffect(comprovante.latitude, comprovante.longitude) {
        ruaEntrega = GeocodingService.buscarRuaPorCoordenadas(
            comprovante.latitude,
            comprovante.longitude
        ) ?: "Endereço não identificado"
    }
    val horarioEntrega = remember(comprovante.criadoEm) {
        SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
            .format(Date(comprovante.criadoEm))
    }
    val coordenadasTexto = remember(comprovante.latitude, comprovante.longitude) {
        String.format(
            Locale.US,
            "%.6f, %.6f",
            comprovante.latitude,
            comprovante.longitude
        )
    }
    var mostrarMapa by remember(comprovante.vendaId, comprovante.latitude, comprovante.longitude) {
        mutableStateOf(false)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Comprovante de entrega", style = MaterialTheme.typography.titleMedium)
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Foto do comprovante de entrega",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { mostrarMapa = true }
                )
            }
            Text(
                "Rua: $ruaEntrega\n" +
                        "Coordenadas: $coordenadasTexto\n" +
                        "Horário: $horarioEntrega\n" +
                        "Toque na foto para ver o local no mapa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (mostrarMapa) {
        AlertDialog(
            onDismissRequest = { mostrarMapa = false },
            title = {
                Text(if (ruaEntrega == "Endereço não identificado") "Local da entrega" else "Local da entrega — $ruaEntrega")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bitmap?.let {
                        Image(
                            bitmap = it,
                            contentDescription = "Foto do comprovante de entrega",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Text(
                        "Rua: $ruaEntrega\n" +
                                "Coordenadas: $coordenadasTexto\n" +
                                "Horário: $horarioEntrega",
                        style = MaterialTheme.typography.bodySmall
                    )
                    MapaRastreamento(
                        origem = null,
                        destino = MarcadorMapa(
                            latitude = comprovante.latitude,
                            longitude = comprovante.longitude,
                            titulo = if (ruaEntrega == "Endereço não identificado") {
                                "Local da entrega"
                            } else "Entrega · $ruaEntrega",
                            tipo = TipoMarcador.DESTINO
                        ),
                        motorista = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { mostrarMapa = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}
