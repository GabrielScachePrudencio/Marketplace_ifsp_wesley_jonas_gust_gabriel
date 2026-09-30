package com.example.marketplace.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.marketplace.data.local.AppDatabase
import com.example.marketplace.model.PontoReferencia
import com.example.marketplace.model.Usuario
import com.example.marketplace.model.enums.TipoPontoReferencia
import com.example.marketplace.repository.PontoReferenciaRepository
import com.example.marketplace.service.GeocodingService
import com.example.marketplace.util.MapaSelecaoPonto
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CriarEditarPontoReferenciaScreen(
    usuario: Usuario,
    pontoExistente: PontoReferencia?,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
) {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()

    val repository = remember {
        val db = AppDatabase.getDatabase(context)
        PontoReferenciaRepository(db.pontoReferenciaDao(), db.pendenteSycronizacaoDao())
    }

    var nome by remember { mutableStateOf(pontoExistente?.nome ?: "") }
    var tipo by remember {
        mutableStateOf(pontoExistente?.tipoEnum ?: TipoPontoReferencia.values().first())
    }
    var cep by remember { mutableStateOf(pontoExistente?.cep ?: "") }
    var rua by remember { mutableStateOf(pontoExistente?.rua ?: "") }
    var numero by remember { mutableStateOf(pontoExistente?.numero ?: "") }
    var cidade by remember { mutableStateOf(pontoExistente?.cidade ?: "") }
    var estado by remember { mutableStateOf(pontoExistente?.estado ?: "") }
    var latitude by remember { mutableStateOf(pontoExistente?.latitude?.takeIf { it != 0.0 }) }
    var longitude by remember { mutableStateOf(pontoExistente?.longitude?.takeIf { it != 0.0 }) }
    var principal by remember { mutableStateOf(pontoExistente?.principal ?: false) }

    var tipoMenuAberto by remember { mutableStateOf(false) }
    var buscandoCep by remember { mutableStateOf(false) }
    var localizando by remember { mutableStateOf(false) }
    var salvando by remember { mutableStateOf(false) }
    var mensagemErro by remember { mutableStateOf<String?>(null) }

    val ocupado = buscandoCep || localizando || salvando

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (pontoExistente == null) "Novo Ponto de Referência" else "Editar Ponto") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = nome,
                onValueChange = { nome = it },
                label = { Text("Nome do ponto (ex: Loja Centro)") },
                modifier = Modifier.fillMaxWidth()
            )

            // ---------- SELECT DO TIPO ----------
            ExposedDropdownMenuBox(
                expanded = tipoMenuAberto,
                onExpandedChange = { tipoMenuAberto = it }
            ) {
                OutlinedTextField(
                    value = tipo.name.replace("_", " "),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tipo") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tipoMenuAberto) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = tipoMenuAberto,
                    onDismissRequest = { tipoMenuAberto = false }
                ) {
                    TipoPontoReferencia.values().forEach { opcao ->
                        DropdownMenuItem(
                            text = { Text(opcao.name.replace("_", " ")) },
                            onClick = {
                                tipo = opcao
                                tipoMenuAberto = false
                            }
                        )
                    }
                }
            }

            // ---------- CEP ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = cep,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 8) cep = input
                    },
                    label = { Text("CEP") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    enabled = !ocupado && cep.length == 8,
                    onClick = {
                        escopo.launch {
                            buscandoCep = true
                            mensagemErro = null
                            val end = GeocodingService.buscarEnderecoPorCep(cep)
                            if (end == null) {
                                mensagemErro = "CEP não encontrado. Confira os números ou preencha o endereço manualmente."
                            } else {
                                if (end.rua.isNotBlank()) rua = end.rua
                                cidade = end.cidade
                                estado = end.estado
                                val coord = GeocodingService.buscarCoordenadas(
                                    rua = end.rua,
                                    numero = "",
                                    cidade = end.cidade,
                                    estado = end.estado,
                                    cep = cep
                                )
                                if (coord != null) {
                                    latitude = coord.latitude
                                    longitude = coord.longitude
                                } else {
                                    mensagemErro = "Endereço preenchido, mas não consegui achar no mapa. Toque no mapa para marcar."
                                }
                            }
                            buscandoCep = false
                        }
                    }
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
                        localizando = true
                        mensagemErro = null
                        val coord = GeocodingService.buscarCoordenadas(rua, numero, cidade, estado, cep)
                        if (coord != null) {
                            latitude = coord.latitude
                            longitude = coord.longitude
                        } else {
                            mensagemErro = "Não consegui localizar esse endereço. Toque no mapa para marcar."
                        }
                        localizando = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (localizando) "Localizando..." else "Localizar endereço no mapa")
            }

            // ---------- MAPA ----------
            Text("Toque no mapa para marcar ou ajustar o local exato", style = MaterialTheme.typography.bodySmall)
            Card(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                MapaSelecaoPonto(
                    latitude = latitude,
                    longitude = longitude,
                    onPontoEscolhido = { lat, lng ->
                        latitude = lat
                        longitude = lng
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                if (latitude != null && longitude != null)
                    "Lat: ${"%.6f".format(latitude)}  |  Lng: ${"%.6f".format(longitude)}"
                else
                    "Nenhum local marcado ainda",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ponto principal")
                Switch(checked = principal, onCheckedChange = { principal = it })
            }

            if (mensagemErro != null) {
                Text(
                    mensagemErro!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                enabled = !ocupado,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (nome.isBlank() || rua.isBlank() || cidade.isBlank()) {
                        mensagemErro = "Preencha nome, rua e cidade."
                        return@Button
                    }
                    escopo.launch {
                        salvando = true
                        mensagemErro = null
                        try {
                            // Se ninguém marcou o mapa, tenta achar pelo endereço antes de salvar
                            if (latitude == null || longitude == null) {
                                GeocodingService.buscarCoordenadas(rua, numero, cidade, estado, cep)?.let {
                                    latitude = it.latitude
                                    longitude = it.longitude
                                }
                            }
                            val lat = latitude
                            val lng = longitude
                            if (lat == null || lng == null) {
                                mensagemErro = "Marque o local no mapa, ou use Buscar CEP / Localizar endereço."
                                salvando = false
                                return@launch
                            }

                            if (pontoExistente == null) {
                                repository.criarPonto(
                                    negocianteId = usuario.uid,
                                    nome = nome.trim(),
                                    tipo = tipo,
                                    rua = rua.trim(),
                                    numero = numero.trim(),
                                    cidade = cidade.trim(),
                                    estado = estado.trim(),
                                    cep = cep,
                                    latitude = lat,
                                    longitude = lng,
                                    principal = principal
                                )
                            } else {
                                repository.atualizarPonto(
                                    pontoExistente.copy(
                                        nome = nome.trim(),
                                        tipo = tipo.name,
                                        rua = rua.trim(),
                                        numero = numero.trim(),
                                        cidade = cidade.trim(),
                                        estado = estado.trim(),
                                        cep = cep,
                                        latitude = lat,
                                        longitude = lng,
                                        principal = principal
                                    )
                                )
                            }
                            onSucesso()
                        } catch (e: Exception) {
                            mensagemErro = e.message ?: "Erro ao salvar o ponto."
                            salvando = false
                        }
                    }
                }
            ) {
                Text(if (salvando) "Salvando..." else "Salvar")
            }
        }
    }
}