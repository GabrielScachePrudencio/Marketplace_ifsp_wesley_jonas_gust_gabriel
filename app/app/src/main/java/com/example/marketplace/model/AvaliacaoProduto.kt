package com.example.marketplace.model

data class AvaliacaoProduto(
    val id: String = "",
    val produtoId: String = "",
    val usuarioId: String = "",
    val nota: Int = 0,
    val comentario: String = "",
    val data: Long = 0L
)