package com.example.marketplace.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "rastreios_entrega")
data class RastreioEntrega(
    @PrimaryKey val id: String = "",
    val vendaId: String = "",
    val motoristaId: String = "",
    val pontoOrigemId: String = "",         // FK -> PontoReferencia

    val enderecoDestinoTexto: String = "",  // snapshot do endereço do comprador
    val destinoLat: Double = 0.0,
    val destinoLng: Double = 0.0,

    val ultimaLat: Double? = null,          // cache local, atualizado ao sair/entregar
    val ultimaLng: Double? = null,
    val ultimaAtualizacao: Long? = null,

    val posicaoNaFila: Int? = null,         // ordem na rota do motorista naquele dia

    val iniciadoEm: Long? = null,           // timestamp de quando saiu para entrega
    val finalizadoEm: Long? = null,         // timestamp de quando foi entregue

    val dataCriacao: LocalDateTime = LocalDateTime.now()
)