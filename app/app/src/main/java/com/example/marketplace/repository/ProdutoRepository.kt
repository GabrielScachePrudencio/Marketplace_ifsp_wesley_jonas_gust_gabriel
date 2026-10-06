package com.example.marketplace.repository

import com.example.marketplace.data.dao.PendenteSycronizacaoDao
import com.example.marketplace.data.dao.ProdutoDao
import com.example.marketplace.data.local.FirestoreDateConverter
import com.example.marketplace.domain.ProdutoRegras
import com.example.marketplace.model.PendenteSycronizacao
import com.example.marketplace.model.Produto
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
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime

class ProdutoRepository(
    private val produtoDao: ProdutoDao,
    private val pendenteSycronizacaoDao: PendenteSycronizacaoDao
) {

    private val colecao = FirebaseService.firestore.collection("produtos")
    private val colecaoVideos = FirebaseService.firestore.collection("videosProdutos")
    private val gson = Gson()

    // ===== ESCRITA — sem mudanças, continua igual (já está funcionando) =====

    suspend fun criarProduto(
        vendedorId: String,
        titulo: String,
        descricao: String,
        categoria: String,
        preco: Double,
        quantidade: Int,
        imagens: List<String> = emptyList(),
        video: ByteArray? = null
    ): Produto {
        ProdutoRegras.validar(titulo, descricao, categoria, preco, quantidade, vendedorId)
        if (video != null) ProdutoRegras.validarVideo(video.size)

        val id = colecao.document().id
        // O vídeo precisa de internet: envia antes de salvar o produto para não deixar referência quebrada
        val videoPartes = if (video != null) salvarVideo(id, video) else 0

        val produto = Produto(
            id = id,
            vendedorId = vendedorId,
            titulo = titulo,
            descricao = descricao,
            categoria = categoria,
            preco = preco,
            quantidade = quantidade,
            imagens = imagens,
            videoPartes = videoPartes,
            dataCriacao = LocalDateTime.now()
        )

        val sucesso = salvarNoFirestore(produto)
        produtoDao.insert(produto)

        if (!sucesso) {
            pendenteSycronizacaoDao.inserir(
                PendenteSycronizacao(
                    id = produto.id,
                    tipo = TipoPendenteSyncronizacao.PRODUTOS,
                    operacao = OperacaoPendente.CREATE,
                    payloadJson = gson.toJson(produto)
                )
            )
        }

        return produto
    }

    suspend fun criarProduto(
        vendedorId: String,
        titulo: String,
        descricao: String,
        categoria: String,
        preco: Double,
        quantidade: Int,
        imagens: String
    ): Produto {
        val lista = if (imagens.isBlank()) emptyList() else listOf(imagens)
        return criarProduto(vendedorId, titulo, descricao, categoria, preco, quantidade, lista)
    }

    suspend fun atualizarProduto(produto: Produto) {
        ProdutoRegras.validar(produto.titulo, produto.descricao, produto.categoria, produto.preco, produto.quantidade, produto.vendedorId)

        val sucesso = salvarNoFirestore(produto)
        produtoDao.update(produto)

        if (!sucesso) {
            pendenteSycronizacaoDao.inserir(
                PendenteSycronizacao(
                    id = produto.id,
                    tipo = TipoPendenteSyncronizacao.PRODUTOS,
                    operacao = OperacaoPendente.UPDATE,
                    payloadJson = gson.toJson(produto)
                )
            )
        }
    }

    suspend fun excluirProduto(produto: Produto) {
        val sucesso = withTimeoutOrNull(5000) {
            try {
                colecao.document(produto.id).delete().await()
                true
            } catch (e: Exception) {
                false
            }
        } ?: false

        produtoDao.deletar(produto)
        if (produto.videoPartes > 0) excluirVideo(produto.id)

        if (!sucesso) {
            pendenteSycronizacaoDao.inserir(
                PendenteSycronizacao(
                    id = produto.id,
                    tipo = TipoPendenteSyncronizacao.PRODUTOS,
                    operacao = OperacaoPendente.DELETE,
                    payloadJson = gson.toJson(produto)
                )
            )
        }
    }

    // ===== LEITURA — agora sempre tenta Firebase primeiro =====

    /** Busca por id: Firestore primeiro; só cai pro Room se estiver offline/der erro */
    suspend fun buscarProdutoPorId(id: String): Produto? {
        return try {
            val doc = colecao.document(id).get().await()
            if (!doc.exists()) return null
            val produto = produtoDeDocumento(doc)
            produtoDao.insert(produto) // atualiza cache local
            produto
        } catch (e: Exception) {
            // offline: usa o que tiver salvo localmente como último recurso
            produtoDao.buscarPorId(id)
        }
    }

    /** Lista em tempo real DIRETO do Firestore — não depende do Room pra exibir */
    fun buscarProdutos(): Flow<List<Produto>> = callbackFlow {
        val listener = colecao.addSnapshotListener { snapshot, erro ->
            if (erro != null) {
                // Firestore indisponível: não fecha o flow, só ignora esse evento
                return@addSnapshotListener
            }
            val produtos = snapshot?.documents?.map { produtoDeDocumento(it) } ?: emptyList()
            trySend(produtos)
        }
        awaitClose { listener.remove() }
    }

    // ===== VÍDEO =====
    // Firestore limita cada documento a 1 MB, então o vídeo é dividido em partes Base64

    /** Salva o vídeo em partes e retorna quantas partes foram gravadas */
    private suspend fun salvarVideo(produtoId: String, video: ByteArray): Int {
        val partes = (video.indices step TAMANHO_PARTE_VIDEO).map { inicio ->
            video.copyOfRange(inicio, minOf(inicio + TAMANHO_PARTE_VIDEO, video.size))
        }
        val enviado = withTimeoutOrNull(120_000) {
            try {
                partes.forEachIndexed { indice, parte ->
                    colecaoVideos.document("${produtoId}_$indice").set(
                        mapOf(
                            "produtoId" to produtoId,
                            "indice" to indice,
                            "dados" to Base64.encodeToString(parte, Base64.NO_WRAP)
                        )
                    ).await()
                }
                true
            } catch (e: Exception) {
                false
            }
        } ?: false

        if (!enviado) throw Exception("Não foi possível enviar o vídeo. Verifique sua conexão e tente novamente.")
        return partes.size
    }

    /** Baixa e remonta o vídeo do produto; retorna null se não houver ou se estiver offline */
    suspend fun carregarVideo(produto: Produto): ByteArray? {
        if (produto.videoPartes <= 0) return null
        return withTimeoutOrNull(60_000) {
            try {
                val documentos = colecaoVideos.whereEqualTo("produtoId", produto.id).get().await()
                    .documents.sortedBy { it.getLong("indice") ?: 0L }
                if (documentos.size != produto.videoPartes) return@withTimeoutOrNull null

                val saida = ByteArrayOutputStream()
                documentos.forEach { doc ->
                    saida.write(Base64.decode(doc.getString("dados") ?: "", Base64.NO_WRAP))
                }
                saida.toByteArray()
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun excluirVideo(produtoId: String) {
        withTimeoutOrNull(10_000) {
            try {
                colecaoVideos.whereEqualTo("produtoId", produtoId).get().await()
                    .documents.forEach { it.reference.delete().await() }
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun salvarNoFirestore(produto: Produto): Boolean {
        val dados = mapOf(
            "vendedorId" to produto.vendedorId,
            "titulo" to produto.titulo,
            "descricao" to produto.descricao,
            "categoria" to produto.categoria,
            "preco" to produto.preco,
            "quantidade" to produto.quantidade,
            "imagens" to produto.imagens,
            "videoPartes" to produto.videoPartes,
            "dataCriacao" to FirestoreDateConverter.paraMillis(produto.dataCriacao)
        )

        return withTimeoutOrNull(5000) {
            try {
                colecao.document(produto.id).set(dados).await()
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    private fun produtoDeDocumento(doc: DocumentSnapshot): Produto {
        @Suppress("UNCHECKED_CAST")
        val imagensList = (doc.get("imagens") as? List<*>)?.mapNotNull { it as? String }
            ?: doc.getString("imagens")?.let { str ->
                if (str.isNotBlank()) listOf(str) else emptyList()
            } ?: emptyList()

        return Produto(
            id = doc.id,
            vendedorId = doc.getString("vendedorId") ?: "",
            titulo = doc.getString("titulo") ?: "",
            descricao = doc.getString("descricao") ?: "",
            categoria = doc.getString("categoria") ?: "",
            preco = doc.getDouble("preco") ?: 0.0,
            quantidade = (doc.getLong("quantidade") ?: 0L).toInt(),
            imagens = imagensList,
            videoPartes = (doc.getLong("videoPartes") ?: 0L).toInt(),
            dataCriacao = FirestoreDateConverter.deMillis(doc.getLong("dataCriacao"))
        )
    }

    companion object {
        /** 512 KB por parte (~700 KB em Base64), abaixo do limite de 1 MB do Firestore */
        private const val TAMANHO_PARTE_VIDEO = 512 * 1024
    }
}
