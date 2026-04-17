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
import android.widget.FrameLayout
import org.osmdroid.views.overlay.Marker
import com.google.android.gms.location.LocationServices
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import com.lionfit.app.utils.MapTrackingManager
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lionfit.app.data.database.AppDatabase
import com.lionfit.app.data.database.RunDao
import com.lionfit.app.data.database.SupabaseManager
import com.lionfit.app.data.model.RoutePoint
import com.lionfit.app.data.model.RunSession
import androidx.fragment.app.activityViewModels
import com.lionfit.app.ui.shared.RunSharedViewModel
import com.lionfit.app.MainActivity

class RunningFragment : Fragment(R.layout.fragment_running) {
    private lateinit var runDao: RunDao
    private val sharedViewModel: RunSharedViewModel by activityViewModels()
    private lateinit var map: MapView
    private lateinit var tvTimer: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvDistance: TextView

    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnStop: ImageButton
    private lateinit var btnLock: ImageButton

    // Locking variables
    private lateinit var viewLockOverlay: View
    private lateinit var layoutLockContainer: FrameLayout
    private lateinit var viewLockCircle: View
    // Expanded Text & Buttons
    private lateinit var statsCardMini: View
    private lateinit var layoutExpandedStats: View
    private lateinit var btnMinimize: ImageButton
    private lateinit var tvTimerExpanded: TextView
    private lateinit var tvDistanceExpanded: TextView
    private lateinit var tvSpeedExpanded: TextView
    private lateinit var tvCaloriesExpanded: TextView
    private lateinit var btnPlayPauseExpanded: ImageButton
    private lateinit var btnStopExpanded: ImageButton
    private lateinit var btnLockExpanded: ImageButton
    private lateinit var layoutLockContainerExpanded: FrameLayout
    private lateinit var viewLockCircleExpanded: View
    // MapTrackingManager
    private lateinit var mapTrackingManager: MapTrackingManager

    // Track if the screen is currently locked
    private var isScreenLocked = false
    // Live distance
    private var runDistanceInMeters = 0f
    private lateinit var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient
    private var isIdleTracking = false

