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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class LocationTrackingService : Service() {

    companion object {
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"
        const val EXTRA_VENDA_ID = "EXTRA_VENDA_ID"
        const val EXTRA_VENDAS_IDS = "EXTRA_VENDAS_IDS"

        private const val CHANNEL_ID = "rastreio_entrega_channel"
        private const val NOTIFICATION_ID = 1001
        private const val INTERVALO_ATUALIZACAO_MS = 5000L
        const val RAIO_CERCA_METROS = 50f
        private const val TAG = "LocationTracking"

        private val destinosCache = ConcurrentHashMap<String, Pair<Double, Double>>()
        private val proximidadeNotificadaMemory = ConcurrentHashMap.newKeySet<String>()
        private val carregandoDestino = ConcurrentHashMap.newKeySet<String>()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun verificarERegistrarProximidade(
            vendaId: String,
            latitudeMotorista: Double,
            longitudeMotorista: Double
        ) {
            if (vendaId.isBlank() || proximidadeNotificadaMemory.contains(vendaId)) return
            val destino = destinosCache[vendaId]
            if (destino == null) {
                carregarDestinoAssincrono(vendaId) { coord ->
                    if (coord != null) {
                        checarDistanciaERegistrar(vendaId, latitudeMotorista, longitudeMotorista, coord)
                    }
                }
                return
            }
            checarDistanciaERegistrar(vendaId, latitudeMotorista, longitudeMotorista, destino)
        }

        private fun carregarDestinoAssincrono(
            vendaId: String,
            onLoaded: (Pair<Double, Double>?) -> Unit = {}
        ) {
            destinosCache[vendaId]?.let {
                onLoaded(it)
                return
            }
            if (!carregandoDestino.add(vendaId)) return

            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("rastreios_entrega")
                .whereEqualTo("vendaId", vendaId)
                .limit(1)
                .get()
                .addOnSuccessListener { resultado ->
                    val documento = resultado.documents.firstOrNull()
                    val lat = documento?.getDouble("destinoLat") ?: 0.0
                    val lng = documento?.getDouble("destinoLng") ?: 0.0
                    val enderecoTexto = documento?.getString("enderecoDestinoTexto").orEmpty()

                    if (coordenadaValida(lat, lng)) {
                        val par = lat to lng
                        destinosCache[vendaId] = par
                        carregandoDestino.remove(vendaId)
                        onLoaded(par)
                    } else {
                        buscarDestinoPeloComprador(firestore, vendaId, documento?.id, enderecoTexto) { par ->
                            carregandoDestino.remove(vendaId)
                            onLoaded(par)
                        }
                    }
                }
                .addOnFailureListener { erro ->
                    Log.e(TAG, "Não foi possível carregar o destino da venda $vendaId.", erro)
                    carregandoDestino.remove(vendaId)
                    onLoaded(null)
                }
        }

        private fun buscarDestinoPeloComprador(
            firestore: FirebaseFirestore,
            vendaId: String,
            rastreioDocId: String?,
            enderecoTexto: String,
            onResult: (Pair<Double, Double>?) -> Unit
        ) {
            firestore.collection("vendas").document(vendaId).get()
                .addOnSuccessListener { vendaDoc ->
                    val compradorId = vendaDoc.getString("compradorId").orEmpty()
                    if (compradorId.isBlank()) {
                        geocodificarFallback(firestore, vendaId, rastreioDocId, enderecoTexto, null, onResult)
                        return@addOnSuccessListener
                    }
                    firestore.collection("usuarios").document(compradorId).get()
                        .addOnSuccessListener { usuarioDoc ->
                            val uLat = usuarioDoc.getDouble("latitude") ?: 0.0
                            val uLng = usuarioDoc.getDouble("longitude") ?: 0.0
                            if (coordenadaValida(uLat, uLng)) {
                                val par = uLat to uLng
                                destinosCache[vendaId] = par
                                salvarDestinoNoRastreio(firestore, rastreioDocId, uLat, uLng)
                                onResult(par)
                            } else {
                                geocodificarFallback(firestore, vendaId, rastreioDocId, enderecoTexto, usuarioDoc, onResult)
                            }
                        }
                        .addOnFailureListener {
                            geocodificarFallback(firestore, vendaId, rastreioDocId, enderecoTexto, null, onResult)
                        }
                }
                .addOnFailureListener {
                    onResult(null)
                }
        }

        private fun geocodificarFallback(
            firestore: FirebaseFirestore,
            vendaId: String,
            rastreioDocId: String?,
            enderecoTexto: String,
            usuarioDoc: com.google.firebase.firestore.DocumentSnapshot?,
            onResult: (Pair<Double, Double>?) -> Unit
        ) {
            scope.launch {
                try {
                    val rua = usuarioDoc?.getString("rua").orEmpty()
                    val numero = usuarioDoc?.getString("numero").orEmpty()
                    val cidade = usuarioDoc?.getString("cidade").orEmpty()
                    val estado = usuarioDoc?.getString("estado").orEmpty()
                    val cep = usuarioDoc?.getString("cep").orEmpty()

                    val coord = if (rua.isNotBlank() || cidade.isNotBlank() || cep.isNotBlank()) {
                        GeocodingService.buscarCoordenadas(
                            rua = rua,
                            numero = numero,
                            cidade = cidade,
                            estado = estado,
                            cep = cep
                        )
                    } else if (enderecoTexto.isNotBlank()) {
                        GeocodingService.buscarCoordenadasPorTexto("$enderecoTexto, Brasil")
                    } else null

                    if (coord != null && coordenadaValida(coord.latitude, coord.longitude)) {
                        val par = coord.latitude to coord.longitude
                        destinosCache[vendaId] = par
                        salvarDestinoNoRastreio(firestore, rastreioDocId, coord.latitude, coord.longitude)
                        onResult(par)
                    } else {
                        onResult(null)
                    }
                } catch (e: Exception) {
                    onResult(null)
                }
            }
        }

        private fun salvarDestinoNoRastreio(
            firestore: FirebaseFirestore,
            rastreioDocId: String?,
            lat: Double,
            lng: Double
        ) {
            if (!rastreioDocId.isNullOrBlank()) {
                firestore.collection("rastreios_entrega")
                    .document(rastreioDocId)
                    .update(mapOf("destinoLat" to lat, "destinoLng" to lng))
            }
        }

        private fun coordenadaValida(lat: Double, lng: Double): Boolean {
            return lat in -90.0..90.0 && lng in -180.0..180.0 && (lat != 0.0 || lng != 0.0)
        }

        private fun checarDistanciaERegistrar(
            vendaId: String,
            latitudeMotorista: Double,
            longitudeMotorista: Double,
            destino: Pair<Double, Double>
        ) {
            if (proximidadeNotificadaMemory.contains(vendaId)) return
            val (destinoLat, destinoLng) = destino
            val distancia = FloatArray(1)
            Location.distanceBetween(
                latitudeMotorista,
                longitudeMotorista,
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

            val firestore = FirebaseFirestore.getInstance()
            val vendaRef = firestore.collection("vendas").document(vendaId)
            firestore.runTransaction { transacao ->
                val venda = transacao.get(vendaRef)
                val status = venda.getString("status")
                val motoristaAtribuido = venda.getString("motoristaId")
                val ativa = status == "A_CAMINHO" || status == "SAIU_PARA_ENTREGA"

                if (!venda.exists() || motoristaAtribuido != motoristaId || !ativa) {
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
                if (registrada) {
                    proximidadeNotificadaMemory.add(vendaId)
                }
            }.addOnFailureListener { erro ->
                Log.e(TAG, "Não foi possível registrar a entrada na cerca da venda $vendaId.", erro)
            }
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private val vendasIdsAtivas = ConcurrentHashMap.newKeySet<String>()

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        criarCanalNotificacao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                val listaExtra = intent.getStringArrayListExtra(EXTRA_VENDAS_IDS).orEmpty()
                val unicoExtra = intent.getStringExtra(EXTRA_VENDA_ID)
                val novosIds = (listaExtra + listOfNotNull(unicoExtra))
                    .filter { it.isNotBlank() }
                    .toSet()

                if (novosIds.isNotEmpty()) {
                    startForeground(NOTIFICATION_ID, buildNotification())
                    vendasIdsAtivas.addAll(novosIds)
                    novosIds.forEach { id -> carregarDestinoAssincrono(id) }
                    if (locationCallback == null) {
                        iniciarAtualizacoesDeLocalizacao()
                    }
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

    private fun iniciarAtualizacoesDeLocalizacao() {
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
                    vendasIdsAtivas.forEach { vendaId ->
                        enviarLocalizacaoParaFirestore(vendaId, location.latitude, location.longitude)
                        verificarERegistrarProximidade(vendaId, location.latitude, location.longitude)
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback as LocationCallback,
            Looper.getMainLooper()
        )
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
        vendasIdsAtivas.clear()
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
