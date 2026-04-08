package com.lionfit.app.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
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

class TrackingService : Service() {

    private var isFirstRun = true
    private var isTimerEnabled = false
    private var timeStarted = 0L
    private var timeRun = 0L

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder

    companion object {
        val isTracking = MutableLiveData<Boolean>()
        val timeRunInMillis = MutableLiveData<Long>()
        // THIS IS THE LIST THAT HOLDS YOUR RUNNING COORDINATES
        val pathPoints = MutableLiveData<MutableList<GeoPoint>>()
    }

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
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle("LionFit")
            .setContentText("Tracking your run...")

        // Listen for tracking state changes to turn GPS on/off
        isTracking.observeForever { isTracking ->
            updateLocationTracking(isTracking)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                "ACTION_START_OR_RESUME_SERVICE" -> {
                    if (isTracking.value == true) return START_STICKY

                    if (isFirstRun) {
                        startForegroundService()
                        isFirstRun = false
                    } else {
                        notificationBuilder.setContentText("Tracking your run...")
                        notificationBuilder.setSmallIcon(R.drawable.ic_play)
                        notificationManager.notify(1, notificationBuilder.build())
                    }
                    startTimer()
                }
                "ACTION_PAUSE_SERVICE" -> pauseService()
                "ACTION_STOP_SERVICE" -> killService()
            }
        }
        return START_STICKY
    }

    // THE GPS CALLBACK THAT FIRES EVERY FEW SECONDS
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            if (isTracking.value == true) {
                result.locations.let { locations ->
                    for (location in locations) {
                        addPathPoint(location)
                    }
                }
            }
        }
    }

    private fun addPathPoint(location: Location?) {
        location?.let {
            val pos = GeoPoint(it.latitude, it.longitude)
            pathPoints.value?.apply {
                add(pos)
                pathPoints.postValue(this)
            }
        }
    }

    // START/STOP REQUESTING GPS COORDS BASED ON PLAY/PAUSE
    @SuppressLint("MissingPermission")
    private fun updateLocationTracking(isTracking: Boolean) {
        if (isTracking) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(2000L)
                .build()
            fusedLocationProviderClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } else {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        }
    }

    private fun pauseService() {
        // This instantly stops the coroutine while loop and saves the elapsed time
        isTracking.postValue(false)
        isTimerEnabled = false
        notificationBuilder.setContentText("Running service paused")
        notificationBuilder.setSmallIcon(R.drawable.ic_pause)
        notificationManager.notify(1, notificationBuilder.build())

    }


    private fun killService() {
        isTracking.postValue(false)
        isTimerEnabled = false
        postInitialValues() // Resets time back to 00:00
        isFirstRun = true
        stopForeground(true)
        stopSelf() // Tells the Android OS to completely destroy the service
    }

    private fun startTimer() {
        isTracking.postValue(true)
        timeStarted = System.currentTimeMillis()
        isTimerEnabled = true

        CoroutineScope(Dispatchers.Main).launch {
            while (isTracking.value == true) {
                val lapTime = System.currentTimeMillis() - timeStarted
                timeRunInMillis.postValue(timeRun + lapTime)
                delay(50L)
            }
            timeRun += (System.currentTimeMillis() - timeStarted)
        }
    }

    private fun startForegroundService() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("tracking_channel", "Active Run Tracker", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
        startForeground(1, notificationBuilder.build())
    }
}