    private val idleLocationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)

            // Check if the TrackingService is alive (either running OR paused)
            val isTracking = TrackingService.isTracking.value ?: false
            val timeRun = TrackingService.timeRunInMillis.value ?: 0L
            val isServiceDead = !isTracking && timeRun == 0L

            // Only move the marker if the service is completely dead
            if (isServiceDead) {
                result.locations.lastOrNull()?.let { location ->
                    val point = GeoPoint(location.latitude, location.longitude)
                    // Move the marker, but pass empty lists
                    mapTrackingManager.updateLiveLocation(point, emptyList(), false)
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocationGranted) {
            enableUserLocationOnMap() // Enable once the permission is granted
        } else {
            Toast.makeText(requireContext(), "Location permission is required to track runs.", Toast.LENGTH_SHORT).show()
        }
    } // I should write another one for the notification as well

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

        // Bind UI elements
        tvTimer = view.findViewById(R.id.tv_timer)
        tvSpeed = view.findViewById(R.id.tv_speed)
        tvDistance = view.findViewById(R.id.tv_distance)
        btnPlayPause = view.findViewById(R.id.btn_play_pause)
        btnStop = view.findViewById(R.id.btn_stop)
        btnLock = view.findViewById(R.id.btn_lock)
        viewLockOverlay = view.findViewById(R.id.view_lock_overlay)
        layoutLockContainer = view.findViewById(R.id.layout_lock_container)
        viewLockCircle = view.findViewById(R.id.view_lock_circle)

        // Expanded Text & Buttons (onViewCreated)
        statsCardMini = view.findViewById(R.id.stats_card)
        layoutExpandedStats = view.findViewById(R.id.layout_expanded_stats)
        btnMinimize = view.findViewById(R.id.btn_minimize)
        tvTimerExpanded = view.findViewById(R.id.tv_timer_expanded)
        tvDistanceExpanded = view.findViewById(R.id.tv_distance_expanded)
        tvSpeedExpanded = view.findViewById(R.id.tv_speed_expanded)
        tvCaloriesExpanded = view.findViewById(R.id.tv_calories_expanded)
        btnPlayPauseExpanded = view.findViewById(R.id.btn_play_pause_expanded)
        btnStopExpanded = view.findViewById(R.id.btn_stop_expanded)
        btnLockExpanded = view.findViewById(R.id.btn_lock_expanded)
        layoutLockContainerExpanded = view.findViewById(R.id.layout_lock_container_expanded)
        viewLockCircleExpanded = view.findViewById(R.id.view_lock_circle_expanded)

        // Initialize Map
        Configuration.getInstance().userAgentValue = requireContext().packageName
        map = view.findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        // Initialize MapTrackingManager
        mapTrackingManager = MapTrackingManager(requireContext(), map)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // Initialize Database DAO
        runDao = AppDatabase.getDatabase(requireContext()).runDao()

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

    @android.annotation.SuppressLint("MissingPermission")
    private fun enableUserLocationOnMap() {
        // Request continuous updates for the Fragment
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        fusedLocationClient.requestLocationUpdates(
            request,
            idleLocationCallback,
            Looper.getMainLooper()
        )
        isIdleTracking = true

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val startPoint = GeoPoint(it.latitude, it.longitude)
                mapTrackingManager.showInitialMarker(startPoint)
            }
        }
    }

    private fun endRunAndNavigateToSave() {
        val duration = TrackingService.timeRunInMillis.value ?: 0L
        val distanceKm = runDistanceInMeters / 1000.0

        if (duration > 0L && distanceKm > 0.0) {
            val timeInMinutes = (duration / 1000f) / 60f
            val avgPace = timeInMinutes / distanceKm
            val calories = ((duration / 1000f) * 0.15f).toInt()
            val timestamp = System.currentTimeMillis()
            val currentUserId = "a9572d27-53fc-473e-bec6-878b5742cb4f"

            val rawPathPoints = TrackingService.pathPoints.value ?: mutableListOf()
            val finalRoute = rawPathPoints.map { segment ->
                segment.map { RoutePoint(it.latitude, it.longitude) }
            }

            val session = RunSession(
                userId = currentUserId,
                timestamp = timestamp,
                durationInMillis = duration,
                distanceInKm = distanceKm,
                averagePace = avgPace,
                caloriesBurned = calories,
                pathCoordinates = finalRoute
            )

            // Put the data in the memory
            sharedViewModel.pendingRunSession.value = session

            // Switch the screen to the Save Form
            (requireActivity() as MainActivity).switchFragment("save_activity")

        } else {
            Toast.makeText(requireContext(), "Run too short to save.", Toast.LENGTH_SHORT).show()
            // Just kill the service if it was a mistake
            sendCommandToService("ACTION_STOP_SERVICE")
        }
    }

    private fun toggleScreenLock() {
        isScreenLocked = !isScreenLocked
        val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
        val btnProfile = requireActivity().findViewById<View>(R.id.card_top_profile)

        if (isScreenLocked) {
            // Lock
            btnLock.setImageResource(R.drawable.ic_unlock)
            btnLockExpanded.setImageResource(R.drawable.ic_unlock)
            btnLock.setColorFilter(android.graphics.Color.WHITE)
            btnLockExpanded.setColorFilter(android.graphics.Color.WHITE)

            viewLockCircle.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).start()
            viewLockCircleExpanded.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).start()

            viewLockOverlay.visibility = View.VISIBLE

            btnPlayPause.isEnabled = false
            btnStop.isEnabled = false
            btnPlayPauseExpanded.isEnabled = false
            btnStopExpanded.isEnabled = false
            btnMinimize.isEnabled = false // Don't let them minimize while locked
            bottomNav.menu.setGroupEnabled(0, false)

            statsCardMini.isEnabled = false
            btnProfile?.isEnabled = false

        } else {
            // Unlock
            btnLock.setImageResource(R.drawable.ic_lock)
            btnLockExpanded.setImageResource(R.drawable.ic_lock)
            btnLock.setColorFilter(android.graphics.Color.BLACK)
            btnLockExpanded.setColorFilter(android.graphics.Color.BLACK)

            viewLockCircle.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(300).start()
            viewLockCircleExpanded.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(300).start()

            viewLockOverlay.visibility = View.GONE

            btnPlayPause.isEnabled = true
            btnStop.isEnabled = true
            btnPlayPauseExpanded.isEnabled = true
            btnStopExpanded.isEnabled = true
            btnMinimize.isEnabled = true
            bottomNav.menu.setGroupEnabled(0, true)

            statsCardMini.isEnabled = true
            btnProfile?.isEnabled = true
        }
    }

    private fun setupClickListeners() {
        // Tap mini card to expand
        statsCardMini.setOnClickListener {
            layoutExpandedStats.alpha = 0f
            layoutExpandedStats.scaleX = 0.9f
            layoutExpandedStats.scaleY = 0.9f
            layoutExpandedStats.visibility = View.VISIBLE

            // Animate Expanded View fading/zooming IN
            layoutExpandedStats.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .start()

            // Animate Mini Card fading OUT
            statsCardMini.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction { statsCardMini.visibility = View.INVISIBLE }
                .start()
        }

        // Tap minimize button to go back
        btnMinimize.setOnClickListener {
            // Prep Mini Card to come back
            statsCardMini.visibility = View.VISIBLE
            statsCardMini.alpha = 0f

            // Animate Mini Card IN
            statsCardMini.animate()
                .alpha(1f)
                .setDuration(300)
                .start()

            // Animate Expanded View shrinking OUT
            layoutExpandedStats.animate()
                .alpha(0f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(200)
                .withEndAction { layoutExpandedStats.visibility = View.GONE }
                .start()
        }
        // Buttons logic
        val playPauseAction = {
            val isTracking = TrackingService.isTracking.value ?: false
            if (isTracking) {
                sendCommandToService("ACTION_PAUSE_SERVICE")
            } else {
                if (PermissionsHelper.hasLocationPermissions(requireContext())) {
                    sendCommandToService("ACTION_START_OR_RESUME_SERVICE")
                } else {
                    requestLocationAndNotificationPermissions()
                }
            }
        }

        btnPlayPause.setOnClickListener { playPauseAction() }
        btnPlayPauseExpanded.setOnClickListener { playPauseAction() }

        btnStop.setOnClickListener { showStopConfirmationDialog() }
        btnStopExpanded.setOnClickListener { showStopConfirmationDialog() }

        btnLock.setOnClickListener { toggleScreenLock() }
        btnLockExpanded.setOnClickListener { toggleScreenLock() }
    }

    private fun showStopConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Do you want to stop tracking?")
            .setPositiveButton("Confirm") { _, _ ->
                endRunAndNavigateToSave()
            }
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()
            .show()
    }

    private fun updateButtonVisibility(isTrackingActive: Boolean) {
        val currentTime = TrackingService.timeRunInMillis.value ?: 0L

        // Helper variables
        val playIcon = R.drawable.ic_play
        val pauseIcon = R.drawable.ic_pause

        if (isTrackingActive) {
            btnPlayPause.setImageResource(pauseIcon)
            btnPlayPauseExpanded.setImageResource(pauseIcon)

            layoutLockContainer.visibility = View.VISIBLE
            layoutLockContainerExpanded.visibility = View.VISIBLE

            btnStop.visibility = View.VISIBLE
            btnStopExpanded.visibility = View.VISIBLE

        } else if (currentTime > 0L) {
            btnPlayPause.setImageResource(playIcon)
            btnPlayPauseExpanded.setImageResource(playIcon)

            layoutLockContainer.visibility = View.VISIBLE
            layoutLockContainerExpanded.visibility = View.VISIBLE

            btnStop.visibility = View.VISIBLE
            btnStopExpanded.visibility = View.VISIBLE

        } else {
            btnPlayPause.setImageResource(playIcon)
            btnPlayPauseExpanded.setImageResource(playIcon)

            layoutLockContainer.visibility = View.GONE
            layoutLockContainerExpanded.visibility = View.GONE

            btnStop.visibility = View.GONE
            btnStopExpanded.visibility = View.GONE

            if (isScreenLocked) toggleScreenLock()
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
            tvTimerExpanded.text = formattedTime

            // Calories Math
            val caloriesBurned = ((timeInMillis / 1000f) * 0.15f).toInt()
            tvCaloriesExpanded.text = caloriesBurned.toString()

            if (timeInMillis == 0L) {
                updateButtonVisibility(TrackingService.isTracking.value ?: false)
            }
        }

        TrackingService.isTracking.observe(viewLifecycleOwner) { isTracking ->
            updateButtonVisibility(isTracking)
        }

        // Observe live location
        TrackingService.currentLocation.observe(viewLifecycleOwner) { location ->
            val point = GeoPoint(location.latitude, location.longitude)
            val segments = TrackingService.pathPoints.value ?: mutableListOf()
            val isTracking = TrackingService.isTracking.value ?: false

            mapTrackingManager.updateLiveLocation(point, segments, isTracking)
        }

        // Observe path point
        TrackingService.pathPoints.observe(viewLifecycleOwner) { segments ->
            if (segments.isNotEmpty() && segments.last().isNotEmpty()) {

                // Distance Math
                runDistanceInMeters = Calculators.calculatePolylineLength(segments)
                val distanceInKm = runDistanceInMeters / 1000f
                val formattedDistance = String.format("%.2f", distanceInKm)

                tvDistance.text = formattedDistance
                tvDistanceExpanded.text = formattedDistance

                // Pace Math
                var paceString = "--'--\""
                val timeInMillis = TrackingService.timeRunInMillis.value ?: 0L

                if (distanceInKm > 0.01 && timeInMillis > 0L) {
                    val timeInMinutes = (timeInMillis / 1000f) / 60f
                    val paceInMinutes = timeInMinutes / distanceInKm

                    if (paceInMinutes < 60.0) {
                        val paceMinutes = paceInMinutes.toInt()
                        val paceSeconds = ((paceInMinutes - paceMinutes) * 60f).toInt()
                        paceString = String.format("%d'%02d\"", paceMinutes, paceSeconds)
                    }
                }
                tvSpeed.text = paceString
                tvSpeedExpanded.text = paceString

            } else {
                // Reset math UI and clear the blue line(s)
                mapTrackingManager.clearMapLines()
                runDistanceInMeters = 0f
                tvDistance.text = "0.00"
                tvDistanceExpanded.text = "0.00"
                tvSpeed.text = "--'--\""
                tvSpeedExpanded.text = "--'--\""
            }
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        mapTrackingManager.startCompass()
        if (PermissionsHelper.hasLocationPermissions(requireContext())) {
            enableUserLocationOnMap()
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        mapTrackingManager.stopCompass()

        if (isIdleTracking) {
            fusedLocationClient.removeLocationUpdates(idleLocationCallback)
            isIdleTracking = false
        }
    }
}