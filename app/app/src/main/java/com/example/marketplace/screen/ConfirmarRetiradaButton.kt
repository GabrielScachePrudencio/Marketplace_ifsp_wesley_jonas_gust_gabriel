package com.example.marketplace.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.marketplace.repository.ComprovanteRetiradaRepository
import com.example.marketplace.util.hasLocationPermission
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

@Composable
fun ConfirmarRetiradaButton(
    vendaId: String,
    motoristaId: String,
    enabled: Boolean = true,
    textoBotao: String = "Tirar foto e confirmar coleta",
    modifier: Modifier = Modifier.fillMaxWidth(),
    onConfirmado: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fotoPendente by remember(vendaId) { mutableStateOf<Uri?>(null) }
    var salvando by remember(vendaId) { mutableStateOf(false) }
    var erro by remember(vendaId) { mutableStateOf<String?>(null) }

    val permissaoLocalizacaoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissoes ->
        val concedida = permissoes[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissoes[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                hasLocationPermission(context)
        erro = if (concedida) null else {
            "A permissão de localização é necessária para registrar o local da retirada."
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { fotoCapturada ->
        val uri = fotoPendente
        fotoPendente = null
        if (!fotoCapturada || uri == null) {
            if (fotoCapturada) erro = "A foto da retirada não pôde ser lida."
            return@rememberLauncherForActivityResult
        }

        salvando = true
        erro = null
        scope.launch {
            try {
                val localizacao = LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token
                    ).await()
                    ?: error("Não foi possível obter a localização atual. Tente novamente com o GPS ativo.")

                ComprovanteRetiradaRepository(context).salvar(
                    vendaId = vendaId,
                    motoristaId = motoristaId,
                    fotoUri = uri,
                    latitude = localizacao.latitude,
                    longitude = localizacao.longitude
                )
                onConfirmado()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                erro = e.message ?: "Não foi possível salvar o comprovante de retirada."
            } finally {
                salvando = false
            }
        }
    }

    Button(
        onClick = {
            erro = null
            if (!hasLocationPermission(context)) {
                permissaoLocalizacaoLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
                return@Button
            }

            val pastaFotos = File(context.cacheDir, "comprovantes_retirada")
            if (!pastaFotos.exists() && !pastaFotos.mkdirs()) {
                erro = "Não foi possível preparar o arquivo da foto."
                return@Button
            }
            try {
                val arquivo = File.createTempFile("retirada_${vendaId}_", ".jpg", pastaFotos)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    arquivo
                )
                fotoPendente = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                erro = e.message ?: "Não foi possível abrir a câmera."
            }
        },
        enabled = enabled && !salvando,
        modifier = modifier
    ) {
        if (salvando) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Salvando comprovante...")
        } else {
            Text(textoBotao)
        }
    }
    erro?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
