package com.example.marketplace.model

data class ComprovanteEntrega(
    val vendaId: String,
    val motoristaId: String,
    val fotoBase64: String,
    val latitude: Double,
    val longitude: Double,
    val criadoEm: Long
)
