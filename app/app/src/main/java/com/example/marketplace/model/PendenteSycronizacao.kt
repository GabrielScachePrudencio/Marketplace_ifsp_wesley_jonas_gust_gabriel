package com.example.marketplace.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.marketplace.model.enums.OperacaoPendente
import com.example.marketplace.model.enums.TipoPendenteSyncronizacao
import java.time.LocalDateTime

@Entity(tableName = "pendente_sycronizacao")
data class PendenteSycronizacao(
    @PrimaryKey val id: String,
    val tipo: TipoPendenteSyncronizacao,
    val operacao: OperacaoPendente,
    val payloadJson: String,
    val criadoEm: LocalDateTime = LocalDateTime.now()

)