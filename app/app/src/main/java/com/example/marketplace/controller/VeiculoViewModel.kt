package com.example.marketplace.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.marketplace.data.repository.VeiculoRepository
import com.example.marketplace.model.Veiculo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class VeiculoUiState {

    object Idle : VeiculoUiState()

    object Loading : VeiculoUiState()

    data class Sucesso(
        val veiculo: Veiculo
    ) : VeiculoUiState()

    data class Erro(
        val mensagem: String
    ) : VeiculoUiState()
}

class VeiculoViewModel(
    private val repository: VeiculoRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<VeiculoUiState>(
            VeiculoUiState.Idle
        )

    val uiState: StateFlow<VeiculoUiState> =
        _uiState.asStateFlow()


    // Lista de veículos do motorista logado
    private val _veiculos =
        MutableStateFlow<List<Veiculo>>(emptyList())

    val veiculos: StateFlow<List<Veiculo>> =
        _veiculos.asStateFlow()


    // =========================================================
    // CADASTRAR VEÍCULO
    // =========================================================

    fun cadastrarVeiculo(
        motoristaId: String,
        tipo: String,
        marca: String,
        modelo: String,
        ano: Int,
        placa: String,
        cor: String
    ) {

        if (motoristaId.isBlank()) {
            _uiState.value =
                VeiculoUiState.Erro(
                    "Motorista não informado"
                )
            return
        }

        if (tipo.isBlank()) {
            _uiState.value =
                VeiculoUiState.Erro(
                    "Informe o tipo do veículo"
                )
            return
        }

        if (marca.isBlank()) {
            _uiState.value =
                VeiculoUiState.Erro(
                    "Informe a marca do veículo"
                )
            return
        }

        if (modelo.isBlank()) {
            _uiState.value =
                VeiculoUiState.Erro(
                    "Informe o modelo do veículo"
                )
            return
        }

        if (ano <= 0) {
            _uiState.value =
                VeiculoUiState.Erro(
                    "Informe um ano válido"
                )
            return
        }

        if (placa.isBlank()) {
            _uiState.value =
                VeiculoUiState.Erro(
                    "Informe a placa"
                )
            return
        }

        viewModelScope.launch {

            _uiState.value =
                VeiculoUiState.Loading

            try {

                val veiculo =
                    repository.cadastrarVeiculo(
                        motoristaId = motoristaId,
                        tipo = tipo,
                        marca = marca,
                        modelo = modelo,
                        ano = ano,
                        placa = placa,
                        cor = cor
                    )

                _uiState.value =
                    VeiculoUiState.Sucesso(
                        veiculo
                    )

                // Atualiza a lista local imediatamente
                carregarVeiculosDoMotorista(motoristaId)

            } catch (e: Exception) {

                _uiState.value =
                    VeiculoUiState.Erro(
                        e.message
                            ?: "Erro ao cadastrar veículo"
                    )
            }
        }
    }


    // =========================================================
    // CARREGAR VEÍCULOS DO MOTORISTA
    // =========================================================

    fun carregarVeiculosDoMotorista(
        motoristaId: String
    ) {

        if (motoristaId.isBlank()) {
            _veiculos.value = emptyList()
            return
        }

        viewModelScope.launch {

            try {

                repository
                    .buscarVeiculosDoMotorista(
                        motoristaId
                    )
                    .collect { lista ->

                        _veiculos.value = lista
                    }

            } catch (e: Exception) {

                _uiState.value =
                    VeiculoUiState.Erro(
                        e.message
                            ?: "Erro ao carregar veículos"
                    )
            }
        }
    }


    // =========================================================
    // SINCRONIZAR FIREBASE -> LOCAL
    // =========================================================

    fun sincronizarECarregarVeiculos(
        motoristaId: String
    ) {
        viewModelScope.launch {

            try {

                repository.sincronizarVeiculosDoMotorista(
                    motoristaId
                )

                repository
                    .buscarVeiculosDoMotorista(
                        motoristaId
                    )
                    .collect { lista ->

                        _veiculos.value = lista
                    }

            } catch (e: Exception) {

                _uiState.value =
                    VeiculoUiState.Erro(
                        e.message
                            ?: "Erro ao carregar veículos"
                    )
            }
        }
    }



    // =========================================================
    // BUSCAR VEÍCULO POR ID
    // =========================================================

    fun buscarVeiculoPorId(
        id: String,
        onResultado: (Veiculo?) -> Unit
    ) {

        viewModelScope.launch {

            try {

                val veiculo =
                    repository.buscarVeiculoPorId(id)

                onResultado(veiculo)

            } catch (e: Exception) {

                _uiState.value =
                    VeiculoUiState.Erro(
                        e.message
                            ?: "Erro ao buscar veículo"
                    )

                onResultado(null)
            }
        }
    }


    // =========================================================
    // ATUALIZAR VEÍCULO
    // =========================================================

    fun atualizarVeiculo(
        veiculo: Veiculo,
        onSucesso: () -> Unit = {}
    ) {

        viewModelScope.launch {

            _uiState.value =
                VeiculoUiState.Loading

            try {

                repository.atualizarVeiculo(
                    veiculo
                )

                _uiState.value =
                    VeiculoUiState.Sucesso(
                        veiculo
                    )

                carregarVeiculosDoMotorista(
                    veiculo.motoristaId
                )

                onSucesso()

            } catch (e: Exception) {

                _uiState.value =
                    VeiculoUiState.Erro(
                        e.message
                            ?: "Erro ao atualizar veículo"
                    )
            }
        }
    }


    // =========================================================
    // EXCLUIR VEÍCULO
    // =========================================================

    fun excluirVeiculo(
        veiculo: Veiculo,
        onSucesso: () -> Unit = {}
    ) {

        viewModelScope.launch {

            _uiState.value =
                VeiculoUiState.Loading

            try {

                repository.excluirVeiculo(
                    veiculo
                )

                _uiState.value =
                    VeiculoUiState.Idle

                carregarVeiculosDoMotorista(
                    veiculo.motoristaId
                )

                onSucesso()

            } catch (e: Exception) {

                _uiState.value =
                    VeiculoUiState.Erro(
                        e.message
                            ?: "Erro ao excluir veículo"
                    )
            }
        }
    }


    // =========================================================
    // RESETAR ESTADO
    // =========================================================

    fun resetar() {

        _uiState.value =
            VeiculoUiState.Idle
    }
}
