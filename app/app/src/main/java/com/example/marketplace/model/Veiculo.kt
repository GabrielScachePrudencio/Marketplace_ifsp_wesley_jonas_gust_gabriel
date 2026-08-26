package com.example.marketplace.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "veiculos")
data class Veiculo(
    @PrimaryKey val id: String = "",
    val motoristaId: String = "",
    val tipo: String = "",
    val marca: String = "",
    val modelo: String = "",
    val ano: Int = 0,
    val placa: String = "",
    val cor: String = "",
    val dataCriacao: LocalDateTime = LocalDateTime.now()

) 