package com.example.marketplace.data.repository

import android.util.Log
import com.example.marketplace.data.dao.UsuarioDao
import com.example.marketplace.service.FirebaseService
import com.example.marketplace.model.Usuario
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.ZoneOffset

class UsuarioRepository(private val usuarioDao: UsuarioDao) {

    suspend fun login(email: String, senha: String): Usuario {
        Log.d("MP_DEBUG", "Iniciando login com email=$email")

        val result = FirebaseService.auth
            .signInWithEmailAndPassword(email, senha)
            .await()

        Log.d("MP_DEBUG", "Login OK, uid=${result.user?.uid}")

        val uid = result.user?.uid ?: throw Exception("UID não encontrado após login")

        val usuario = buscarUsuarioPorUid(uid)

        Log.d("MP_DEBUG", "Usuario convertido: $usuario")

        return usuario
    }

    fun logout() {
        FirebaseService.auth.signOut()
    }

    fun usuarioAtual() = FirebaseService.auth.currentUser

    suspend fun carregarUsuarioAtual(): Usuario? {
        val uid = usuarioAtual()?.uid ?: return null

        return try {

            // 1. Primeiro procura no banco local
            val usuarioLocal =
                usuarioDao.buscarPorId(uid)

            if (usuarioLocal != null) {
                return usuarioLocal
            }

            // 2. Se não encontrou localmente,
            // busca no Firebase
            val usuarioFirebase =
                buscarUsuarioPorUid(uid)

            // 3. Salva no banco local
            usuarioDao.insert(usuarioFirebase)

            usuarioFirebase

        } catch (e: Exception) {

            Log.d(
                "MP_DEBUG",
                "Falha ao restaurar sessão: ${e.message}"
            )

            null
        }
    }

    suspend fun cadastrar(
        nome: String,
        email: String,
        senha: String,
        perfil: String,
        cpf: String,
        rua: String,
        numero: String,
        cidade: String,
        estado: String,
        cep: String
    ): Usuario {
        var result = FirebaseService.auth.createUserWithEmailAndPassword(email, senha).await()

        val uid = result.user?.uid ?: throw Exception("UID NAO CONTRANDO APOS CADASTRAR")

        val usuario = Usuario(
            uid = uid,
            nome = nome,
            email = email,
            perfil = perfil,
            cpf = cpf,
            rua = rua,
            numero = numero,
            cidade = cidade,
            estado = estado,
            cep = cep,
            dataCriacao = LocalDateTime.now()
        )

        val dadosFirestore = mapOf(
            "uid" to usuario.uid,
            "nome" to usuario.nome,
            "email" to usuario.email,
            "perfil" to usuario.perfil,
            "cpf" to usuario.cpf,
            "rua" to usuario.rua,
            "numero" to usuario.numero,
            "cidade" to usuario.cidade,
            "estado" to usuario.estado,
            "cep" to usuario.cep,
            "negocianteId" to usuario.negocianteId,
            "dataCriacao" to usuario.dataCriacao.toEpochSecond(ZoneOffset.UTC) * 1000
        )

        FirebaseService.firestore
            .collection("usuarios")
            .document(uid)
            .set(dadosFirestore)
            .await()

        usuarioDao.insert(usuario)

        return usuario
    }

    private suspend fun buscarUsuarioPorUid(uid: String): Usuario {
        Log.d("MP_DEBUG", "Buscando documento em usuarios/$uid")

        val doc = FirebaseService.firestore
            .collection("usuarios")
            .document(uid)
            .get()
            .await()

        Log.d("MP_DEBUG", "Documento retornado. Existe? ${doc.exists()}")
        Log.d("MP_DEBUG", "Dados brutos: ${doc.data}")

        if (!doc.exists()) throw Exception("Perfil de usuário não encontrado no Firestore")

        val dataCriacaoMillis = doc.getLong("dataCriacao")
        return Usuario(
            uid = uid,
            nome = doc.getString("nome") ?: "",
            email = doc.getString("email") ?: "",
            perfil = doc.getString("perfil") ?: "",
            cpf = doc.getString("cpf") ?: "",
            dataCriacao = dataCriacaoMillis?.let {
                LocalDateTime.ofEpochSecond(it / 1000, ((it % 1000) * 1_000_000).toInt(), ZoneOffset.UTC)
            } ?: LocalDateTime.now(),
            rua = doc.getString("rua") ?: "",
            numero = doc.getString("numero") ?: "",
            cidade = doc.getString("cidade") ?: "",
            estado = doc.getString("estado") ?: "",
            cep = doc.getString("cep") ?: "",
            negocianteId = doc.getString("negocianteId"),
        )
    }

    suspend fun cadastrar(nome: String, email: String, senha: String, perfil: String, cpf: String): Usuario{
        var result = FirebaseService.auth.createUserWithEmailAndPassword(email, senha).await()

        val uid = result.user?.uid ?: throw Exception("UID NAO CONTRANDO APOS CADASTRAR")

        val usuario = Usuario(
            uid = uid,
            nome = nome,
            email = email,
            perfil = perfil,
            cpf = cpf,
            dataCriacao = LocalDateTime.now()
        )

        val dadosFirestore = mapOf(
            "uid" to usuario.uid,
            "nome" to usuario.nome,
            "email" to usuario.email,
            "perfil" to usuario.perfil,
            "cpf" to usuario.cpf,
            "dataCriacao" to usuario.dataCriacao.toEpochSecond(ZoneOffset.UTC) * 1000
        )

        FirebaseService.firestore
            .collection("usuarios")
            .document(uid)
            .set(dadosFirestore)
            .await()

        usuarioDao.insert(usuario)

        return usuario
    }
    suspend fun listarNegociantes(): List<Usuario> {
        return FirebaseService.firestore
            .collection("usuarios")
            .whereEqualTo("perfil", "negociador")
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                Usuario(
                    uid = doc.id,
                    nome = doc.getString("nome") ?: "",
                    email = doc.getString("email") ?: "",
                    perfil = doc.getString("perfil") ?: ""
                )
            }
    }

    suspend fun vincularNegociante(motoristaUid: String, negocianteId: String) {
        FirebaseService.firestore
            .collection("usuarios")
            .document(motoristaUid)
            .update("negocianteId", negocianteId)
            .await()

        usuarioDao.vincularNegociante(motoristaUid, negocianteId)
    }
    suspend fun buscarUsuarioLocal(uid: String): Usuario? {
        return usuarioDao.buscarPorId(uid)
    }
}