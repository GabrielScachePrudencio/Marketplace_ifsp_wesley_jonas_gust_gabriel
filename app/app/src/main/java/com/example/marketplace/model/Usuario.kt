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
    val dataCriacao: LocalDateTime = LocalDateTime.now()
)