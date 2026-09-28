package com.example.marketplace.util

import android.content.Context
import com.example.marketplace.service.GeocodingService
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

object TilesGeoapify : OnlineTileSourceBase(
    "GeoapifyOsmBright", 0, 19, 256, ".png",
    arrayOf("https://maps.geoapify.com/v1/tile/osm-bright/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl +
                MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getX(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) + ".png" +
                "?apiKey=" + GeocodingService.API_KEY
    }
}

/** Chame antes de criar qualquer MapView. */
fun configurarOsmdroid(context: Context) {
    Configuration.getInstance().apply {
        load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        userAgentValue = "MarketplaceIFSP/1.0 (${context.packageName})"
    }
}