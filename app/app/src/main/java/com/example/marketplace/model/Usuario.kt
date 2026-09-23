package com.example.marketplace.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "usuarios")
data class Usuario(
    @PrimaryKey val uid: String = "",
    val nome: String = "",
    val email: String = "",
    val perfil: String = "",
    val cpf: String = "",
    val rua: String = "",
    val numero: String = "",
    val cidade: String = "",
    val estado: String = "",
    val cep: String = "",
    val negocianteId: String? = null,
    val negociantesIds: List<String> = emptyList(),
    val fotoPerfil: String = "",
    val dataCriacao: LocalDateTime = LocalDateTime.now()
) {
    fun todosNegociantesIds(): List<String> {
        val lista = ArrayList<String>()
        lista.addAll(negociantesIds)
        if (!negocianteId.isNullOrBlank() && !lista.contains(negocianteId)) {
            lista.add(negocianteId)
        }
        return lista
    }
}