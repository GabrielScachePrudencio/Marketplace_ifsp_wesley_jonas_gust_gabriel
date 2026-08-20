package com.example.marketplace.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "avalicaoProdutos")
data class AvaliacaoProduto(
    @PrimaryKey val id: String = "",
    val produtoId: String = "",
    val usuarioId: String = "",
    val nota: Int = 0,
    val comentario: String = "",
    val data: Long = 0L,
    val dataCriacao: LocalDateTime = LocalDateTime.now()

)