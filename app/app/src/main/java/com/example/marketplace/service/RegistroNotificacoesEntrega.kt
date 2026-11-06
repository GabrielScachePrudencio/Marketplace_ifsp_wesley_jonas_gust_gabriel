package com.example.marketplace.service

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

@Composable
fun RegistroNotificacoesEntrega(uid: String) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (!concedida) {
            Toast.makeText(
                context,
                "Notificações desativadas. Ative-as nas configurações para receber avisos de entrega.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(uid) {
        EntregaMessagingService.criarCanal(context)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        try {
            val token = FirebaseMessaging.getInstance().token.await()
            EntregaMessagingService.registrarToken(uid, token)
        } catch (e: Exception) {
            android.util.Log.e("EntregaNotifications", "Não foi possível registrar o dispositivo para notificações.", e)
        }
    }
}
