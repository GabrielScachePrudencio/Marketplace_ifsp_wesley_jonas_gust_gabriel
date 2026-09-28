package com.example.marketplace.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class Coordenadas(val latitude: Double, val longitude: Double)

data class EnderecoCep(
    val rua: String,
    val bairro: String,
    val cidade: String,
    val estado: String
)

object GeocodingService {

    // TODO: cole aqui a chave gerada em myprojects.geoapify.com
    const val API_KEY = "SUA_CHAVE_AQUI"

    private val client = OkHttpClient()

    suspend fun buscarCoordenadas(
        rua: String,
        numero: String,
        cidade: String,
        estado: String,
        cep: String
    ): Coordenadas? {
        val partes = listOf(rua, numero, cidade, estado, cep, "Brasil").filter { it.isNotBlank() }
        return buscarCoordenadasPorTexto(partes.joinToString(", "))
    }

    suspend fun buscarCoordenadasPorTexto(texto: String): Coordenadas? = withContext(Dispatchers.IO) {
        try {
            val codificado = URLEncoder.encode(texto, "UTF-8")
            val url = "https://api.geoapify.com/v1/geocode/search" +
                    "?text=$codificado&filter=countrycode:br&limit=1&apiKey=$API_KEY"

            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val corpo = response.body?.string() ?: return@withContext null
                android.util.Log.d("Geocoding", "HTTP ${response.code} -> $corpo")
                if (!response.isSuccessful) return@withContext null

                val features = JSONObject(corpo).getJSONArray("features")
                if (features.length() == 0) return@withContext null

                // Geoapify devolve [longitude, latitude], nessa ordem
                val c = features.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
                Coordenadas(latitude = c.getDouble(1), longitude = c.getDouble(0))
            }
        } catch (e: Exception) {
            android.util.Log.e("Geocoding", "Erro: ${e.message}")
            null
        }
    }

    /** Preenche rua/cidade/estado a partir do CEP (ViaCEP, gratuito e sem chave). */
    suspend fun buscarEnderecoPorCep(cep: String): EnderecoCep? = withContext(Dispatchers.IO) {
        try {
            val limpo = cep.filter { it.isDigit() }
            if (limpo.length != 8) return@withContext null

            val request = Request.Builder().url("https://viacep.com.br/ws/$limpo/json/").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string() ?: return@withContext null)
                if (json.optBoolean("erro", false)) return@withContext null

                EnderecoCep(
                    rua = json.optString("logradouro"),
                    bairro = json.optString("bairro"),
                    cidade = json.optString("localidade"),
                    estado = json.optString("uf")
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}