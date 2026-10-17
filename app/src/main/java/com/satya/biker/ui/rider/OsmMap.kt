package com.satya.biker.ui.rider

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

data class MapPin(
    val id: String,
    val lat: Double,
    val lng: Double,
    val title: String,
    val subtitle: String,
    val label: String,
    val color: Int,
)

/** OpenStreetMap view (no API key needed) that shows rider pins and fits them on first load. */
@Composable
fun OsmMap(pins: List<MapPin>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { object { var fitted = false } }
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(5.0)
            controller.setCenter(GeoPoint(22.5, 79.0))
        }
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); map.onDetach() }
    }
    AndroidView({ map }, modifier) { view ->
        view.overlays.removeAll { it is Marker }
        pins.forEach { p ->
            view.overlays.add(
                Marker(view).apply {
                    position = GeoPoint(p.lat, p.lng)
                    title = p.title
                    snippet = p.subtitle
                    icon = BitmapDrawable(view.resources, pinBitmap(p.label, p.color, view.resources.displayMetrics.density))
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                },
            )
        }
        if (!state.fitted && pins.isNotEmpty()) {
            state.fitted = true
            view.post {
                if (pins.size == 1) {
                    view.controller.setZoom(15.0); view.controller.setCenter(GeoPoint(pins[0].lat, pins[0].lng))
                } else {
                    val box = BoundingBox.fromGeoPoints(pins.map { GeoPoint(it.lat, it.lng) })
                    view.zoomToBoundingBox(box.increaseByScale(1.4f), false)
                }
            }
        }
        view.invalidate()
    }
}

private fun pinBitmap(label: String, color: Int, density: Float): Bitmap {
    val size = (40 * density).toInt()
    val tail = (10 * density).toInt()
    val bmp = Bitmap.createBitmap(size, size + tail, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3 * density
    }
    val path = android.graphics.Path().apply {
        moveTo(size / 2f - tail, size - tail / 2f); lineTo(size / 2f, size + tail.toFloat()); lineTo(size / 2f + tail, size - tail / 2f); close()
    }
    c.drawPath(path, fill)
    c.drawCircle(size / 2f, size / 2f, size / 2f - 2 * density, fill)
    c.drawCircle(size / 2f, size / 2f, size / 2f - 2 * density, stroke)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE; textSize = 14 * density; typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    c.drawText(label.take(2), size / 2f, size / 2f - (text.descent() + text.ascent()) / 2, text)
    return bmp
}
