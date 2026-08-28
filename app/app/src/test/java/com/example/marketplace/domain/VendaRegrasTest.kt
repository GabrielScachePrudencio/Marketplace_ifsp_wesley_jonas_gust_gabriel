package com.example.marketplace.domain

import com.example.marketplace.model.Produto
import com.example.marketplace.model.StatusEntrega
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VendaRegrasTest {

    @Test
    fun statusEntrega_conversoesEValoresValidos() {
        assertEquals(StatusEntrega.PENDENTE, StatusEntrega.deString("PENDENTE"))
        assertEquals(StatusEntrega.PRONTO_PARA_ENTREGA, StatusEntrega.deString("PRONTO_PARA_ENTREGA"))
        assertEquals(StatusEntrega.A_CAMINHO, StatusEntrega.deString("A_CAMINHO"))
        assertEquals(StatusEntrega.A_CAMINHO, StatusEntrega.deString("EM_TRANSPORTE"))
        assertEquals(StatusEntrega.ENTREGUE, StatusEntrega.deString("ENTREGUE"))
        assertEquals(StatusEntrega.CANCELADA, StatusEntrega.deString("CANCELADA"))
        assertEquals(StatusEntrega.PENDENTE, StatusEntrega.deString(null))
    }

    @Test
    fun negociador_podeAvancarERegredirStatusEntrePendenteEProntoParaEntrega() {
        // PENDENTE -> PRONTO_PARA_ENTREGA (avanço)
        VendaRegras.validarTransicao("PENDENTE", "PRONTO_PARA_ENTREGA", "negociador")

        // PRONTO_PARA_ENTREGA -> PENDENTE (regressão)
        VendaRegras.validarTransicao("PRONTO_PARA_ENTREGA", "PENDENTE", "negociador")

        // PENDENTE -> CANCELADA
        VendaRegras.validarTransicao("PENDENTE", "CANCELADA", "negociador")
    }

    @Test
    fun negociador_naoPodeAlterarParaACaminhoOuEntregue() {
        assertThrows(IllegalArgumentException::class.java) {
            VendaRegras.validarTransicao("PRONTO_PARA_ENTREGA", "A_CAMINHO", "negociador")
        }

        assertThrows(IllegalArgumentException::class.java) {
            VendaRegras.validarTransicao("A_CAMINHO", "ENTREGUE", "negociador")
        }
    }

    @Test
    fun motorista_podeAvancarERegredirStatusEntreProntoACaminhoEEntregue() {
        // PRONTO_PARA_ENTREGA -> A_CAMINHO (iniciar entrega)
        VendaRegras.validarTransicao("PRONTO_PARA_ENTREGA", "A_CAMINHO", "motorista")

        // A_CAMINHO -> ENTREGUE (concluir entrega)
        VendaRegras.validarTransicao("A_CAMINHO", "ENTREGUE", "motorista")

        // ENTREGUE -> A_CAMINHO (regressão: desfazer entrega)
        VendaRegras.validarTransicao("ENTREGUE", "A_CAMINHO", "motorista")

        // A_CAMINHO -> PRONTO_PARA_ENTREGA (regressão: cancelar coleta)
        VendaRegras.validarTransicao("A_CAMINHO", "PRONTO_PARA_ENTREGA", "motorista")
    }

    @Test
    fun motorista_naoPodeIniciarDePendente() {
        assertThrows(IllegalArgumentException::class.java) {
            VendaRegras.validarTransicao("PENDENTE", "A_CAMINHO", "motorista")
        }

        assertThrows(IllegalArgumentException::class.java) {
            VendaRegras.validarTransicao("PENDENTE", "PRONTO_PARA_ENTREGA", "motorista")
        }
    }

    @Test
    fun comprador_naoPodeAlterarStatus() {
        assertThrows(IllegalArgumentException::class.java) {
            VendaRegras.validarTransicao("PENDENTE", "PRONTO_PARA_ENTREGA", "comprador")
        }
    }
}
