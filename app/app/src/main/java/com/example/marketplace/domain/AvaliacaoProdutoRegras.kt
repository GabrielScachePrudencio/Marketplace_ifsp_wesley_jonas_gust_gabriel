package com.example.marketplace.domain

object AvaliacaoProdutoRegras {
    fun validar(nota: Int) {
        require(nota in 1..5) { "Nota deve estar entre 1 e 5" }
    }
}
