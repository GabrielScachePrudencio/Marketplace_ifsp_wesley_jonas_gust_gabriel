package com.example.marketplace.data.repository

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
import java.time.LocalDateTime

class ProdutoRepository(
    private val produtoDao: ProdutoDao,
    private val pendenteSycronizacaoDao: PendenteSycronizacaoDao
) {

    private val colecao = FirebaseService.firestore.collection("produtos")
    private val gson = Gson()

    // ===== ESCRITA — sem mudanças, continua igual (já está funcionando) =====

    suspend fun criarProduto(
        vendedorId: String,
        titulo: String,
        descricao: String,
        categoria: String,
        preco: Double,
        quantidade: Int,
        imagens: String
    ): Produto {
        ProdutoRegras.validar(titulo, descricao, categoria, preco, quantidade, vendedorId)

        val produto = Produto(
            id = colecao.document().id,
            vendedorId = vendedorId,
            titulo = titulo,
            descricao = descricao,
            categoria = categoria,
            preco = preco,
            quantidade = quantidade,
            imagens = imagens,
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

    private suspend fun salvarNoFirestore(produto: Produto): Boolean {
        val dados = mapOf(
            "vendedorId" to produto.vendedorId,
            "titulo" to produto.titulo,
            "descricao" to produto.descricao,
            "categoria" to produto.categoria,
            "preco" to produto.preco,
            "quantidade" to produto.quantidade,
            "imagens" to produto.imagens,
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
        return Produto(
            id = doc.id,
            vendedorId = doc.getString("vendedorId") ?: "",
            titulo = doc.getString("titulo") ?: "",
            descricao = doc.getString("descricao") ?: "",
            categoria = doc.getString("categoria") ?: "",
            preco = doc.getDouble("preco") ?: 0.0,
            quantidade = (doc.getLong("quantidade") ?: 0L).toInt(),
            imagens = doc.getString("imagens") ?: "",
            dataCriacao = FirestoreDateConverter.deMillis(doc.getLong("dataCriacao"))
        )
    }
}