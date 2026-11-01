package com.example.marketplace.domain

import com.example.marketplace.model.Venda
import com.example.marketplace.model.enums.StatusEntrega

object AvaliacaoProdutoRegras {

    /** Só é possível avaliar um produto depois que o pedido foi entregue */
    val STATUS_QUE_PERMITEM_AVALIAR = setOf(StatusEntrega.ENTREGUE)

    const val MENSAGEM_PEDIDO_NAO_FINALIZADO =
        "Você só pode avaliar este produto depois que seu pedido for entregue"

    fun validar(nota: Int) {
        require(nota in 1..5) { "Nota deve estar entre 1 e 5" }
    }

    /** [pedidos] são os pedidos do comprador para o produto que ele quer avaliar */
    fun podeAvaliar(pedidos: List<Venda>): Boolean =
        pedidos.any { it.statusEntrega in STATUS_QUE_PERMITEM_AVALIAR }

    fun validarPedidos(pedidos: List<Venda>) {
        require(podeAvaliar(pedidos)) { MENSAGEM_PEDIDO_NAO_FINALIZADO }
    }
}
