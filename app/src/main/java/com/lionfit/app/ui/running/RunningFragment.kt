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
import android.widget.FrameLayout
import com.google.android.gms.location.LocationServices
import com.lionfit.app.utils.MapTrackingManager
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import android.os.Looper
import com.lionfit.app.data.database.AppDatabase
import com.lionfit.app.data.database.RunDao
import com.lionfit.app.data.database.SupabaseManager
import com.lionfit.app.data.model.RoutePoint
import com.lionfit.app.data.model.RunSession
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.lionfit.app.ui.shared.SharedViewModel
import com.lionfit.app.MainActivity
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

class RunningFragment : Fragment(R.layout.fragment_running) {
    private lateinit var runDao: RunDao
    private val sharedViewModel: SharedViewModel by activityViewModels()
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
    // global average fallback
    private var currentUserWeight: Double = 70.0

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
                    val speed = location.speed
                    val bearing = location.bearing
                    val hasBearing = location.hasBearing()

                    // Move the marker with the hybrid parameters
                    mapTrackingManager.updateLiveLocation(
                        newPosition = point,
                        segments = emptyList(),
                        isTracking = false,
                        speed = speed,
                        bearing = bearing,
                        hasBearing = hasBearing
                    )
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocationGranted) {
            startGpsPreview() // Enable once the permission is granted
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

        // Fetch user weight
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentUser = SupabaseManager.client.auth.currentUserOrNull()
                if (currentUser != null) {
                    val profile = SupabaseManager.getProfile(currentUser.id)
                    // Only update if they actually entered a weight greater than 0
                    if (profile != null && profile.weightKg > 0.0) {
                        currentUserWeight = profile.weightKg
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Check permissions the moment the Run tab opens
        if (PermissionsHelper.hasLocationPermissions(requireContext())) {
            startGpsPreview()
        } else {
            // Default center just in case they deny it
            map.controller.setZoom(19.0)
            map.controller.setCenter(GeoPoint(13.8256, 100.4485))
            requestLocationAndNotificationPermissions()
        }
        setupClickListeners()
        subscribeToObservers()
    }

    private fun endRunAndNavigateToSave() {
        val duration = TrackingService.timeRunInMillis.value ?: 0L
        val distanceKm = runDistanceInMeters / 1000.0

        if (duration > 0L && distanceKm > 0.0) {
            val timeInMinutes = (duration / 1000f) / 60f
            val avgPace = timeInMinutes / distanceKm
            val calories = (distanceKm * currentUserWeight * 1.036).toInt()
            val timestamp = System.currentTimeMillis()
            val currentUser = SupabaseManager.client.auth.currentUserOrNull()
            val currentUserId = currentUser?.id

            // Safety net: If user session expired mid-run
            if (currentUserId == null) {
                Toast.makeText(requireContext(), "Error: Could not verify user identity. Please log in again.", Toast.LENGTH_LONG).show()
                return
            }

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

            val pauseIntent = Intent(requireContext(), TrackingService::class.java).apply {
                action = TrackingService.ACTION_PAUSE_SERVICE
            }
            requireContext().startService(pauseIntent)
            resetToMiniCard()
            // Switch the screen to the Save Form
            (requireActivity() as MainActivity).switchFragment("save_activity")

        } else {
            Toast.makeText(requireContext(), "Run too short to save.", Toast.LENGTH_SHORT).show()
            // Just kill the service if it was a mistake
            sendCommandToService("ACTION_STOP_SERVICE")
        }
    }

    // TODO: Remove simulateFakeRun() since this is only for testing.
    private fun simulateFakeRun() {
        // 1. Generate Realistic Core Stats
        // Distance between 1.00 km and 12.00 km
        val randomDistance = kotlin.math.round(Random.nextDouble(1.0, 12.0) * 100) / 100.0

        // Pace between 4.5 (super fast) and 12.0 (walking pace) mins per km
        val randomPace = kotlin.math.round(Random.nextDouble(4.5, 12.0) * 100) / 100.0

        // Math: Time = Distance * Pace
        val totalMinutes = randomDistance * randomPace
        val randomDurationMillis = (totalMinutes * 60 * 1000).toLong()

        // Math: Calories = ~70 kcals per km (with a slight random variation)
        val randomCalories = (randomDistance * Random.nextInt(65, 85)).toInt()

        // 2. Generate a random wandering GPS path
        var currentLat = 13.9644
        var currentLng = 100.5871
        val numPoints = Random.nextInt(10, 30) // Random amount of GPS dots
        val routePoints = mutableListOf<RoutePoint>()

        for (i in 0 until numPoints) {
            routePoints.add(RoutePoint(currentLat, currentLng))
            // Add a tiny random offset (simulates moving about 10-50 meters in a random direction)
            currentLat += Random.nextDouble(-0.001, 0.001)
            currentLng += Random.nextDouble(-0.001, 0.001)
        }

        // Wrap it in the segment list
        val mockPath = listOf(routePoints)

        // 3. Build the fake finished session
        val fakeSession = RunSession(
            id = java.util.UUID.randomUUID().toString(),
            userId = SupabaseManager.client.auth.currentUserOrNull()?.id ?: "",
            // Randomize the start time between "right now" and "up to 3 days ago"
            timestamp = System.currentTimeMillis() - Random.nextLong(0, 259200000),
            durationInMillis = randomDurationMillis,
            distanceInKm = randomDistance,
            averagePace = randomPace,
            caloriesBurned = randomCalories,
            pathCoordinates = mockPath,
            title = "",
            description = "",
            activityType = "Run",
            mapSnapshotUrl = null // Starts null, your SaveActivity will fill it!
        )

        // 4. Inject it into the memory box and jump to the Save screen
        sharedViewModel.pendingRunSession.value = fakeSession
        val pauseIntent = Intent(requireContext(), TrackingService::class.java).apply {
            action = TrackingService.ACTION_PAUSE_SERVICE
        }
        requireContext().startService(pauseIntent)
        resetToMiniCard()
        (requireActivity() as MainActivity).switchFragment("save_activity")
    }

    private fun resetToMiniCard() {
        // Instantly snap everything back to the default mini state
        layoutExpandedStats.visibility = View.GONE
        layoutExpandedStats.alpha = 0f
        layoutExpandedStats.scaleX = 0.9f
        layoutExpandedStats.scaleY = 0.9f

        statsCardMini.visibility = View.VISIBLE
        statsCardMini.alpha = 1f
    }

    // This triggers whenever MainActivity hides or shows this fragment
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        manageGpsBattery(isHidden = hidden)
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

        val fabHistory = view?.findViewById<FloatingActionButton>(R.id.fabHistory)
        fabHistory?.setOnClickListener {
            // Shout to MainActivity to change the screen!
            (requireActivity() as MainActivity).switchFragment("run_history")
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
                simulateFakeRun()
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

            val speed = location.speed
            val bearing = location.bearing
            val hasBearing = location.hasBearing()

            // Move the marker with the hybrid parameters
            mapTrackingManager.updateLiveLocation(
                newPosition = point,
                segments = segments,
                isTracking = isTracking,
                speed = speed,
                bearing = bearing,
                hasBearing = hasBearing
            )
        }

        // Observe path point
        TrackingService.pathPoints.observe(viewLifecycleOwner) { segments ->
            if (segments.isNotEmpty() && segments.last().isNotEmpty()) {

                // Distance Math
                runDistanceInMeters = Calculators.calculatePolylineLength(segments)
                val distanceInKm = runDistanceInMeters / 1000f
                val formattedDistance = String.format("%.2f", distanceInKm)
                // Calories Math
                val liveCalories = (distanceInKm * currentUserWeight * 1.036).toInt()
                tvCaloriesExpanded.text = liveCalories.toString()

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
                tvCaloriesExpanded.text = "0"
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
        manageGpsBattery(isHidden = false)
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        mapTrackingManager.stopCompass()
        manageGpsBattery(isHidden = true)
    }

    private fun manageGpsBattery(isHidden: Boolean) {
        // SAFETY CHECK: Are we currently in the middle of a workout?
        val isActivelyRunning = TrackingService.isTracking.value ?: false

        if (isActivelyRunning) {
            return
        }

        if (isHidden) {
            // User switched to Account Tab or locked phone. Kill the preview to save battery!
            stopGpsPreview()
        } else {
            // User is looking at the map waiting to run. Wake up the GPS!
            startGpsPreview()
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startGpsPreview() {
        if (!PermissionsHelper.hasLocationPermissions(requireContext())) return

        // Only start it if it isn't already running
        if (!isIdleTracking) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(2000L)
                .build()

            fusedLocationClient.requestLocationUpdates(
                request,
                idleLocationCallback,
                Looper.getMainLooper()
            )
            isIdleTracking = true

            // Snap the map to them instantly
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val startPoint = GeoPoint(it.latitude, it.longitude)
                    mapTrackingManager.showInitialMarker(startPoint)
                }
            }
        }
    }

    private fun stopGpsPreview() {
        if (isIdleTracking) {
            fusedLocationClient.removeLocationUpdates(idleLocationCallback)
            isIdleTracking = false
        }
    }
}