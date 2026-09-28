package com.example.marketplace.util

import android.content.Context
import android.view.MotionEvent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@Composable
fun MapaSelecaoPonto(
    latitude: Double?,
    longitude: Double?,
    onPontoEscolhido: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val escolhaAtual by rememberUpdatedState(onPontoEscolhido)

    val mapView = remember {
        configurarOsmdroid(context)
        MapView(context).apply {
            // Se você trocou os tiles para os da Geoapify no MapaRastreamento, use o mesmo tile source aqui
            setTileSource(TilesGeoapify)
            setMultiTouchControls(true)
            controller.setZoom(13.0)
            controller.setCenter(GeoPoint(-21.7946, -48.1756)) // Araraquara, só como ponto de partida

            // Evita que o scroll da tela roube o arrastar do mapa
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.parent.requestDisallowInterceptTouchEvent(false)
                }
                false
            }

            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    escolhaAtual(p.latitude, p.longitude)
                    return true
                }
                override fun longPressHelper(p: GeoPoint): Boolean = false
            }))
        }
    }

    val marcador = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Ponto de referência"
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

    LaunchedEffect(latitude, longitude) {
        val valido = latitude != null && longitude != null && !(latitude == 0.0 && longitude == 0.0)
        if (valido) {
            val p = GeoPoint(latitude!!, longitude!!)
            marcador.position = p
            if (!mapView.overlays.contains(marcador)) mapView.overlays.add(marcador)
            mapView.controller.animateTo(p)
        } else {
            mapView.overlays.remove(marcador)
        }
        mapView.invalidate()
    }

    AndroidView(modifier = modifier, factory = { mapView })
}