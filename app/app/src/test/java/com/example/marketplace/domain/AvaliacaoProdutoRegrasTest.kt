package com.example.marketplace.domain

import com.example.marketplace.model.Venda
import com.example.marketplace.model.enums.StatusEntrega
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvaliacaoProdutoRegrasTest {

    private fun pedido(status: StatusEntrega) = Venda(status = status.name)

    @Test
    fun `nao pode avaliar sem pedido`() {
        assertFalse(AvaliacaoProdutoRegras.podeAvaliar(emptyList()))
    }

    @Test
    fun `nao pode avaliar com pedido pendente, em andamento ou cancelado`() {
        listOf(
            StatusEntrega.PENDENTE,
            StatusEntrega.PRONTO_PARA_ENTREGA,
            StatusEntrega.SAIU_PARA_ENTREGA,
            StatusEntrega.A_CAMINHO,
            StatusEntrega.CANCELADA
        ).forEach { status ->
            assertFalse(status.name, AvaliacaoProdutoRegras.podeAvaliar(listOf(pedido(status))))
        }
    }

    @Test
    fun `pode avaliar apenas com pedido entregue`() {
        assertTrue(AvaliacaoProdutoRegras.podeAvaliar(listOf(pedido(StatusEntrega.ENTREGUE))))
    }

    @Test
    fun `basta um pedido finalizado entre varios`() {
        val pedidos = listOf(pedido(StatusEntrega.PENDENTE), pedido(StatusEntrega.ENTREGUE))
        assertTrue(AvaliacaoProdutoRegras.podeAvaliar(pedidos))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validarPedidos lanca erro com pedido pendente`() {
        AvaliacaoProdutoRegras.validarPedidos(listOf(pedido(StatusEntrega.PENDENTE)))
    }
}
