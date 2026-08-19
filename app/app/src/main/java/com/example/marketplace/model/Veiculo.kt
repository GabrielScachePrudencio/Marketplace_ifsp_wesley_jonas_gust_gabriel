package com.example.marketplace.model

data class Veiculo(
    val id: String = "",
    val motoristaId: String = "",
    val tipo: String = "",
    val marca: String = "",
    val modelo: String = "",
    val ano: Int = 0,
    val placa: String = "",
    val cor: String = "",
)