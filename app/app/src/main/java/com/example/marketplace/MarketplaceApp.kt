package com.example.marketplace

import android.app.Application
import android.util.Log
import com.example.marketplace.data.local.AppDatabase
import com.example.marketplace.data.sync.ConnectivityObserver
import com.example.marketplace.data.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MarketplaceApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        val db = AppDatabase.getDatabase(this)
        val syncManager = SyncManager(
            pendenteSycronizacaoDao = db.pendenteSycronizacaoDao(),
        )
        val connectivityObserver = ConnectivityObserver(this)

        // 1) reage IMEDIATAMENTE quando a internet volta
        appScope.launch {
            connectivityObserver.observe().collect { conectado ->
                if (conectado) {
                    Log.d("SyncManager", "Rede disponível, tentando sincronizar...")
                    syncManager.sincronizarPendentes()
                }
            }
        }

        // 2) rede de segurança: tenta de novo a cada X segundos, o app inteiro rodando
        appScope.launch {
            while (true) {
                delay(15_000) // a cada 15 segundos, ajuste como quiser
                val sincronizados = syncManager.sincronizarPendentes()
                if (sincronizados > 0) {
                    Log.d("SyncManager", "$sincronizados pendente(s) sincronizado(s) no loop periódico")
                }
            }
        }
    }
}