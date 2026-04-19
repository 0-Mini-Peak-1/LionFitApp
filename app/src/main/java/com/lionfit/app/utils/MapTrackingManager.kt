package com.lionfit.app.utils

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import com.lionfit.app.R
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.compass.IOrientationConsumer
import org.osmdroid.views.overlay.compass.IOrientationProvider

class MapTrackingManager(private val context: Context, private val map: MapView) : IOrientationConsumer {

    private val polylines = mutableListOf<Polyline>()
    private var userMarker: Marker? = null
    private var markerAnimator: ValueAnimator? = null
    private var isFirstLocationFix = true
    private val compassProvider = InternalCompassOrientationProvider(context)
    private var currentSpeed: Float = 0f
    private var hasGpsBearing: Boolean = false
    private var gpsBearing: Float = 0f

    init {
        setupMarker()
    }

    private fun setupMarker() {
        userMarker = Marker(map)
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_blue_dot)
        userMarker?.icon = drawable
        userMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        userMarker?.setOnMarkerClickListener { _, _ -> true }
        map.overlays.add(userMarker)
    }

    fun updateLiveLocation(
        newPosition: GeoPoint,
        segments: List<List<GeoPoint>>,
        isTracking: Boolean,
        speed: Float = 0f,
        bearing: Float = 0f,
        hasBearing: Boolean = false
    ) {
        val marker = userMarker ?: return
        currentSpeed = speed
        hasGpsBearing = hasBearing
        gpsBearing = bearing

        // The hybrid architecture logic
//        if (currentSpeed >= 1.5f && hasGpsBearing) {
//            userMarker?.rotation = gpsBearing
//        }

        while (polylines.size < segments.size) {
            val newPolyline = Polyline(map).apply {
                outlinePaint.color = Color.parseColor("#4285F4")
                outlinePaint.strokeWidth = 15f
                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                infoWindow = null
            }
            polylines.add(newPolyline)
            map.overlays.add(0, newPolyline)
        }

        for (i in 0 until segments.size - 1) {
            polylines[i].setPoints(segments[i])
        }

        val activePolyline = if (polylines.isNotEmpty()) polylines.last() else null
        val activeSegment = if (segments.isNotEmpty()) segments.last() else emptyList()

        if (isFirstLocationFix) {
            userMarker?.position = newPosition
            map.controller.setZoom(19.0)
            map.controller.animateTo(newPosition)
            if (isTracking && activePolyline != null) {
                activePolyline.setPoints(activeSegment)
            }
            isFirstLocationFix = false
            return
        }

        val startPosition = marker.position ?: return
        markerAnimator?.cancel()

        markerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            interpolator = android.view.animation.LinearInterpolator()

            addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                val lat = startPosition.latitude + (newPosition.latitude - startPosition.latitude) * fraction
                val lon = startPosition.longitude + (newPosition.longitude - startPosition.longitude) * fraction
                val interpolatedPoint = GeoPoint(lat, lon)

                // Move the marker and camera
                marker.position = interpolatedPoint
                map.controller.setCenter(interpolatedPoint)

                if (isTracking && activePolyline != null && activeSegment.isNotEmpty()) {
                    val animatedPath = activeSegment.dropLast(1).toMutableList()
                    animatedPath.add(interpolatedPoint)
                    activePolyline.setPoints(animatedPath)
                }

                map.invalidate()
            }
            start()
        }
    }

    fun clearMapLines() {
        polylines.forEach { map.overlays.remove(it) }
        polylines.clear()
        map.invalidate()
    }

    fun showInitialMarker(startPoint: GeoPoint) {
        userMarker?.position = startPoint
        map.controller.setZoom(19.0)
        map.controller.animateTo(startPoint)
        map.invalidate()
    }

    fun clearMap() {
        polylines.forEach { map.overlays.remove(it) }
        polylines.clear()
        map.invalidate()
        isFirstLocationFix = true
        markerAnimator?.cancel()
    }

    fun startCompass() {
        compassProvider.startOrientationProvider(this)
    }

    fun stopCompass() {
        compassProvider.stopOrientationProvider()
    }

    override fun onOrientationChanged(orientation: Float, source: IOrientationProvider?) {
        // TODO: I remove hybrid architecture for testing purpose
//        if (currentSpeed < 1.5f || !hasGpsBearing) {
//            userMarker?.rotation = -orientation
//            map.post { map.invalidate() }
//        }
        userMarker?.rotation = -orientation
        map.post { map.invalidate() }
    }
}

