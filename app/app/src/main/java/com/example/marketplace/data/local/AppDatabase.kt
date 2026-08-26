package com.example.marketplace.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.marketplace.data.dao.AvaliacaoProdutoDao
import com.example.marketplace.data.dao.ProdutoDao
import com.example.marketplace.data.dao.UsuarioDao
import com.example.marketplace.data.dao.VeiculoDao
import com.example.marketplace.data.dao.VendaDao
import com.example.marketplace.model.AvaliacaoProduto
import com.example.marketplace.model.Produto
import com.example.marketplace.model.Usuario
import com.example.marketplace.model.Veiculo
import com.example.marketplace.model.Venda

@Database(
    entities = [
        Usuario::class,
        Produto::class,
        AvaliacaoProduto::class,
        Veiculo::class,
        Venda::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun avaliacaoProdutoDao(): AvaliacaoProdutoDao
    abstract fun produtoDao(): ProdutoDao
    abstract fun usuarioDao(): UsuarioDao
    abstract fun veiculoDao(): VeiculoDao
    abstract fun vendaDao(): VendaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "MarketPlace-IFSP"
                )
                    .fallbackToDestructiveMigration() // apaga e recria se a versão mudar
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

}