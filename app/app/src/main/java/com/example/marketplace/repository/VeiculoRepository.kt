package com.example.marketplace.data.repository

import com.example.marketplace.data.dao.PendenteSycronizacaoDao
import com.example.marketplace.data.dao.VeiculoDao
import com.example.marketplace.data.local.FirestoreDateConverter
import com.example.marketplace.domain.VeiculoRegras
import com.example.marketplace.model.PendenteSycronizacao
import com.example.marketplace.model.Veiculo
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

class VeiculoRepository(
    private val veiculoDao: VeiculoDao,
    private val pendenteSycronizacaoDao: PendenteSycronizacaoDao
) {

    private val colecao = FirebaseService.firestore.collection("veiculos")
    private val gson = Gson()

    suspend fun cadastrarVeiculo(
        motoristaId: String,
        tipo: String,
        marca: String,
        modelo: String,
        ano: Int,
        placa: String,
        cor: String
    ): Veiculo {
        VeiculoRegras.validar(motoristaId = motoristaId, placa = placa, ano = ano)

        val veiculo = Veiculo(
            id = colecao.document().id,
            motoristaId = motoristaId,
            tipo = tipo,
            marca = marca,
            modelo = modelo,
            ano = ano,
            placa = placa,
            cor = cor,
            dataCriacao = LocalDateTime.now()
        )

        val sucesso = salvarNoFirestore(veiculo)

        veiculoDao.insert(veiculo)

        if (!sucesso) {
            pendenteSycronizacaoDao.inserir(
                PendenteSycronizacao(
                    id = veiculo.id,
                    tipo = TipoPendenteSyncronizacao.VEICULOS,
                    operacao = OperacaoPendente.CREATE,
                    payloadJson = gson.toJson(veiculo)
                )
            )
        }

        return veiculo
    }

    suspend fun atualizarVeiculo(veiculo: Veiculo) {
        VeiculoRegras.validar(motoristaId = veiculo.motoristaId, placa = veiculo.placa, ano = veiculo.ano)

        val sucesso = salvarNoFirestore(veiculo)

        veiculoDao.update(veiculo)

        if (!sucesso) {
            pendenteSycronizacaoDao.inserir(
                PendenteSycronizacao(
                    id = veiculo.id,
                    tipo = TipoPendenteSyncronizacao.VEICULOS,
                    operacao = OperacaoPendente.UPDATE,
                    payloadJson = gson.toJson(veiculo)
                )
            )
        }
    }

    suspend fun excluirVeiculo(veiculo: Veiculo) {
        val sucesso = withTimeoutOrNull(5000) {
            try {
                colecao.document(veiculo.id).delete().await()
                true
            } catch (e: Exception) {
                false
            }
        } ?: false

        veiculoDao.deletar(veiculo)

        if (!sucesso) {
            pendenteSycronizacaoDao.inserir(
                PendenteSycronizacao(
                    id = veiculo.id,
                    tipo = TipoPendenteSyncronizacao.VEICULOS,
                    operacao = OperacaoPendente.DELETE,
                    payloadJson = gson.toJson(veiculo)
                )
            )
        }
    }

    /** Firestore primeiro; só cai pro Room se estiver offline/der erro */
    suspend fun buscarVeiculoPorId(id: String): Veiculo? {
        return try {
            val doc = colecao.document(id).get().await()
            if (!doc.exists()) return null
            val veiculo = veiculoDeDocumento(doc)
            veiculoDao.insert(veiculo)
            veiculo
        } catch (e: Exception) {
            veiculoDao.buscarPorId(id)
        }
    }

    /** Lista em tempo real DIRETO do Firestore */
    fun buscarVeiculos(): Flow<List<Veiculo>> = callbackFlow {
        val listener = colecao.addSnapshotListener { snapshot, erro ->
            if (erro != null) return@addSnapshotListener
            trySend(snapshot?.documents?.map { veiculoDeDocumento(it) } ?: emptyList())
        }
        awaitClose { listener.remove() }
    }

    /** Lista de um motorista, em tempo real DIRETO do Firestore */
    fun buscarVeiculosDoMotorista(motoristaId: String): Flow<List<Veiculo>> = callbackFlow {
        val listener = colecao
            .whereEqualTo("motoristaId", motoristaId)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null) return@addSnapshotListener
                trySend(snapshot?.documents?.map { veiculoDeDocumento(it) } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    private suspend fun salvarNoFirestore(veiculo: Veiculo): Boolean {
        val dados = mapOf(
            "motoristaId" to veiculo.motoristaId,
            "tipo" to veiculo.tipo,
            "marca" to veiculo.marca,
            "modelo" to veiculo.modelo,
            "ano" to veiculo.ano,
            "placa" to veiculo.placa,
            "cor" to veiculo.cor,
            "dataCriacao" to FirestoreDateConverter.paraMillis(veiculo.dataCriacao)
        )

        return withTimeoutOrNull(5000) {
            try {
                colecao.document(veiculo.id).set(dados).await()
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    private fun veiculoDeDocumento(doc: DocumentSnapshot): Veiculo {
        return Veiculo(
            id = doc.id,
            motoristaId = doc.getString("motoristaId") ?: "",
            tipo = doc.getString("tipo") ?: "",
            marca = doc.getString("marca") ?: "",
            modelo = doc.getString("modelo") ?: "",
            ano = (doc.getLong("ano") ?: 0L).toInt(),
            placa = doc.getString("placa") ?: "",
            cor = doc.getString("cor") ?: "",
            dataCriacao = FirestoreDateConverter.deMillis(doc.getLong("dataCriacao"))
        )
    }
}