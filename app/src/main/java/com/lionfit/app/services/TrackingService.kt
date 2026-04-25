package com.lionfit.app.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import com.lionfit.app.R
import android.widget.RemoteViews
import android.app.PendingIntent
import com.lionfit.app.MainActivity
import java.util.Locale

class TrackingService : Service() {

    private var isFirstRun = true
    private var isTimerEnabled = false
    private var timeStarted = 0L
    private var timeRun = 0L
    private var serviceKilled = false

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder

    companion object {
        val timeRunInMillis = MutableLiveData<Long>()
        val isTracking = MutableLiveData<Boolean>()
        val pathPoints = MutableLiveData<MutableList<MutableList<GeoPoint>>>()
        val currentLocation = MutableLiveData<android.location.Location>()
        const val ACTION_START_OR_RESUME_SERVICE = "ACTION_START_OR_RESUME_SERVICE"
        const val ACTION_PAUSE_SERVICE = "ACTION_PAUSE_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        val cheaterAlert = MutableLiveData<Boolean>()
    }

    private val CHEATER_SPEED_THRESHOLD_MS = 7.0f // 7 meters per second (~25 km/h)
    private var cheaterStrikeCount = 0
    private val MAX_STRIKES = 3

    private fun postInitialValues() {
        isTracking.postValue(false)
        timeRunInMillis.postValue(0L)
        pathPoints.postValue(mutableListOf()) // Start with an empty path
    }

