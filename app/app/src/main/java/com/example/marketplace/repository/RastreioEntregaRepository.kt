package com.example.marketplace.repository

import com.example.marketplace.data.dao.PendenteSycronizacaoDao
import com.example.marketplace.data.dao.RastreioEntregaDao
import com.example.marketplace.data.local.FirestoreDateConverter
import com.example.marketplace.model.PendenteSycronizacao
import com.example.marketplace.model.RastreioEntrega
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

class RastreioEntregaRepository(
    private val rastreioEntregaDao: RastreioEntregaDao,
    private val pendenteSycronizacaoDao: PendenteSycronizacaoDao
) {

    private val colecao = FirebaseService.firestore.collection("rastreios_entrega")
    private val gson = Gson()

    // ===== ESCRITA =====

    suspend fun limparFilaDoMotorista(motoristaId: String) {
        val snapshot = colecao
            .whereEqualTo("motoristaId", motoristaId)
            .get()
            .await()

        snapshot.documents
            .map { rastreioDeDocumento(it) }
            .filter { it.iniciadoEm == null || it.finalizadoEm != null }
            .forEach { excluirRastreio(it) }
    }


        /** Chamado pelo negociante ao marcar a venda como PRONTO_PARA_ENTREGA */
    suspend fun criarRastreio(
        vendaId: String,
        pontoOrigemId: String,
        enderecoDestinoTexto: String,
        destinoLat: Double,
        destinoLng: Double
    ): RastreioEntrega {
        val existente = buscarPorVendaIdUmaVez(vendaId)
        if (existente != null) {
            val atualizado = existente.copy(
                pontoOrigemId = pontoOrigemId,
                enderecoDestinoTexto = enderecoDestinoTexto.ifBlank { existente.enderecoDestinoTexto },
                destinoLat = if (destinoLat != 0.0) destinoLat else existente.destinoLat,
                destinoLng = if (destinoLng != 0.0) destinoLng else existente.destinoLng,
                motoristaId = "",
                iniciadoEm = null,
                finalizadoEm = null
            )
            salvarEPersistir(atualizado, OperacaoPendente.UPDATE)
            return atualizado
        }

        val rastreio = RastreioEntrega(
            id = colecao.document().id,
            vendaId = vendaId,
            pontoOrigemId = pontoOrigemId,
            enderecoDestinoTexto = enderecoDestinoTexto,
            destinoLat = destinoLat,
            destinoLng = destinoLng,
            dataCriacao = LocalDateTime.now()
        )

        salvarEPersistir(rastreio, OperacaoPendente.CREATE)
        return rastreio
    }

    /** Chamado quando o motorista aceita e sai para entrega */
    suspend fun iniciarEntrega(rastreioId: String, motoristaId: String, posicaoNaFila: Int? = null) {
        val atual = buscarPorId(rastreioId) ?: return
        val pos = posicaoNaFila ?: atual.posicaoNaFila ?: run {
            val filaAtual = try {
                colecao.whereEqualTo("motoristaId", motoristaId).get().await()
                    .documents.map { rastreioDeDocumento(it) }
                    .filter { it.finalizadoEm == null && it.id != rastreioId }
            } catch (e: Exception) {
                rastreioEntregaDao.listarFilaDoMotorista(motoristaId)
                    .filter { it.finalizadoEm == null && it.id != rastreioId }
            }
            (filaAtual.maxOfOrNull { it.posicaoNaFila ?: 0 } ?: 0) + 1
        }
        val atualizado = atual.copy(
            motoristaId = motoristaId,
            iniciadoEm = atual.iniciadoEm ?: System.currentTimeMillis(),
            finalizadoEm = null,
            posicaoNaFila = pos
        )
        salvarEPersistir(atualizado, OperacaoPendente.UPDATE)
    }

    /** Desvincula o motorista caso a entrega volte para PRONTO_PARA_ENTREGA */
    suspend fun cancelarColetaEntrega(vendaId: String) {
        val atual = buscarPorVendaIdUmaVez(vendaId) ?: return
        val atualizado = atual.copy(
            motoristaId = "",
            iniciadoEm = null,
            finalizadoEm = null,
            posicaoNaFila = null
        )
        salvarEPersistir(atualizado, OperacaoPendente.UPDATE)
    }

    /** Busca o rastreio pelo campo vendaId (o ID do documento é aleatório) */
    suspend fun buscarPorVendaIdUmaVez(vendaId: String): RastreioEntrega? {
        return try {
            val snap = colecao.whereEqualTo("vendaId", vendaId).limit(1).get().await()
            val doc = snap.documents.firstOrNull() ?: return rastreioEntregaDao.buscarPorVendaId(vendaId)
            val rastreio = rastreioDeDocumento(doc)
            rastreioEntregaDao.insert(rastreio)
            rastreio
        } catch (e: Exception) {
            rastreioEntregaDao.buscarPorVendaId(vendaId)
        }
    }
    /** Reordena a fila de entregas do motorista */
    suspend fun atualizarPosicaoNaFila(rastreioId: String, novaPosicao: Int) {
        val atual = buscarPorId(rastreioId) ?: return
        val motoristaId = atual.motoristaId
        if (motoristaId.isNotBlank()) {
            val fila = try {
                colecao.whereEqualTo("motoristaId", motoristaId).get().await()
                    .documents.map { rastreioDeDocumento(it) }
                    .filter { it.finalizadoEm == null }
                    .sortedBy { it.posicaoNaFila ?: Int.MAX_VALUE }
            } catch (e: Exception) {
                rastreioEntregaDao.listarFilaDoMotorista(motoristaId).filter { it.finalizadoEm == null }
            }
            val outroNaMesmaPosicao = fila.find { it.id != rastreioId && it.posicaoNaFila == novaPosicao }
            if (outroNaMesmaPosicao != null) {
                salvarEPersistir(outroNaMesmaPosicao.copy(posicaoNaFila = atual.posicaoNaFila), OperacaoPendente.UPDATE)
            }
        }
        salvarEPersistir(atual.copy(posicaoNaFila = novaPosicao), OperacaoPendente.UPDATE)
    }

    /** Chamado quando o motorista marca a venda como ENTREGUE */
    suspend fun finalizarEntrega(rastreioId: String, ultimaLat: Double?, ultimaLng: Double?) {
        val atual = buscarPorId(rastreioId) ?: return
        val atualizado = atual.copy(
            finalizadoEm = System.currentTimeMillis(),
            ultimaLat = ultimaLat ?: atual.ultimaLat,
            ultimaLng = ultimaLng ?: atual.ultimaLng,
            ultimaAtualizacao = System.currentTimeMillis()
        )
        salvarEPersistir(atualizado, OperacaoPendente.UPDATE)
    }

    suspend fun excluirRastreio(rastreio: RastreioEntrega) {
        val sucesso = withTimeoutOrNull(5000) {
            try {
                colecao.document(rastreio.id).delete().await()
                true
            } catch (e: Exception) {
                false
            }
        } ?: false

        rastreioEntregaDao.deletar(rastreio)

        if (!sucesso) {
            pendenteSycronizacaoDao.inserir(
                PendenteSycronizacao(
                    id = rastreio.id,
                    tipo = TipoPendenteSyncronizacao.RASTREIOS_ENTREGA,
                    operacao = OperacaoPendente.DELETE,
                    payloadJson = gson.toJson(rastreio)
                )
            )
        }
    }

    // ===== LEITURA =====

    suspend fun buscarPorId(id: String): RastreioEntrega? {
        return try {
            val doc = colecao.document(id).get().await()
            if (!doc.exists()) return null
            val rastreio = rastreioDeDocumento(doc)
            rastreioEntregaDao.insert(rastreio)
            rastreio
        } catch (e: Exception) {
            rastreioEntregaDao.buscarPorId(id)
        }
    }

    /** Tela do comprador: observa em tempo real o rastreio de uma venda */
    fun buscarPorVendaId(vendaId: String): Flow<RastreioEntrega?> = callbackFlow {
        val listener = colecao
            .whereEqualTo("vendaId", vendaId)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null) return@addSnapshotListener
                val rastreio = snapshot?.documents?.firstOrNull()?.let { rastreioDeDocumento(it) }
                trySend(rastreio)
            }
        awaitClose { listener.remove() }
    }

    /** Tela do motorista: fila de entregas ordenada, em tempo real */
    fun buscarFilaDoMotorista(motoristaId: String): Flow<List<RastreioEntrega>> = callbackFlow {
        val listener = colecao
            .whereEqualTo("motoristaId", motoristaId)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null) return@addSnapshotListener
                val fila = snapshot?.documents
                    ?.map { rastreioDeDocumento(it) }
                    ?.filter { it.finalizadoEm == null }
                    ?.sortedBy { it.posicaoNaFila ?: Int.MAX_VALUE }
                    ?: emptyList()
                trySend(fila)
            }
        awaitClose { listener.remove() }
    }

    /** Tela do motorista: entregas disponíveis (PRONTO_PARA_ENTREGA sem motorista) */
    fun buscarEntregasDisponiveis(negociantesIds: List<String>): Flow<List<RastreioEntrega>> = callbackFlow {
        if (negociantesIds.isEmpty()) {
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }

        val listener = colecao
            .whereIn("pontoOrigemId", negociantesIds)
            .whereEqualTo("motoristaId", "")
            .addSnapshotListener { snapshot, erro ->
                if (erro != null) return@addSnapshotListener
                val disponiveis = snapshot?.documents?.map { rastreioDeDocumento(it) } ?: emptyList()
                trySend(disponiveis)
            }
        awaitClose { listener.remove() }
    }

    private suspend fun salvarEPersistir(rastreio: RastreioEntrega, operacao: OperacaoPendente) {
        val sucesso = salvarNoFirestore(rastreio)

        if (operacao == OperacaoPendente.CREATE) {
            rastreioEntregaDao.insert(rastreio)
        } else {
            rastreioEntregaDao.update(rastreio)
        }

        if (!sucesso) {
            pendenteSycronizacaoDao.inserir(
                PendenteSycronizacao(
                    id = rastreio.id,
                    tipo = TipoPendenteSyncronizacao.RASTREIOS_ENTREGA,
                    operacao = operacao,
                    payloadJson = gson.toJson(rastreio)
                )
            )
        }
    }

    private suspend fun salvarNoFirestore(rastreio: RastreioEntrega): Boolean {
        val dados = mapOf(
            "vendaId" to rastreio.vendaId,
            "motoristaId" to rastreio.motoristaId,
            "pontoOrigemId" to rastreio.pontoOrigemId,
            "enderecoDestinoTexto" to rastreio.enderecoDestinoTexto,
            "destinoLat" to rastreio.destinoLat,
            "destinoLng" to rastreio.destinoLng,
            "ultimaLat" to rastreio.ultimaLat,
            "ultimaLng" to rastreio.ultimaLng,
            "ultimaAtualizacao" to rastreio.ultimaAtualizacao,
            "posicaoNaFila" to rastreio.posicaoNaFila,
            "iniciadoEm" to rastreio.iniciadoEm,
            "finalizadoEm" to rastreio.finalizadoEm,
            "dataCriacao" to FirestoreDateConverter.paraMillis(rastreio.dataCriacao)
        )

        return withTimeoutOrNull(5000) {
            try {
                colecao.document(rastreio.id).set(dados).await()
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    private fun rastreioDeDocumento(doc: DocumentSnapshot): RastreioEntrega {
        return RastreioEntrega(
            id = doc.id,
            vendaId = doc.getString("vendaId") ?: "",
            motoristaId = doc.getString("motoristaId") ?: "",
            pontoOrigemId = doc.getString("pontoOrigemId") ?: "",
            enderecoDestinoTexto = doc.getString("enderecoDestinoTexto") ?: "",
            destinoLat = doc.getDouble("destinoLat") ?: 0.0,
            destinoLng = doc.getDouble("destinoLng") ?: 0.0,
            ultimaLat = doc.getDouble("ultimaLat"),
            ultimaLng = doc.getDouble("ultimaLng"),
            ultimaAtualizacao = doc.getLong("ultimaAtualizacao"),
            posicaoNaFila = doc.getLong("posicaoNaFila")?.toInt(),
            iniciadoEm = doc.getLong("iniciadoEm"),
            finalizadoEm = doc.getLong("finalizadoEm"),
            dataCriacao = FirestoreDateConverter.deMillis(doc.getLong("dataCriacao"))
        )
    }
}