package com.example.marketplace.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximidadeEntregaPolicyTest {

    @Test
    fun notifiesAfterOneAccurateFixInsideTheRadius() {
        assertTrue(ProximidadeEntregaPolicy.podeNotificar(5f, 4f, 1))
        assertFalse(ProximidadeEntregaPolicy.podeNotificar(5f, 4f, 0))
    }

    @Test
    fun rejectsOutOfRadiusOrInaccurateGpsFixes() {
        assertFalse(ProximidadeEntregaPolicy.podeNotificar(10.1f, 2f, 2))
        assertFalse(ProximidadeEntregaPolicy.podeNotificar(5f, 10.1f, 2))
    }
}
