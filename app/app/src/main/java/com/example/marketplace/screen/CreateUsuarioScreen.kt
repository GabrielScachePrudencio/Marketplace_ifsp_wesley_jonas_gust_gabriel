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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.CadastroUiState
import com.example.marketplace.controller.CadastroViewModel
import com.example.marketplace.controller.CadastroViewModelFactory
import com.example.marketplace.service.GeocodingService
import com.example.marketplace.util.AvatarUsuario
import com.example.marketplace.util.ImageUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val PERFIS = listOf("comprador", "motorista", "negociador")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateUsuarioScreen(
    onCadastroSuccess: () -> Unit,
    onVoltarLogin: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: CadastroViewModel = viewModel(
        factory = CadastroViewModelFactory(context)
    )
    val uiState by viewModel.uiState.collectAsState()

    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var confirmarSenha by remember { mutableStateOf("") }
    var cpf by remember { mutableStateOf("") }
    var perfil by remember { mutableStateOf(PERFIS[0]) }

    var rua by remember { mutableStateOf("") }
    var numero by remember { mutableStateOf("") }
    var cidade by remember { mutableStateOf("") }
    var estado by remember { mutableStateOf("") }
    var cep by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var fotoPerfilBase64 by remember { mutableStateOf("") }

    val escopo = rememberCoroutineScope()
    var buscandoCep by remember { mutableStateOf(false) }
    var buscandoCoordenadas by remember { mutableStateOf(false) }
    var mensagemCep by remember { mutableStateOf<String?>(null) }

    fun buscarDadosPorCep(cepBusca: String) {
        val limpo = cepBusca.filter { it.isDigit() }
        if (limpo.length != 8) return
        escopo.launch {
            buscandoCep = true
            mensagemCep = null
            val end = GeocodingService.buscarEnderecoPorCep(limpo)
            if (end == null) {
                mensagemCep = "CEP não encontrado. Preencha o endereço manualmente."
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

    // Busca automática assim que o usuário digitar os 8 dígitos do CEP
    LaunchedEffect(cep) {
        val limpo = cep.filter { it.isDigit() }
        if (limpo.length == 8) {
            buscarDadosPorCep(limpo)
        }
    }

    // Atualiza as coordenadas automaticamente quando o usuário preenche o número da residência
    LaunchedEffect(numero) {
        if (numero.isNotBlank() && rua.isNotBlank() && cidade.isNotBlank()) {
            delay(700)
            buscandoCoordenadas = true
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
            }
            buscandoCoordenadas = false
        }
    }

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

    LaunchedEffect(uiState) {
        if (uiState is CadastroUiState.Sucesso) {
            onCadastroSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Criar conta") })
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

            Text("Dados pessoais", style = MaterialTheme.typography.titleMedium)

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
                            contentDescription = "Selecionar foto",
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

            OutlinedTextField(
                value = nome,
                onValueChange = { nome = it },
                label = { Text("Nome completo") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-mail") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = senha,
                onValueChange = { senha = it },
                label = { Text("Senha") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = confirmarSenha,
                onValueChange = { confirmarSenha = it },
                label = { Text("Confirmar senha") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = cpf,
                onValueChange = { cpf = it },
                label = { Text("CPF (somente números)") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Perfil", style = MaterialTheme.typography.titleMedium)

            Column {
                PERFIS.forEach { opcao ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = perfil == opcao,
                            onClick = { perfil = opcao }
                        )
                        Text(opcao.replaceFirstChar { it.uppercase() })
                    }
                }
            }

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
                        if (digitos.length <= 8) cep = digitos
                    },
                    label = { Text("CEP (somente números)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    enabled = !buscandoCep && !buscandoCoordenadas && cep.filter { it.isDigit() }.length == 8,
                    onClick = { buscarDadosPorCep(cep) }
                ) {
                    Text(if (buscandoCep) "..." else "Buscar CEP")
                }
            }

            if (mensagemCep != null) {
                Text(
                    text = mensagemCep!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Coordenadas (preenchidas automaticamente)",
                    style = MaterialTheme.typography.titleSmall
                )
                if (buscandoCoordenadas) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
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

            if (uiState is CadastroUiState.Erro) {
                Text(
                    text = (uiState as CadastroUiState.Erro).mensagem,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val latParsed = latitude.trim().replace(",", ".").toDoubleOrNull()
                    val lngParsed = longitude.trim().replace(",", ".").toDoubleOrNull()
                    viewModel.cadastrar(
                        nome = nome,
                        email = email,
                        senha = senha,
                        confirmarSenha = confirmarSenha,
                        perfil = perfil,
                        cpf = cpf,
                        rua = rua,
                        numero = numero,
                        cidade = cidade,
                        estado = estado,
                        cep = cep,
                        latitude = latParsed,
                        longitude = lngParsed,
                        fotoPerfil = fotoPerfilBase64
                    )
                },
                enabled = uiState !is CadastroUiState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState is CadastroUiState.Loading) "Cadastrando..." else "Cadastrar")
            }

            TextButton(
                onClick = onVoltarLogin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Já tenho conta")
            }
        }
    }
}