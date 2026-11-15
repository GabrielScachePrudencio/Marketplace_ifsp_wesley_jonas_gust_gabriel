package com.example.marketplace.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.marketplace.MainActivity
import com.example.marketplace.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class EntregaMessagingService : FirebaseMessagingService() {

    override fun onCreate() {
        super.onCreate()
        criarCanal(this)
    }

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                registrarToken(uid, token)
            } catch (e: Exception) {
                Log.e(TAG, "Não foi possível registrar o novo token FCM.", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        LogNotificacoesEntrega.registrar(
            "FCM",
            "Mensagem remota recebida. tipo=${message.data["tipo"]} vendaId=${message.data["vendaId"]}"
        )
        val vendaId = message.data["vendaId"]
        if (vendaId.isNullOrBlank()) {
            LogNotificacoesEntrega.registrar("FCM", "Mensagem ignorada: vendaId ausente.")
            return
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val titulo = message.data["titulo"]
        val mensagem = message.data["mensagem"]
        when (message.data["tipo"]) {
            TIPO_ENTREGADOR_PROXIMO -> mostrarNotificacaoProximidade(
                context = this,
                vendaId = vendaId,
                titulo = titulo ?: "Seu entregador está chegando",
                mensagem = mensagem
                    ?: "O entregador está a menos de ${ProximidadeEntregaPolicy.raioMetros.toInt()} metros do endereço de entrega.",
                uid = uid
            )
            TIPO_ENTREGA_INICIADA -> mostrarNotificacaoEntregaIniciada(
                context = this,
                vendaId = vendaId,
                titulo = titulo ?: "O motorista iniciou a entrega",
                mensagem = mensagem
                    ?: "Toque para acompanhar a localização e a previsão de chegada.",
            )
        }
    }

    companion object {
        const val ACTION_ABRIR_RASTREAMENTO = "com.example.marketplace.ABRIR_RASTREAMENTO"
        const val EXTRA_VENDA_ID = "vendaId"
        const val TIPO_ENTREGADOR_PROXIMO = "ENTREGADOR_PROXIMO"
        const val TIPO_ENTREGA_INICIADA = "ENTREGA_INICIADA"

        private const val TAG = "EntregaNotifications"
        private const val CHANNEL_ID = "entregas_proximas"
        private const val PREFS_NAME = "notificacoes_entrega_prefs"

        fun criarCanal(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val canal = NotificationChannel(
                    CHANNEL_ID,
                    "Avisos de entrega",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Avisos de início e proximidade das entregas."
                    enableVibration(true)
                }
                context.getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(canal)
            }
        }

        fun jaFoiNotificada(context: Context, uid: String, vendaId: String): Boolean {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val set = prefs.getStringSet(chavePrefs(uid), emptySet()).orEmpty()
            return vendaId in set
        }

        fun jaFoiNotificadaInicio(context: Context, uid: String, vendaId: String): Boolean {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return vendaId in prefs.getStringSet(chavePrefsInicio(uid), emptySet()).orEmpty()
        }

        fun marcarInicioComoNotificada(context: Context, uid: String, vendaId: String) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val atual = prefs.getStringSet(chavePrefsInicio(uid), emptySet()).orEmpty().toMutableSet()
            if (atual.add(vendaId)) {
                prefs.edit().putStringSet(chavePrefsInicio(uid), atual).apply()
            }
        }

        fun limparNotificacaoInicio(context: Context, uid: String, vendaId: String) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val atual = prefs.getStringSet(chavePrefsInicio(uid), emptySet()).orEmpty().toMutableSet()
            if (atual.remove(vendaId)) {
                prefs.edit().putStringSet(chavePrefsInicio(uid), atual).apply()
            }
        }

        fun marcarComoNotificada(context: Context, uid: String, vendaId: String) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val atual = prefs.getStringSet(chavePrefs(uid), emptySet()).orEmpty().toMutableSet()
            if (atual.add(vendaId)) {
                prefs.edit().putStringSet(chavePrefs(uid), atual).apply()
            }
        }

        fun limparNotificacaoVenda(context: Context, uid: String, vendaId: String) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val atual = prefs.getStringSet(chavePrefs(uid), emptySet()).orEmpty().toMutableSet()
            if (atual.remove(vendaId)) {
                prefs.edit().putStringSet(chavePrefs(uid), atual).apply()
            }
        }

        private fun chavePrefs(uid: String) = "vendas_notificadas_$uid"
        private fun chavePrefsInicio(uid: String) = "vendas_inicio_notificadas_$uid"

        fun mostrarNotificacaoProximidade(
            context: Context,
            vendaId: String,
            titulo: String,
            mensagem: String,
            uid: String? = null
        ) {
            val appContext = context.applicationContext
            criarCanal(appContext)

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Aviso de proximidade recebido, mas a permissão de notificações foi negada.")
                LogNotificacoesEntrega.registrar(
                    "Notificação",
                    "Proximidade (venda ${vendaId.take(8)}): BLOQUEADA — permissão de notificações negada."
                )
                return
            }

            if (!uid.isNullOrBlank()) {
                synchronized(this) {
                    if (jaFoiNotificada(appContext, uid, vendaId)) {
                        LogNotificacoesEntrega.registrar(
                            "Notificação",
                            "Proximidade (venda ${vendaId.take(8)}): já notificada antes, ignorando duplicata."
                        )
                        return
                    }
                    marcarComoNotificada(appContext, uid, vendaId)
                }
            }

            val intent = Intent(appContext, MainActivity::class.java).apply {
                action = ACTION_ABRIR_RASTREAMENTO
                putExtra(EXTRA_VENDA_ID, vendaId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                vendaId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_delivery)
                .setContentTitle(titulo)
                .setContentText(mensagem)
                .setStyle(NotificationCompat.BigTextStyle().bigText(mensagem))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(appContext).notify(
                idNotificacao(vendaId, TIPO_ENTREGADOR_PROXIMO),
                notification
            )
            LogNotificacoesEntrega.registrar(
                "Notificação",
                "Proximidade EXIBIDA: \"$titulo\" — $mensagem"
            )
        }

        fun mostrarNotificacaoEntregaIniciada(
            context: Context,
            vendaId: String,
            titulo: String,
            mensagem: String
        ) {
            val appContext = context.applicationContext
            criarCanal(appContext)
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Aviso de início recebido, mas a permissão de notificações foi negada.")
                LogNotificacoesEntrega.registrar(
                    "Notificação",
                    "Início de entrega (venda ${vendaId.take(8)}): BLOQUEADA — permissão de notificações negada."
                )
                return
            }

            val intent = Intent(appContext, MainActivity::class.java).apply {
                action = ACTION_ABRIR_RASTREAMENTO
                putExtra(EXTRA_VENDA_ID, vendaId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                idNotificacao(vendaId, TIPO_ENTREGA_INICIADA),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_delivery)
                .setContentTitle(titulo)
                .setContentText(mensagem)
                .setStyle(NotificationCompat.BigTextStyle().bigText(mensagem))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(
                    R.drawable.ic_stat_delivery,
                    "Ver localização",
                    pendingIntent
                )
                .build()

            NotificationManagerCompat.from(appContext).notify(
                "$TIPO_ENTREGA_INICIADA:$vendaId",
                0,
                notification
            )
            LogNotificacoesEntrega.registrar(
                "Notificação",
                "Início de entrega EXIBIDO: \"$titulo\" — $mensagem"
            )
        }

        private fun idNotificacao(vendaId: String, tipo: String) =
            31 * vendaId.hashCode() + tipo.hashCode()

        suspend fun registrarToken(uid: String, token: String) {
            FirebaseFirestore.getInstance().collection("usuarios").document(uid)
                .set(
                    mapOf("fcmTokens" to FieldValue.arrayUnion(token)),
                    SetOptions.merge()
                )
                .await()
            LogNotificacoesEntrega.registrar(
                "FCM",
                "Token FCM registrado no Firestore (uid ${uid.take(8)}...)."
            )
        }
    }
}
