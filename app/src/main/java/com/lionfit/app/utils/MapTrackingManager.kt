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

class MapTrackingManager(private val context: Context, private val map: MapView) : SensorEventListener {

    private val polylines = mutableListOf<Polyline>()

    private var userMarker: Marker? = null
    private var markerAnimator: ValueAnimator? = null
    private var isFirstLocationFix = true

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    init {
        setupMarker()
    }

    private fun setupMarker() {
        userMarker = Marker(map)
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_blue_dot)
        userMarker?.icon = drawable
        userMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        map.overlays.add(userMarker)
    }

    fun updateLiveLocation(newPosition: GeoPoint, segments: List<List<GeoPoint>>, isTracking: Boolean) {
        val marker = userMarker ?: return

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
            map.controller.setZoom(18.0)
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
        map.controller.setZoom(18.0)
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
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopCompass() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientationAngles = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            var azimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            azimuthDegrees = (azimuthDegrees + 360) % 360

            userMarker?.rotation = azimuthDegrees
            map.invalidate()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // We don't need to do anything here, but Android requires the function to exist.
    }
}

