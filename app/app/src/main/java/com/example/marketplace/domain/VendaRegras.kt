package com.example.marketplace.domain

import com.example.marketplace.model.Produto

object VendaRegras {
    const val STATUS_INICIAL = "PENDENTE"

    private val transicoesPermitidas = mapOf(
        "PENDENTE" to setOf("EM_TRANSPORTE", "CANCELADA"),
        "EM_TRANSPORTE" to setOf("ENTREGUE")
    )

    fun validarCriacao(produto: Produto, quantidade: Int, vendedorId: String) {
        require(quantidade > 0) { "Quantidade deve ser maior que zero" }
        require(produto.vendedorId == vendedorId) { "vendedorId não corresponde ao produto" }
        require(produto.quantidade >= quantidade) { "Estoque insuficiente" }
    }

    fun calcularValorTotal(valorUnitario: Double, quantidade: Int): Double =
        valorUnitario * quantidade

    fun validarTransicao(statusAtual: String, novoStatus: String) {
        val permitido = transicoesPermitidas[statusAtual]?.contains(novoStatus) == true
        require(permitido) { "Transição de status inválida: $statusAtual -> $novoStatus" }
    }
}
