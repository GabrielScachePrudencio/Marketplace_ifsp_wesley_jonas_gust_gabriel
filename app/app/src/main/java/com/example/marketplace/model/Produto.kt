package com.example.marketplace.model

data class Produto(
    val id: String = "",
    val vendedorId: String = "",
    val titulo: String = "",
    val descricao: String = "",
    val categoria: String = "",
    val preco: Double = 0.0,
    val quantidade: Int = 0,
    val imagens: String = "",
)