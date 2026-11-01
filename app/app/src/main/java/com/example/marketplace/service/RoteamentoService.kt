package com.example.marketplace.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PontoRota(val latitude: Double, val longitude: Double)

data class Rota(
    val pontos: List<PontoRota>,
    val distanciaMetros: Double,
    val duracaoSegundos: Double
) {
    val distanciaTexto: String
        get() = if (distanciaMetros >= 1000) "%.1f km".format(distanciaMetros / 1000) else "${distanciaMetros.toInt()} m"

    val duracaoTexto: String
        get() {
            val min = (duracaoSegundos / 60).toInt().coerceAtLeast(1)
            return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
        }
}

/**
 * Busca rotas de carro entre 2 ou mais paradas (na ordem recebida).
 * Usa o servidor público do OSRM (sem chave). Para produção, troque BASE_URL
 * por uma instância própria ou pela API de rotas do Geoapify (mesma chave dos tiles).
 */
object RoteamentoService {

    private const val BASE_URL = "https://router.project-osrm.org/route/v1/driving"

    suspend fun buscarRota(paradas: List<PontoRota>): Rota? = withContext(Dispatchers.IO) {
        if (paradas.size < 2) return@withContext null
        var conexao: HttpURLConnection? = null
        try {
            val coords = paradas.joinToString(";") {
                String.format(java.util.Locale.US, "%.6f,%.6f", it.longitude, it.latitude)
            }
            val url = URL("$BASE_URL/$coords?overview=full&geometries=geojson")
            conexao = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "MarketplaceApp/1.0")
            }
            if (conexao.responseCode != 200) return@withContext null

            val json = JSONObject(conexao.inputStream.bufferedReader().readText())
            if (json.optString("code") != "Ok") return@withContext null

            val rota = json.getJSONArray("routes").getJSONObject(0)
            val coordenadas = rota.getJSONObject("geometry").getJSONArray("coordinates")
            val pontos = List(coordenadas.length()) { i ->
                val par = coordenadas.getJSONArray(i)
                PontoRota(latitude = par.getDouble(1), longitude = par.getDouble(0)) // GeoJSON = [lng, lat]
            }

            Rota(
                pontos = pontos,
                distanciaMetros = rota.getDouble("distance"),
                duracaoSegundos = rota.getDouble("duration")
            )
        } catch (e: Exception) {
            null
        } finally {
            conexao?.disconnect()
        }
    }
}