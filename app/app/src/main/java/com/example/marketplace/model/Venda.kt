package com.example.marketplace.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.marketplace.model.enums.StatusEntrega
import java.time.LocalDateTime

@Entity(tableName = "vendas")
data class Venda(
    @PrimaryKey val id: String = "",
    val compradorId: String = "",
    val vendedorId: String = "",
    val motoristaId: String = "",
    val produtoId: String = "",
    val quantidade: Int = 0,
    val valorUnitario: Double = 0.0,
    val valorTotal: Double = 0.0,
    val status: String = StatusEntrega.PENDENTE.name,
    val data: Long = 0L,
    val dataCriacao: LocalDateTime = LocalDateTime.now()
) {
    val statusEntrega: StatusEntrega
        get() = StatusEntrega.deString(status)
}