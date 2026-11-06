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
        val vendaId = message.data["vendaId"]
        if (message.data["tipo"] != TIPO_ENTREGADOR_PROXIMO || vendaId.isNullOrBlank()) {
            return
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Aviso de proximidade recebido, mas a permissão de notificações foi negada.")
            return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_ABRIR_RASTREAMENTO
            putExtra(EXTRA_VENDA_ID, vendaId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            vendaId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(message.data["titulo"] ?: "Seu entregador está chegando")
            .setContentText(message.data["mensagem"] ?: "Acompanhe a entrega no mapa.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    message.data["mensagem"] ?: "Acompanhe a entrega no mapa."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(vendaId.hashCode(), notification)
    }

    companion object {
        const val ACTION_ABRIR_RASTREAMENTO = "com.example.marketplace.ABRIR_RASTREAMENTO"
        const val EXTRA_VENDA_ID = "vendaId"
        const val TIPO_ENTREGADOR_PROXIMO = "ENTREGADOR_PROXIMO"

        private const val TAG = "EntregaNotifications"
        private const val CHANNEL_ID = "entregas_proximas"

        fun criarCanal(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val canal = NotificationChannel(
                    CHANNEL_ID,
                    "Avisos de entrega",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Avisos quando o entregador estiver perto do endereço."
                }
                context.getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(canal)
            }
        }

        suspend fun registrarToken(uid: String, token: String) {
            FirebaseFirestore.getInstance().collection("usuarios").document(uid)
                .set(
                    mapOf("fcmTokens" to FieldValue.arrayUnion(token)),
                    SetOptions.merge()
                )
                .await()
        }
    }
}
