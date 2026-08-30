package com.example.marketplace.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "pendente_sycronizacao")
data class PendenteSycronizacao(
    @PrimaryKey val id: String,
    val tipo: String,
    val operacao: String,
    val payloadJson: String,
    val criadoEm: LocalDateTime = LocalDateTime.now()

)