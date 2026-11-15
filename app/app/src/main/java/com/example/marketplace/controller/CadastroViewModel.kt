package com.example.marketplace.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.marketplace.repository.UsuarioRepository
import com.example.marketplace.model.Usuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class CadastroUiState {
    object Idle : CadastroUiState()
    object Loading : CadastroUiState()
    data class Sucesso(val usuario: Usuario) : CadastroUiState()
    data class Erro(val mensagem: String) : CadastroUiState()
}

class CadastroViewModel(
    private val repository: UsuarioRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<CadastroUiState>(CadastroUiState.Idle)

    val uiState: StateFlow<CadastroUiState> = _uiState

    fun cadastrar(
        nome: String,
        email: String,
        senha: String,
        confirmarSenha: String,
        perfil: String,
        cpf: String,
        rua: String,
        numero: String,
        cidade: String,
        estado: String,
        cep: String,
        latitude: Double? = null,
        longitude: Double? = null,
        fotoPerfil: String = ""
    ) {

        if (
            nome.isBlank() ||
            email.isBlank() ||
            senha.isBlank() ||
            cpf.isBlank()
        ) {
            _uiState.value =
                CadastroUiState.Erro("Preencha todos os campos")
            return
        }

        if (senha != confirmarSenha) {
            _uiState.value =
                CadastroUiState.Erro("As senhas não coincidem")
            return
        }

        if (senha.length < 6) {
            _uiState.value =
                CadastroUiState.Erro(
                    "A senha deve ter no mínimo 6 caracteres"
                )
            return
        }

        if (cpf.length != 11) {
            _uiState.value =
                CadastroUiState.Erro(
                    "O CPF precisa ter 11 caracteres"
                )
            return
        }

        if (
            perfil != "comprador" &&
            perfil != "motorista" &&
            perfil != "negociador"
        ) {
            _uiState.value =
                CadastroUiState.Erro(
                    "Selecione um tipo de usuário válido"
                )
            return
        }

        val coordenadasValidas = latitude != null &&
                longitude != null &&
                latitude.isFinite() &&
                longitude.isFinite() &&
                latitude in -90.0..90.0 &&
                longitude in -180.0..180.0 &&
                (latitude != 0.0 || longitude != 0.0)
        val enderecoCompleto = rua.isNotBlank() &&
                numero.isNotBlank() &&
                cidade.isNotBlank() &&
                estado.isNotBlank() &&
                cep.isNotBlank()

        if (!enderecoCompleto && !coordenadasValidas) {
            _uiState.value =
                CadastroUiState.Erro("Preencha o endereço ou selecione sua localização atual")
            return
        }

        viewModelScope.launch {

            _uiState.value = CadastroUiState.Loading

            try {
                var latFinal = latitude.takeIf { coordenadasValidas }
                var lngFinal = longitude.takeIf { coordenadasValidas }
                if (!coordenadasValidas) {
                    val coord = com.example.marketplace.service.GeocodingService.buscarCoordenadas(
                        rua = rua,
                        numero = numero,
                        cidade = cidade,
                        estado = estado,
                        cep = cep
                    )
                    if (coord != null) {
                        latFinal = coord.latitude
                        lngFinal = coord.longitude
                    }
                }

                val usuario = repository.cadastrar(
                    nome = nome,
                    email = email,
                    senha = senha,
                    perfil = perfil,
                    cpf = cpf,
                    rua = rua,
                    numero = numero,
                    cidade = cidade,
                    estado = estado,
                    cep = cep,
                    latitude = latFinal,
                    longitude = lngFinal,
                    fotoPerfil = fotoPerfil
                )

                _uiState.value =
                    CadastroUiState.Sucesso(usuario)

            } catch (e: Exception) {

                _uiState.value =
                    CadastroUiState.Erro(
                        e.message ?: "Erro ao criar usuário"
                    )
            }
        }
    }

    fun resetar() {
        _uiState.value = CadastroUiState.Idle
    }
}