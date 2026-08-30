package com.example.marketplace.data.repository

import com.example.marketplace.data.dao.AvaliacaoProdutoDao
import com.example.marketplace.data.dao.PendenteSycronizacaoDao
import com.example.marketplace.data.local.FirestoreDateConverter
import com.example.marketplace.domain.AvaliacaoProdutoRegras
import com.example.marketplace.model.AvaliacaoProduto
import com.example.marketplace.model.PendenteSycronizacao
import com.example.marketplace.model.enums.OperacaoPendente
import com.example.marketplace.model.enums.TipoPendenteSyncronizacao
import com.example.marketplace.service.FirebaseService
import com.google.firebase.firestore.DocumentSnapshot
import com.google.gson.Gson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime

class AvaliacaoProdutoRepository(
    private val avaliacaoDao: AvaliacaoProdutoDao,
    private val pendenteSycronizacaoDao: PendenteSycronizacaoDao
) {

    private val colecao = FirebaseService.firestore.collection("avaliacoesProdutos")
    private val gson = Gson()

    suspend fun avaliarProduto(
        produtoId: String,
        usuarioId: String,
        nota: Int,
        comentario: String
    ): AvaliacaoProduto {
        AvaliacaoProdutoRegras.validar(nota)

        val id = "${produtoId}_$usuarioId"

        // Checagem de duplicidade: Firestore primeiro, cai pro local se offline
        val jaAvaliou = withTimeoutOrNull(5000) {
            try {
                colecao.document(id).get().await().exists()
            } catch (e: Exception) {
                null
            }
        } ?: (avaliacaoDao.buscarPorId(id) != null)

        if (jaAvaliou) throw Exception("Você já avaliou este produto")

        val avaliacao = AvaliacaoProduto(
            id = id,
            produtoId = produtoId,
            usuarioId = usuarioId,
            nota = nota,
            comentario = comentario,
            data = System.currentTimeMillis(),
            dataCriacao = LocalDateTime.now()
        )

        val sucesso = salvarNoFirestore(avaliacao)

        avaliacaoDao.insert(avaliacao)

        if (!sucesso) {
            pendenteSycronizacaoDao.inserir(
                PendenteSycronizacao(
                    id = avaliacao.id,
                    tipo = TipoPendenteSyncronizacao.AVALIACAO,
                    operacao = OperacaoPendente.CREATE,
                    payloadJson = gson.toJson(avaliacao)
                )
            )
        }

        return avaliacao
    }

    suspend fun excluirAvaliacao(avaliacao: AvaliacaoProduto) {
        val sucesso = withTimeoutOrNull(5000) {
            try {
                colecao.document(avaliacao.id).delete().await()
                true
            } catch (e: Exception) {
                false
            }
        } ?: false

        avaliacaoDao.deletar(avaliacao)

        if (!sucesso) {
            pendenteSycronizacaoDao.inserir(
                PendenteSycronizacao(
                    id = avaliacao.id,
                    tipo = TipoPendenteSyncronizacao.AVALIACAO,
                    operacao = OperacaoPendente.DELETE,
                    payloadJson = gson.toJson(avaliacao)
                )
            )
        }
    }

    /** Firestore primeiro; só cai pro Room se estiver offline/der erro */
    suspend fun buscarAvaliacaoPorId(id: String): AvaliacaoProduto? {
        return try {
            val doc = colecao.document(id).get().await()
            if (!doc.exists()) return null
            val avaliacao = avaliacaoDeDocumento(doc)
            avaliacaoDao.insert(avaliacao)
            avaliacao
        } catch (e: Exception) {
            avaliacaoDao.buscarPorId(id)
        }
    }

    /** Lista em tempo real DIRETO do Firestore */
    fun buscarAvaliacoesDoProduto(produtoId: String): Flow<List<AvaliacaoProduto>> = callbackFlow {
        val listener = colecao
            .whereEqualTo("produtoId", produtoId)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null) return@addSnapshotListener
                trySend(snapshot?.documents?.map { avaliacaoDeDocumento(it) } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    private suspend fun salvarNoFirestore(avaliacao: AvaliacaoProduto): Boolean {
        val dados = mapOf(
            "produtoId" to avaliacao.produtoId,
            "usuarioId" to avaliacao.usuarioId,
            "nota" to avaliacao.nota,
            "comentario" to avaliacao.comentario,
            "data" to avaliacao.data,
            "dataCriacao" to FirestoreDateConverter.paraMillis(avaliacao.dataCriacao)
        )

        return withTimeoutOrNull(5000) {
            try {
                colecao.document(avaliacao.id).set(dados).await()
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    private fun avaliacaoDeDocumento(doc: DocumentSnapshot): AvaliacaoProduto {
        return AvaliacaoProduto(
            id = doc.id,
            produtoId = doc.getString("produtoId") ?: "",
            usuarioId = doc.getString("usuarioId") ?: "",
            nota = (doc.getLong("nota") ?: 0L).toInt(),
            comentario = doc.getString("comentario") ?: "",
            data = doc.getLong("data") ?: 0L,
            dataCriacao = FirestoreDateConverter.deMillis(doc.getLong("dataCriacao"))
        )
    }
}