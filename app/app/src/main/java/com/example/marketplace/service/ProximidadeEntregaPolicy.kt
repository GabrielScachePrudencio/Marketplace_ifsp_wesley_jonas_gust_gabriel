package com.example.marketplace.service

import android.content.Context

internal object ProximidadeEntregaPolicy {
    const val PRECISAO_MAXIMA_METROS = 20f
    const val LEITURAS_PARA_CONFIRMAR = 1
    const val RAIO_PADRAO_METROS = 10f

    private const val PREFS = "config_entrega"
    private const val CHAVE_RAIO = "raio_metros"

    val RAIO_PRESSETS_METROS = floatArrayOf(10f, 20f, 50f, 100f, 200f)

    @Volatile
    var raioMetros: Float = RAIO_PADRAO_METROS
        private set

    fun carregar(context: Context) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        raioMetros = prefs.getFloat(CHAVE_RAIO, RAIO_PADRAO_METROS)
            .coerceIn(RAIO_PRESSETS_METROS.first(), RAIO_PRESSETS_METROS.last())
    }

    fun definirRaio(context: Context, metros: Float) {
        raioMetros = metros
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(CHAVE_RAIO, metros)
            .apply()
    }

    fun proximoRaio(): Float {
        val indice = RAIO_PRESSETS_METROS.indexOfFirst { it == raioMetros }
        return RAIO_PRESSETS_METROS[(indice + 1).mod(RAIO_PRESSETS_METROS.size)]
    }

    fun precisaoMaximaMetros(): Float = minOf(raioMetros, PRECISAO_MAXIMA_METROS)

    fun amostraValida(distanciaMetros: Float, precisaoMetros: Float): Boolean {
        return distanciaMetros.isFinite() &&
                precisaoMetros.isFinite() &&
                distanciaMetros >= 0f &&
                precisaoMetros >= 0f &&
                distanciaMetros <= raioMetros &&
                precisaoMetros <= precisaoMaximaMetros()
    }

    fun podeNotificar(
        distanciaMetros: Float,
        precisaoMetros: Float,
        leiturasConsecutivas: Int
    ): Boolean {
        return amostraValida(distanciaMetros, precisaoMetros) &&
                leiturasConsecutivas >= LEITURAS_PARA_CONFIRMAR
    }
}