    override fun onCreate() {
        super.onCreate()
        postInitialValues()
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationBuilder = NotificationCompat.Builder(this, "tracking_channel")
            .setAutoCancel(false)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_notif_lion)
            .setContentTitle("LionFit")
            .setContentText("Tracking your run...")
    }

    private fun addEmptyPolyline() {
        pathPoints.value?.apply {
            add(mutableListOf())
            pathPoints.postValue(this)
        } ?: pathPoints.postValue(mutableListOf(mutableListOf()))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_OR_RESUME_SERVICE -> {
                    if (isTracking.value == true) return START_STICKY

                    if (isFirstRun) {
                        startForegroundService()
                        startLocationUpdates()
                        isFirstRun = false
                    }
                    addEmptyPolyline()
                    startTimer()
                }
                ACTION_PAUSE_SERVICE -> pauseService()
                ACTION_STOP_SERVICE -> killService()
            }
        }
        return START_STICKY
    }

    // THE GPS CALLBACK THAT FIRES EVERY FEW SECONDS
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            result.locations.let { locations ->
                for (location in locations) {
                    // Always update current location
                    currentLocation.postValue(location)

                    // If the timer is paused, stop drawing
                    if (isTracking.value != true) {
                        continue
                    }

                    // The Drift Bouncer
                    if (location.accuracy > 20f) continue
                    val currentSpeed = if (location.hasSpeed()) location.speed else 0f

                    // Cheater detection
                    if (currentSpeed > CHEATER_SPEED_THRESHOLD_MS) {
                        cheaterStrikeCount++

                        if (cheaterStrikeCount >= MAX_STRIKES) {
                            handleCheaterDetected()
                            continue // KILL SWITCH: Stop processing this point entirely
                        }
                    } else {
                        // They slowed down or are running normally. Reset the strikes
                        cheaterStrikeCount = 0
                    }

                    // Grab the last point recorded
                    val currentSegment = pathPoints.value?.last()
                    val lastPoint = currentSegment?.lastOrNull()

                    if (lastPoint != null) {
                        // Calculate the distance between the last point and this new one
                        val results = FloatArray(1)
                        android.location.Location.distanceBetween(
                            lastPoint.latitude, lastPoint.longitude,
                            location.latitude, location.longitude,
                            results
                        )
                        val distanceMoved = results[0]

                        if (distanceMoved < 3f) {
                            continue
                        }
                    }
                    addPathPoint(location)
                }
            }
        }
    }

    private fun handleCheaterDetected() {
        // Pause the timer and stop recording data immediately
        isTracking.postValue(false)

        // Reset the strike count so it doesn't stay permanently locked
        cheaterStrikeCount = 0

        // Fire the flare to the RunningFragment
        cheaterAlert.postValue(true)
    }

    private fun addPathPoint(location: android.location.Location?) {
        location?.let {
            val pos = GeoPoint(location.latitude, location.longitude)
            pathPoints.value?.apply {
                last().add(pos)
                pathPoints.postValue(this)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        fusedLocationProviderClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun pauseService() {
        // This instantly stops the coroutine while loop and saves the elapsed time
        isTracking.postValue(false)
        isTimerEnabled = false
        // Call custom notification
        updateCustomNotification(isPaused = true)
    }

    private fun killService() {
        serviceKilled = true
        isFirstRun = true
        timeRun = 0L
        timeStarted = 0L
        timeRunInMillis.postValue(0L)
        pathPoints.postValue(mutableListOf())
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        isTracking.postValue(false)
        isTimerEnabled = false
        stopForeground(true)
        stopSelf() // Tells the OS to completely destroy the service
    }

    private fun startTimer() {
        serviceKilled = false
        isTracking.postValue(true)
        timeStarted = System.currentTimeMillis()
        isTimerEnabled = true

        CoroutineScope(Dispatchers.Main).launch {
            while (isTracking.value == true) {
                val lapTime = System.currentTimeMillis() - timeStarted
                timeRunInMillis.postValue(timeRun + lapTime)
                delay(50L)
            }
            if (serviceKilled) {
                // The run stopped
                timeRun = 0L
                timeStarted = 0L
                timeRunInMillis.postValue(0L)
                pathPoints.postValue(mutableListOf())
            } else {
                // The run paused
                timeRun += (System.currentTimeMillis() - timeStarted)
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            while (isTracking.value == true) {
                updateCustomNotification(isPaused = false)
                delay(1000L) // Wait 1 sec
            }
        }
    }

    // --- THE CUSTOM NOTIFICATION BUILDER ---
    private fun updateCustomNotification(isPaused: Boolean) {
        val timeMs = timeRunInMillis.value ?: 0L
        val distanceKm = calculateTotalDistance()

        val timeStr = formatTime(timeMs)
        val distStr = String.format(Locale.getDefault(), "%.2f", distanceKm)
        val paceStr = formatPace(distanceKm, timeMs)

        // The tap action to open the Run tab
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_FRAGMENT", "run")
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Bind to the XML layout
        val remoteViews = RemoteViews(packageName, R.layout.layout_notification_run).apply {
            setTextViewText(R.id.notif_time, timeStr)
            setTextViewText(R.id.notif_distance, "$distStr km")
            setTextViewText(R.id.notif_pace, paceStr)

            if (isPaused) {
                setTextViewText(R.id.notif_title, "Paused")
                setTextColor(R.id.notif_title, android.graphics.Color.parseColor("#E57373")) // Red
            } else {
                setTextViewText(R.id.notif_title, "Running...")
                setTextColor(R.id.notif_title, android.graphics.Color.parseColor("#81C784")) // Green
            }
        }

        // Apply it to the global builder and trigger it
        notificationBuilder.setContent(remoteViews)
        notificationBuilder.setContentIntent(pendingIntent)
        notificationBuilder.setStyle(NotificationCompat.DecoratedCustomViewStyle())

        notificationManager.notify(1, notificationBuilder.build())
    }

    // --- NOTIFICATION HELPER MATH ---
    private fun calculateTotalDistance(): Float {
        var distance = 0f
        pathPoints.value?.let { points ->
            for (polyline in points) {
                for (i in 0 until polyline.size - 1) {
                    val pos1 = polyline[i]
                    val pos2 = polyline[i + 1]
                    val result = FloatArray(1)
                    android.location.Location.distanceBetween(
                        pos1.latitude, pos1.longitude,
                        pos2.latitude, pos2.longitude,
                        result
                    )
                    distance += result[0]
                }
            }
        }
        return distance / 1000f // Convert meters to km
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
        else String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    private fun formatPace(distanceKm: Float, timeMs: Long): String {
        if (distanceKm < 0.01f || timeMs == 0L) return "--:--"
        val timeMinutes = timeMs / 60000.0
        val paceMinPerKm = timeMinutes / distanceKm
        val paceMin = paceMinPerKm.toInt()
        val paceSec = ((paceMinPerKm - paceMin) * 60).toInt()
        return String.format(Locale.getDefault(), "%d:%02d", paceMin, paceSec)
    }

    private fun startForegroundService() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel("tracking_channel", "Active Run Tracker", NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)
        startForeground(1, notificationBuilder.build())
    }
}