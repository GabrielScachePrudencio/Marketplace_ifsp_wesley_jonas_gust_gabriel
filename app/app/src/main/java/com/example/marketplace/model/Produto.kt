package com.example.marketplace.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "produtos")
data class Produto(
    @PrimaryKey val id: String = "",
    val vendedorId: String = "",
    val titulo: String = "",
    val descricao: String = "",
    val categoria: String = "",
    val preco: Double = 0.0,
    val quantidade: Int = 0,
    val imagens: String = "",
    val dataCriacao: LocalDateTime = LocalDateTime.now()

)