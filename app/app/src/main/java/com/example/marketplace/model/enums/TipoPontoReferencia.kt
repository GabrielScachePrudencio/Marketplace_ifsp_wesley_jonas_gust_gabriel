package com.example.marketplace.model.enums

enum class TipoPontoReferencia {
    LOJA, FABRICA, DEPOSITO, OUTRO;

    companion object {
        fun deString(valor: String): TipoPontoReferencia =
            values().firstOrNull { it.name == valor } ?: OUTRO
    }
}