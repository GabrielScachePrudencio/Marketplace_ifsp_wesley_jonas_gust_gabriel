package com.example.marketplace.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.marketplace.MainActivity
import com.example.marketplace.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LocationTrackingService : Service() {

    companion object {
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"
        const val EXTRA_VENDA_ID = "EXTRA_VENDA_ID"

        private const val CHANNEL_ID = "rastreio_entrega_channel"
        private const val NOTIFICATION_ID = 1001
        private const val INTERVALO_ATUALIZACAO_MS = 5000L
        private const val RAIO_CERCA_METROS = 187f
        private const val TAG = "LocationTracking"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var vendaIdAtual: String? = null
    private var destino: Pair<Double, Double>? = null
    private var proximidadeNotificada = false

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        criarCanalNotificacao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                val vendaId = intent.getStringExtra(EXTRA_VENDA_ID)
                if (!vendaId.isNullOrBlank() &&
                    (vendaIdAtual != vendaId || locationCallback == null)
                ) {
                    pararAtualizacoes()
                    vendaIdAtual = vendaId
                    startForeground(NOTIFICATION_ID, buildNotification())
                    carregarDestino(vendaId)
                    iniciarAtualizacoesDeLocalizacao(vendaId)
                }
            }
            ACTION_STOP_TRACKING -> {
                pararAtualizacoes()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun iniciarAtualizacoesDeLocalizacao(vendaId: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            INTERVALO_ATUALIZACAO_MS
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    enviarLocalizacaoParaFirestore(vendaId, location.latitude, location.longitude)
                    verificarProximidade(vendaId, location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback as LocationCallback,
            Looper.getMainLooper()
        )
    }

    private fun carregarDestino(vendaId: String) {
        destino = null
        proximidadeNotificada = false
        firestore.collection("rastreios_entrega")
            .whereEqualTo("vendaId", vendaId)
            .limit(1)
            .get()
            .addOnSuccessListener { resultado ->
                if (vendaIdAtual != vendaId) return@addOnSuccessListener
                val documento = resultado.documents.firstOrNull() ?: return@addOnSuccessListener
                val latitude = documento.getDouble("destinoLat") ?: return@addOnSuccessListener
                val longitude = documento.getDouble("destinoLng") ?: return@addOnSuccessListener
                if (latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
                    (latitude != 0.0 || longitude != 0.0)
                ) {
                    destino = latitude to longitude
                } else {
                    Log.w(TAG, "Coordenadas inválidas para a venda $vendaId.")
                }
            }
            .addOnFailureListener { erro ->
                Log.e(TAG, "Não foi possível carregar o destino da venda $vendaId.", erro)
            }
    }

    private fun verificarProximidade(vendaId: String, localizacao: Location) {
        if (vendaId != vendaIdAtual || proximidadeNotificada) return
        val (destinoLat, destinoLng) = destino ?: return
        val distancia = FloatArray(1)
        Location.distanceBetween(
            localizacao.latitude,
            localizacao.longitude,
            destinoLat,
            destinoLng,
            distancia
        )
        if (distancia[0] > RAIO_CERCA_METROS) return

        val motoristaId = FirebaseAuth.getInstance().currentUser?.uid
        if (motoristaId.isNullOrBlank()) {
            Log.w(TAG, "Usuário motorista não autenticado; evento de proximidade não registrado.")
            return
        }

        val vendaRef = firestore.collection("vendas").document(vendaId)
        firestore.runTransaction { transacao ->
            val venda = transacao.get(vendaRef)
            val status = venda.getString("status")
            val motoristaAtribuido = venda.getString("motoristaId")
            val ativa = status == "A_CAMINHO" || status == "SAIU_PARA_ENTREGA"

            if (
                !venda.exists() ||
                motoristaAtribuido != motoristaId ||
                !ativa
            ) {
                false
            } else if (venda.getBoolean("proximidadeNotificada") == true) {
                true
            } else {
                transacao.update(
                    vendaRef,
                    mapOf(
                        "proximidadeNotificada" to true,
                        "proximidadeNotificadaEm" to System.currentTimeMillis()
                    )
                )
                true
            }
        }.addOnSuccessListener { registrada ->
            if (registrada) proximidadeNotificada = true
        }.addOnFailureListener { erro ->
            Log.e(TAG, "Não foi possível registrar a entrada na cerca da venda $vendaId.", erro)
        }
    }

    private fun enviarLocalizacaoParaFirestore(vendaId: String, lat: Double, lng: Double) {
        val dados = mapOf(
            "latitude" to lat,
            "longitude" to lng,
            "timestamp" to System.currentTimeMillis()
        )
        firestore.collection("localizacoes_tempo_real")
            .document(vendaId)
            .set(dados)
    }

    private fun pararAtualizacoes() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
        vendaIdAtual = null
        destino = null
        proximidadeNotificada = false
    }

    private fun criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rastreio de Entrega",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação enquanto a entrega está em andamento"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Entrega em andamento")
            .setContentText("Enviando sua localização em tempo real")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        pararAtualizacoes()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}