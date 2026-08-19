package com.example.marketplace.model

data class Venda(
    val id: String = "",
    val compradorId: String = "",
    val vendedorId: String = "",
    val produtoId: String = "",
    val quantidade: Int = 0,
    val valorUnitario: Double = 0.0,
    val valorTotal: Double = 0.0,
    val status: String = "PENDENTE",
    val data: Long = 0L
)