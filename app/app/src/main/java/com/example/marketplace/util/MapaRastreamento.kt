package com.example.marketplace.util

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

enum class TipoMarcador { ORIGEM, DESTINO, MOTORISTA }

data class MarcadorMapa(
    val latitude: Double,
    val longitude: Double,
    val titulo: String,
    val tipo: TipoMarcador
)

@Composable
fun MapaRastreamento(
    origem: MarcadorMapa?,
    destino: MarcadorMapa?,
    motorista: MarcadorMapa?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        configurarOsmdroid(context)
        MapView(context).apply {
            setTileSource(TilesGeoapify)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(origem, destino, motorista) {
        mapView.overlays.clear()
        val pontosValidos = mutableListOf<GeoPoint>()

        fun adicionar(marcador: MarcadorMapa?, emoji: String) {
            marcador ?: return
            if (marcador.latitude == 0.0 && marcador.longitude == 0.0) return

            val ponto = GeoPoint(marcador.latitude, marcador.longitude)
            pontosValidos.add(ponto)
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = ponto
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "$emoji ${marcador.titulo}"
                }
            )
        }

        adicionar(origem, "🏪")
        adicionar(destino, "🏁")
        adicionar(motorista, "🚗")

        if (pontosValidos.size == 1) {
            mapView.controller.setCenter(pontosValidos.first())
        } else if (pontosValidos.size > 1) {
            mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(pontosValidos), true, 100)
        }

        mapView.invalidate()
    }

    AndroidView(modifier = modifier.fillMaxSize(), factory = { mapView })
}