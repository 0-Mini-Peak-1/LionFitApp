package com.lionfit.app.ui.running

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.lionfit.app.R
import com.lionfit.app.services.TrackingService
import com.lionfit.app.utils.Calculators
import com.lionfit.app.utils.PermissionsHelper
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

class RunningFragment : Fragment(R.layout.fragment_running) {

    private lateinit var map: MapView
    private lateinit var tvTimer: TextView
    private lateinit var btnStart: MaterialButton
    private lateinit var btnStop: MaterialButton

    // 1. This handles the result of the permission popup automatically
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocationGranted) {
            // The user clicked "Allow", start the run!
            startTrackingService()
        } else {
            // The user clicked "Deny"
            Toast.makeText(requireContext(), "Location permission is required to track runs.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 2. Link the variables to your XML layout elements
        tvTimer = view.findViewById(R.id.tv_timer)
        btnStart = view.findViewById(R.id.btn_start)
        btnStop = view.findViewById(R.id.btn_stop)

        // 3. Map Setup (Kept exactly as you had it)
        Configuration.getInstance().userAgentValue = requireContext().packageName
        map = view.findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        val mapController = map.controller
        mapController.setZoom(16.0)
        mapController.setCenter(GeoPoint(13.8256, 100.4485))

        // 4. Handle the Start Button Click
        btnStart.setOnClickListener {
            if (PermissionsHelper.hasLocationPermissions(requireContext())) {
                // We already have permission, just start the service
                startTrackingService()
            } else {
                // We don't have permission, launch the popup asking for it
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        // 5. Handle the Stop Button Click
        btnStop.setOnClickListener {
            stopTrackingService()
        }

        subscribeToObservers()
    }

    // Add this new function at the bottom of the class
    private fun subscribeToObservers() {
        TrackingService.timeRunInMillis.observe(viewLifecycleOwner) { timeInMillis ->
            val formattedTime = Calculators.getFormattedStopWatchTime(timeInMillis)
            tvTimer.text = formattedTime
        }
    }

    private fun startTrackingService() {
        // Change the button text so the user knows it's active
        btnStart.text = "Tracking..."

        // Wake up the TrackingService we built earlier
        val intent = Intent(requireContext(), TrackingService::class.java).also {
            it.action = "ACTION_START_OR_RESUME_SERVICE"
        }
        requireContext().startService(intent)
    }

    private fun stopTrackingService() {
        // Reset the button
        btnStart.text = "Start Run"

        // Tell the TrackingService to shut down
        val intent = Intent(requireContext(), TrackingService::class.java).also {
            it.action = "ACTION_STOP_SERVICE"
        }
        requireContext().startService(intent)
    }

    // Lifecycle methods to keep the map memory usage low
    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}