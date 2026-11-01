package com.example.marketplace.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.UsuarioViewModel
import com.example.marketplace.controller.UsuarioViewModelFactory
import com.example.marketplace.model.Usuario
import com.example.marketplace.service.GeocodingService
import com.example.marketplace.util.AvatarUsuario
import com.example.marketplace.util.ImageUtils
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditarPerfilScreen(
    usuario: Usuario,
    onVoltar: () -> Unit,
    onPerfilAtualizado: (Usuario) -> Unit
) {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val viewModel: UsuarioViewModel = viewModel(
        factory = UsuarioViewModelFactory(context)
    )

    var nome by remember { mutableStateOf(usuario.nome) }
    var cpf by remember { mutableStateOf(usuario.cpf) }
    var cep by remember { mutableStateOf(usuario.cep) }
    var rua by remember { mutableStateOf(usuario.rua) }
    var numero by remember { mutableStateOf(usuario.numero) }
    var cidade by remember { mutableStateOf(usuario.cidade) }
    var estado by remember { mutableStateOf(usuario.estado) }
    var latitude by remember {
        mutableStateOf(
            usuario.latitude?.takeIf { it != 0.0 }?.let { String.format(Locale.US, "%.6f", it) } ?: ""
        )
    }
    var longitude by remember {
        mutableStateOf(
            usuario.longitude?.takeIf { it != 0.0 }?.let { String.format(Locale.US, "%.6f", it) } ?: ""
        )
    }
    var fotoPerfilBase64 by remember { mutableStateOf(usuario.fotoPerfil) }

    var buscandoCep by remember { mutableStateOf(false) }
    var buscandoCoordenadas by remember { mutableStateOf(false) }
    var salvando by remember { mutableStateOf(false) }
    var mensagemErro by remember { mutableStateOf<String?>(null) }

    val ocupado = buscandoCep || buscandoCoordenadas || salvando

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val base64 = ImageUtils.uriParaBase64(context, uri, maxDimensao = 300, qualidade = 70)
            if (base64 != null) {
                fotoPerfilBase64 = base64
            }
        }
    }

    fun buscarDadosPorCep(cepBusca: String) {
        val limpo = cepBusca.filter { it.isDigit() }
        if (limpo.length != 8) return
        escopo.launch {
            buscandoCep = true
            mensagemErro = null
            val end = GeocodingService.buscarEnderecoPorCep(limpo)
            if (end == null) {
                mensagemErro = "CEP não encontrado. Confira os números ou preencha manualmente."
                buscandoCep = false
            } else {
                if (end.rua.isNotBlank()) rua = end.rua
                cidade = end.cidade
                estado = end.estado
                buscandoCep = false

                buscandoCoordenadas = true
                val coord = GeocodingService.buscarCoordenadas(
                    rua = rua,
                    numero = numero,
                    cidade = cidade,
                    estado = estado,
                    cep = limpo
                )
                if (coord != null) {
                    latitude = String.format(Locale.US, "%.6f", coord.latitude)
                    longitude = String.format(Locale.US, "%.6f", coord.longitude)
                }
                buscandoCoordenadas = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar Perfil") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
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
            // ---------- FOTO DE PERFIL ----------
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    AvatarUsuario(
                        fotoBase64 = fotoPerfilBase64.ifEmpty { null },
                        nome = nome,
                        modifier = Modifier.size(96.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Alterar foto",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (fotoPerfilBase64.isBlank()) "Adicionar foto" else "Alterar foto",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // ---------- DADOS DA CONTA (SOMENTE LEITURA) ----------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("E-mail: ${usuario.email}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Perfil: ${usuario.perfil.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text("Dados pessoais", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = nome,
                onValueChange = { nome = it },
                label = { Text("Nome completo") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = cpf,
                onValueChange = { input ->
                    val digitos = input.filter { it.isDigit() }
                    if (digitos.length <= 11) cpf = digitos
                },
                label = { Text("CPF (somente números)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Endereço", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = cep,
                    onValueChange = { input ->
                        val digitos = input.filter { it.isDigit() }
                        if (digitos.length <= 8) {
                            cep = digitos
                            if (digitos.length == 8 && digitos != usuario.cep.filter { it.isDigit() }) {
                                buscarDadosPorCep(digitos)
                            }
                        }
                    },
                    label = { Text("CEP") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    enabled = !ocupado && cep.filter { it.isDigit() }.length == 8,
                    onClick = { buscarDadosPorCep(cep) }
                ) {
                    Text(if (buscandoCep) "..." else "Buscar CEP")
                }
            }

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
                    value = estado,
                    onValueChange = { if (it.length <= 2) estado = it.uppercase() },
                    label = { Text("UF") },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = cidade,
                onValueChange = { cidade = it },
                label = { Text("Cidade") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                enabled = !ocupado && rua.isNotBlank() && cidade.isNotBlank(),
                onClick = {
                    escopo.launch {
                        buscandoCoordenadas = true
                        mensagemErro = null
                        val coord = GeocodingService.buscarCoordenadas(
                            rua = rua,
                            numero = numero,
                            cidade = cidade,
                            estado = estado,
                            cep = cep.filter { it.isDigit() }
                        )
                        if (coord != null) {
                            latitude = String.format(Locale.US, "%.6f", coord.latitude)
                            longitude = String.format(Locale.US, "%.6f", coord.longitude)
                        } else {
                            mensagemErro = "Não foi possível localizar as coordenadas deste endereço."
                        }
                        buscandoCoordenadas = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (buscandoCoordenadas) "Atualizando coordenadas..." else "Atualizar coordenadas pelo endereço")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it },
                    label = { Text("Latitude") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    label = { Text("Longitude") },
                    modifier = Modifier.weight(1f)
                )
            }

            if (mensagemErro != null) {
                Text(
                    text = mensagemErro!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    if (nome.isBlank()) {
                        mensagemErro = "O nome não pode ficar em branco."
                        return@Button
                    }
                    if (cpf.isNotBlank() && cpf.length != 11) {
                        mensagemErro = "O CPF precisa ter 11 dígitos."
                        return@Button
                    }
                    if (rua.isBlank() || numero.isBlank() || cidade.isBlank() || estado.isBlank() || cep.isBlank()) {
                        mensagemErro = "Preencha o endereço completo."
                        return@Button
                    }

                    escopo.launch {
                        salvando = true
                        mensagemErro = null

                        val enderecoMudou = rua.trim() != usuario.rua.trim() ||
                                numero.trim() != usuario.numero.trim() ||
                                cidade.trim() != usuario.cidade.trim() ||
                                estado.trim() != usuario.estado.trim() ||
                                cep.trim() != usuario.cep.trim()

                        var latFinal = latitude.trim().replace(",", ".").toDoubleOrNull()
                        var lngFinal = longitude.trim().replace(",", ".").toDoubleOrNull()

                        if (enderecoMudou || latFinal == null || lngFinal == null || (latFinal == 0.0 && lngFinal == 0.0)) {
                            val coord = GeocodingService.buscarCoordenadas(
                                rua = rua.trim(),
                                numero = numero.trim(),
                                cidade = cidade.trim(),
                                estado = estado.trim(),
                                cep = cep.filter { it.isDigit() }
                            )
                            if (coord != null) {
                                latFinal = coord.latitude
                                lngFinal = coord.longitude
                            }
                        }

                        val usuarioAtualizado = usuario.copy(
                            nome = nome.trim(),
                            cpf = cpf.trim(),
                            rua = rua.trim(),
                            numero = numero.trim(),
                            cidade = cidade.trim(),
                            estado = estado.trim().uppercase(),
                            cep = cep.filter { it.isDigit() },
                            fotoPerfil = fotoPerfilBase64,
                            latitude = latFinal,
                            longitude = lngFinal
                        )

                        viewModel.atualizarPerfil(
                            usuarioAtualizado = usuarioAtualizado,
                            onSucesso = { salvo ->
                                salvando = false
                                onPerfilAtualizado(salvo)
                            },
                            onErro = { erro ->
                                salvando = false
                                mensagemErro = erro
                            }
                        )
                    }
                },
                enabled = !ocupado,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (salvando) "Salvando..." else "Salvar alterações")
            }
        }
    }
}
