package com.example.marketplace.domain

import java.time.LocalDate

object VeiculoRegras {
    fun validar(motoristaId: String, placa: String, ano: Int) {
        require(motoristaId.isNotBlank()) { "motoristaId é obrigatório" }
        require(placa.isNotBlank()) { "Placa é obrigatória" }
        val anoAtual = LocalDate.now().year
        require(ano in 1950..(anoAtual + 1)) { "Ano inválido" }
    }
}
