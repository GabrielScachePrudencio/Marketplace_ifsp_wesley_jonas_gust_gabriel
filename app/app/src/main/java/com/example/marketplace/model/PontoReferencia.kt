package com.example.marketplace.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.marketplace.model.enums.TipoPontoReferencia
import java.time.LocalDateTime

@Entity(tableName = "pontos_referencia")
data class PontoReferencia(
    @PrimaryKey val id: String = "",
    val negocianteId: String = "",
    val nome: String = "",
    val tipo: String = TipoPontoReferencia.LOJA.name,
    val rua: String = "",
    val numero: String = "",
    val cidade: String = "",
    val estado: String = "",
    val cep: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val principal: Boolean = false,
    val dataCriacao: LocalDateTime = LocalDateTime.now()
) {
    val tipoEnum: TipoPontoReferencia
        get() = TipoPontoReferencia.deString(tipo)
}