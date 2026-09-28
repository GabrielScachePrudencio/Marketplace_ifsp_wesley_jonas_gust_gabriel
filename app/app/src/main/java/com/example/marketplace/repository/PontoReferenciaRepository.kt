package com.example.marketplace.repository

import com.example.marketplace.data.dao.PendenteSycronizacaoDao
import com.example.marketplace.data.dao.PontoReferenciaDao
import com.example.marketplace.data.local.FirestoreDateConverter
import com.example.marketplace.model.PendenteSycronizacao
import com.example.marketplace.model.PontoReferencia
import com.example.marketplace.model.enums.OperacaoPendente
import com.example.marketplace.model.enums.TipoPendenteSyncronizacao
import com.example.marketplace.model.enums.TipoPontoReferencia
import com.example.marketplace.service.FirebaseService
import com.google.firebase.firestore.DocumentSnapshot
import com.google.gson.Gson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime

class PontoReferenciaRepository(
    private val pontoReferenciaDao: PontoReferenciaDao,
    private val pendenteSycronizacaoDao: PendenteSycronizacaoDao
) {

    private val colecao = FirebaseService.firestore.collection("pontos_referencia")
    private val gson = Gson()

    // ===== ESCRITA =====

    suspend fun criarPonto(
        negocianteId: String,
        nome: String,
        tipo: TipoPontoReferencia,
        rua: String,
        numero: String,
        cidade: String,
        estado: String,
        cep: String,
        latitude: Double,
        longitude: Double,
        principal: Boolean = false
    ): PontoReferencia {
        val ponto = PontoReferencia(
            id = colecao.document().id,
            negocianteId = negocianteId,
            nome = nome,
            tipo = tipo.name,
            rua = rua,
            numero = numero,
            cidade = cidade,
            estado = estado,
            cep = cep,
            latitude = latitude,
            longitude = longitude,
            principal = principal,
            dataCriacao = LocalDateTime.now()
        )

        val sucesso = salvarNoFirestore(ponto)
        pontoReferenciaDao.insert(ponto)

        if (!sucesso) {
            pendenteSycronizacaoDao.inserir(
                PendenteSycronizacao(
                    id = ponto.id,
                    tipo = TipoPendenteSyncronizacao.PONTOS_REFERENCIA,
                    operacao = OperacaoPendente.CREATE,
                    payloadJson = gson.toJson(ponto)
                )
            )
        }

        return ponto
    }

    suspend fun atualizarPonto(ponto: PontoReferencia) {
        val sucesso = salvarNoFirestore(ponto)
        pontoReferenciaDao.update(ponto)

        if (!sucesso) {
            pendenteSycronizacaoDao.inserir(
                PendenteSycronizacao(
                    id = ponto.id,
                    tipo = TipoPendenteSyncronizacao.PONTOS_REFERENCIA,
                    operacao = OperacaoPendente.UPDATE,
                    payloadJson = gson.toJson(ponto)
                )
            )
        }
    }

    suspend fun excluirPonto(ponto: PontoReferencia) {
        val sucesso = withTimeoutOrNull(5000) {
            try {
                colecao.document(ponto.id).delete().await()
                true
            } catch (e: Exception) {
                false
            }
        } ?: false

        pontoReferenciaDao.deletar(ponto)

        if (!sucesso) {
            pendenteSycronizacaoDao.inserir(
                PendenteSycronizacao(
                    id = ponto.id,
                    tipo = TipoPendenteSyncronizacao.PONTOS_REFERENCIA,
                    operacao = OperacaoPendente.DELETE,
                    payloadJson = gson.toJson(ponto)
                )
            )
        }
    }

    // ===== LEITURA =====

    suspend fun buscarPontoPorId(id: String): PontoReferencia? {
        return try {
            val doc = colecao.document(id).get().await()
            if (!doc.exists()) return null
            val ponto = pontoDeDocumento(doc)
            pontoReferenciaDao.insert(ponto)
            ponto
        } catch (e: Exception) {
            pontoReferenciaDao.buscarPorId(id)
        }
    }

    /** Lista em tempo real dos pontos de um negociante — pro CRUD dele */
    fun buscarPontosDoNegociante(negocianteId: String): Flow<List<PontoReferencia>> = callbackFlow {
        val listener = colecao
            .whereEqualTo("negocianteId", negocianteId)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null) return@addSnapshotListener
                val pontos = snapshot?.documents?.map { pontoDeDocumento(it) } ?: emptyList()
                trySend(pontos)
            }
        awaitClose { listener.remove() }
    }

    private suspend fun salvarNoFirestore(ponto: PontoReferencia): Boolean {
        val dados = mapOf(
            "negocianteId" to ponto.negocianteId,
            "nome" to ponto.nome,
            "tipo" to ponto.tipo,
            "rua" to ponto.rua,
            "numero" to ponto.numero,
            "cidade" to ponto.cidade,
            "estado" to ponto.estado,
            "cep" to ponto.cep,
            "latitude" to ponto.latitude,
            "longitude" to ponto.longitude,
            "principal" to ponto.principal,
            "dataCriacao" to FirestoreDateConverter.paraMillis(ponto.dataCriacao)
        )

        return withTimeoutOrNull(5000) {
            try {
                colecao.document(ponto.id).set(dados).await()
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    private fun pontoDeDocumento(doc: DocumentSnapshot): PontoReferencia {
        return PontoReferencia(
            id = doc.id,
            negocianteId = doc.getString("negocianteId") ?: "",
            nome = doc.getString("nome") ?: "",
            tipo = doc.getString("tipo") ?: TipoPontoReferencia.OUTRO.name,
            rua = doc.getString("rua") ?: "",
            numero = doc.getString("numero") ?: "",
            cidade = doc.getString("cidade") ?: "",
            estado = doc.getString("estado") ?: "",
            cep = doc.getString("cep") ?: "",
            latitude = doc.getDouble("latitude") ?: 0.0,
            longitude = doc.getDouble("longitude") ?: 0.0,
            principal = doc.getBoolean("principal") ?: false,
            dataCriacao = FirestoreDateConverter.deMillis(doc.getLong("dataCriacao"))
        )
    }
}