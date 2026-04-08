package com.lionfit.app.ui.running

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import com.lionfit.app.R
import com.lionfit.app.services.TrackingService
import com.lionfit.app.utils.Calculators
import com.lionfit.app.utils.PermissionsHelper
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.Polyline
import android.graphics.Color

class RunningFragment : Fragment(R.layout.fragment_running) {

    private lateinit var map: MapView
    private lateinit var tvTimer: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var btnPause: ImageButton
    private lateinit var btnStop: ImageButton

    private lateinit var locationOverlay: MyLocationNewOverlay
    private var runningPathPolyline: Polyline? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocationGranted) {
            enableUserLocationOnMap() // Enable once the permission is granted
        } else {
            Toast.makeText(requireContext(), "Location permission is required to track runs.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestLocationAndNotificationPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind the new UI elements
        tvTimer = view.findViewById(R.id.tv_timer)
        btnPlay = view.findViewById(R.id.btn_play)
        btnPause = view.findViewById(R.id.btn_pause)
        btnStop = view.findViewById(R.id.btn_stop)

        // Initialize Map
        Configuration.getInstance().userAgentValue = requireContext().packageName
        map = view.findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        // Initialize the Polyline (The blue tracking line)
        runningPathPolyline = Polyline(map)
        runningPathPolyline?.outlinePaint?.color = Color.parseColor("#4285F4") // That nice Google Blue
        runningPathPolyline?.outlinePaint?.strokeWidth = 15f // Thickness of the line
        runningPathPolyline?.outlinePaint?.strokeCap = android.graphics.Paint.Cap.ROUND // Smooth rounded edges

        // Add it to the map's overlay list
        map.overlays.add(runningPathPolyline)

        // Setup the Overlay
        val locationProvider = GpsMyLocationProvider(requireContext())
        locationOverlay = MyLocationNewOverlay(locationProvider, map)
        map.overlays.add(locationOverlay)

        // Check permissions the moment the Run tab opens
        if (PermissionsHelper.hasLocationPermissions(requireContext())) {
            enableUserLocationOnMap()
        } else {
            // Default center just in case they deny it
            map.controller.setZoom(16.0)
            map.controller.setCenter(GeoPoint(13.8256, 100.4485))

            // Immediately pop up the permission request
            requestLocationAndNotificationPermissions()
        }

        setupClickListeners()
        subscribeToObservers()
    }

    private fun enableUserLocationOnMap() {
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_blue_dot)
        val customLocationBitmap = drawable?.toBitmap()

        if (customLocationBitmap != null) {
            locationOverlay.setDirectionIcon(customLocationBitmap)
            locationOverlay.setPersonIcon(customLocationBitmap)
        }

        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()

        if (locationOverlay.myLocation != null) {
            map.controller.animateTo(locationOverlay.myLocation)
            map.controller.setZoom(18.0)
        }

        // The moment the GPS finds the user, zoom in close to them!
        locationOverlay.runOnFirstFix {
            requireActivity().runOnUiThread {
                if (locationOverlay.myLocation != null) {
                    map.controller.animateTo(locationOverlay.myLocation)
                    map.controller.setZoom(18.0)
                }
            }
        }
    }

    private fun setupClickListeners() {
        // PLAY / RESUME
        btnPlay.setOnClickListener {
            if (PermissionsHelper.hasLocationPermissions(requireContext())) {
                sendCommandToService("ACTION_START_OR_RESUME_SERVICE")
                updateButtonVisibility(isTrackingActive = true)
            } else {
                requestLocationAndNotificationPermissions()
            }
        }

        // PAUSE
        btnPause.setOnClickListener {
            sendCommandToService("ACTION_PAUSE_SERVICE")
        }

        // STOP (Triggers the Confirmation Dialog)
        btnStop.setOnClickListener {
            showStopConfirmationDialog()
        }
    }

    private fun showStopConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Do you want to stop tracking?")
            .setPositiveButton("Confirm") { _, _ ->
                sendCommandToService("ACTION_STOP_SERVICE")
                updateButtonVisibility(isTrackingActive = false)
            }
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()
            .show()
    }

    private fun updateButtonVisibility(isTrackingActive: Boolean) {
        val currentTime = TrackingService.timeRunInMillis.value ?: 0L

        if (isTrackingActive) {
            btnPlay.visibility = View.GONE
            btnPause.visibility = View.VISIBLE
            btnStop.visibility = View.VISIBLE
        } else if (currentTime > 0L) {
            btnPlay.visibility = View.VISIBLE
            btnPause.visibility = View.GONE
            btnStop.visibility = View.VISIBLE
        } else {
            btnPlay.visibility = View.VISIBLE
            btnPause.visibility = View.GONE
            btnStop.visibility = View.GONE
        }
    }

    private fun sendCommandToService(actionStr: String) {
        val intent = Intent(requireContext(), TrackingService::class.java).also {
            it.action = actionStr
        }
        requireContext().startService(intent)
    }

    private fun subscribeToObservers() {
        // Observe the Timer
        TrackingService.timeRunInMillis.observe(viewLifecycleOwner) { timeInMillis ->
            val formattedTime = Calculators.getFormattedStopWatchTime(timeInMillis)
            tvTimer.text = formattedTime
        }

        TrackingService.isTracking.observe(viewLifecycleOwner) { isTracking ->
            updateButtonVisibility(isTracking)
        }

        // Observe the GPS Coordinates and draw the line
        TrackingService.pathPoints.observe(viewLifecycleOwner) { points ->
            if (points.isNotEmpty()) {
                runningPathPolyline?.setPoints(points)
                map.invalidate()
                map.controller.animateTo(points.last())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        if (PermissionsHelper.hasLocationPermissions(requireContext())) {
            locationOverlay.enableMyLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        locationOverlay.disableMyLocation()
    }
}