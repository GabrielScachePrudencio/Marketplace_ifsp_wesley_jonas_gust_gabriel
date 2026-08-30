package com.example.marketplace.data.repository

import com.example.marketplace.data.dao.PendenteSycronizacaoDao
import com.example.marketplace.data.dao.VendaDao
import com.example.marketplace.data.local.FirestoreDateConverter
import com.example.marketplace.domain.VendaRegras
import com.example.marketplace.model.PendenteSycronizacao
import com.example.marketplace.model.Venda
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

class VendaRepository(
    private val vendaDao: VendaDao,
    private val pendenteSycronizacaoDao: PendenteSycronizacaoDao,
    private val produtoRepository: ProdutoRepository
) {

    private val colecao = FirebaseService.firestore.collection("vendas")
    private val gson = Gson()

    suspend fun criarVenda(
        compradorId: String,
        vendedorId: String,
        motoristaId: String,
        produtoId: String,
        quantidade: Int
    ): Venda {
        val produto = produtoRepository.buscarProdutoPorId(produtoId)
            ?: throw Exception("Produto não encontrado")

        VendaRegras.validarCriacao(produto, quantidade, vendedorId)

        val valorUnitario = produto.preco
        val valorTotal = VendaRegras.calcularValorTotal(valorUnitario, quantidade)

        val venda = Venda(
            id = colecao.document().id,
            compradorId = compradorId,
            vendedorId = vendedorId,
            motoristaId = motoristaId,
            produtoId = produtoId,
            quantidade = quantidade,
            valorUnitario = valorUnitario,
            valorTotal = valorTotal,
            status = VendaRegras.STATUS_INICIAL,
            data = System.currentTimeMillis(),
            dataCriacao = LocalDateTime.now()
        )

        val sucesso = salvarNoFirestore(venda)

        vendaDao.insert(venda)

        if (!sucesso) {
            pendenteSycronizacaoDao.inserir(
                PendenteSycronizacao(
                    id = venda.id,
                    tipo = TipoPendenteSyncronizacao.VENDAS,
                    operacao = OperacaoPendente.CREATE,
                    payloadJson = gson.toJson(venda)
                )
            )
        }

        produtoRepository.atualizarProduto(produto.copy(quantidade = produto.quantidade - quantidade))

        return venda
    }

    suspend fun atualizarStatusVenda(
        id: String,
        novoStatus: String,
        perfil: String? = null,
        motoristaId: String? = null
    ): Venda {
        val venda = buscarVendaPorId(id) ?: throw Exception("Venda não encontrada")
        VendaRegras.validarTransicao(venda.status, novoStatus, perfil)

        val novoMotoristaId = if (!motoristaId.isNullOrBlank()) motoristaId else venda.motoristaId

        val atualizada = venda.copy(
            status = novoStatus,
            motoristaId = novoMotoristaId
        )

        val sucesso = salvarNoFirestore(atualizada)

        vendaDao.update(atualizada)

        if (!sucesso) {
            pendenteSycronizacaoDao.inserir(
                PendenteSycronizacao(
                    id = atualizada.id,
                    tipo = TipoPendenteSyncronizacao.VENDAS,
                    operacao = OperacaoPendente.UPDATE,
                    payloadJson = gson.toJson(atualizada)
                )
            )
        }

        return atualizada
    }

    /** Firestore primeiro; só cai pro Room se estiver offline/der erro */
    suspend fun buscarVendaPorId(id: String): Venda? {
        return try {
            val doc = colecao.document(id).get().await()
            if (!doc.exists()) return null
            val venda = vendaDeDocumento(doc)
            vendaDao.insert(venda)
            venda
        } catch (e: Exception) {
            vendaDao.buscarPorId(id)
        }
    }

    /** Lista em tempo real DIRETO do Firestore */
    fun buscarVendas(): Flow<List<Venda>> = callbackFlow {
        val listener = colecao.addSnapshotListener { snapshot, erro ->
            if (erro != null) return@addSnapshotListener
            trySend(snapshot?.documents?.map { vendaDeDocumento(it) } ?: emptyList())
        }
        awaitClose { listener.remove() }
    }

    private suspend fun salvarNoFirestore(venda: Venda): Boolean {
        val dados = mapOf(
            "compradorId" to venda.compradorId,
            "vendedorId" to venda.vendedorId,
            "motoristaId" to venda.motoristaId,
            "produtoId" to venda.produtoId,
            "quantidade" to venda.quantidade,
            "valorUnitario" to venda.valorUnitario,
            "valorTotal" to venda.valorTotal,
            "status" to venda.status,
            "data" to venda.data,
            "dataCriacao" to FirestoreDateConverter.paraMillis(venda.dataCriacao)
        )

        return withTimeoutOrNull(5000) {
            try {
                colecao.document(venda.id).set(dados).await()
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    private fun vendaDeDocumento(doc: DocumentSnapshot): Venda {
        return Venda(
            id = doc.id,
            compradorId = doc.getString("compradorId") ?: "",
            vendedorId = doc.getString("vendedorId") ?: "",
            motoristaId = doc.getString("motoristaId") ?: "",
            produtoId = doc.getString("produtoId") ?: "",
            quantidade = (doc.getLong("quantidade") ?: 0L).toInt(),
            valorUnitario = doc.getDouble("valorUnitario") ?: 0.0,
            valorTotal = doc.getDouble("valorTotal") ?: 0.0,
            status = doc.getString("status") ?: VendaRegras.STATUS_INICIAL,
            data = doc.getLong("data") ?: 0L,
            dataCriacao = FirestoreDateConverter.deMillis(doc.getLong("dataCriacao"))
        )
    }
}