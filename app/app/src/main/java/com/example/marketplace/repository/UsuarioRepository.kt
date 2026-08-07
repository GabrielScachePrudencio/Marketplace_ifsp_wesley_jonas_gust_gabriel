package com.example.marketplace.data.repository

import android.util.Log
import com.example.marketplace.service.FirebaseService
import com.example.marketplace.model.Usuario
import kotlinx.coroutines.tasks.await

class UsuarioRepository {

    suspend fun login(email: String, senha: String): Usuario {
        Log.d("MP_DEBUG", "Iniciando login com email=$email")

        val result = FirebaseService.auth
            .signInWithEmailAndPassword(email, senha)
            .await()

        Log.d("MP_DEBUG", "Login OK, uid=${result.user?.uid}")

        val uid = result.user?.uid ?: throw Exception("UID não encontrado após login")

        Log.d("MP_DEBUG", "Buscando documento em usuarios/$uid")

        val doc = FirebaseService.firestore
            .collection("usuarios")
            .document(uid)
            .get()
            .await()

        Log.d("MP_DEBUG", "Documento retornado. Existe? ${doc.exists()}")
        Log.d("MP_DEBUG", "Dados brutos: ${doc.data}")

        if (!doc.exists()) throw Exception("Perfil de usuário não encontrado no Firestore")

        val usuario = doc.toObject(Usuario::class.java)?.copy(uid = uid)
            ?: throw Exception("Erro ao converter dados do usuário")

        Log.d("MP_DEBUG", "Usuario convertido: $usuario")

        return usuario
    }

    fun logout() {
        FirebaseService.auth.signOut()
    }

    fun usuarioAtual() = FirebaseService.auth.currentUser
}