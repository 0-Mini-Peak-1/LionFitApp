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
import androidx.fragment.app.Fragment
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
    private lateinit var btnPlay: ImageButton
    private lateinit var btnPause: ImageButton
    private lateinit var btnStop: ImageButton

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocationGranted) {
            sendCommandToService("ACTION_START_OR_RESUME_SERVICE")
        } else {
            Toast.makeText(requireContext(), "Location permission is required to track runs.", Toast.LENGTH_SHORT).show()
        }
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
        val mapController = map.controller
        mapController.setZoom(16.0)
        mapController.setCenter(GeoPoint(13.8256, 100.4485))

        setupClickListeners()
        subscribeToObservers()
    }

    private fun setupClickListeners() {
        // PLAY / RESUME
        btnPlay.setOnClickListener {
            if (PermissionsHelper.hasLocationPermissions(requireContext())) {
                sendCommandToService("ACTION_START_OR_RESUME_SERVICE")
                updateButtonVisibility(isTrackingActive = true)
            } else {
                // Ask for Location AND Notifications at the same time
                val permissionsToRequest = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )

                // Only ask for POST_NOTIFICATIONS if the phone is Android 13 or newer
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                }

                requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
        }

        // PAUSE
        btnPause.setOnClickListener {
            sendCommandToService("ACTION_PAUSE_SERVICE")
            // Optional: You could change the Play button icon to a "Resume" icon here if you want
        }

        // STOP (Triggers the Confirmation Dialog)
        btnStop.setOnClickListener {
            showStopConfirmationDialog()
        }
    }

    private fun showStopConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Are u sure to stop Recording")
            .setPositiveButton("Confirm") { _, _ ->
                sendCommandToService("ACTION_STOP_SERVICE")

                // Hide the Pause and Stop buttons again
                updateButtonVisibility(isTrackingActive = false)

                // TODO: Add logic here to save the run data to the database
            }
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()
            .show()
    }

    // Helper function to handle the UI state transition smoothly
    private fun updateButtonVisibility(isTrackingActive: Boolean) {
        if (isTrackingActive) {
            btnPause.visibility = View.VISIBLE
            btnStop.visibility = View.VISIBLE
        } else {
            btnPause.visibility = View.GONE
            btnStop.visibility = View.GONE
        }
    }

    // Helper function to keep Intents clean
    private fun sendCommandToService(actionStr: String) {
        val intent = Intent(requireContext(), TrackingService::class.java).also {
            it.action = actionStr
        }
        requireContext().startService(intent)
    }

    private fun subscribeToObservers() {
        TrackingService.timeRunInMillis.observe(viewLifecycleOwner) { timeInMillis ->
            val formattedTime = Calculators.getFormattedStopWatchTime(timeInMillis)
            tvTimer.text = formattedTime
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}