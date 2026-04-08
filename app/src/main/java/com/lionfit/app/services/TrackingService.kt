package com.lionfit.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lionfit.app.R

class TrackingService : Service() {

    private var isFirstRun = true
    private var isTimerEnabled = false
    private var timeStarted = 0L
    private var timeRun = 0L

    // Companion object allows other files to observe these variables easily
    companion object {
        val isTracking = MutableLiveData<Boolean>()
        val timeRunInMillis = MutableLiveData<Long>()
    }

    private fun postInitialValues() {
        isTracking.postValue(false)
        timeRunInMillis.postValue(0L)
    }

    override fun onCreate() {
        super.onCreate()
        postInitialValues()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                "ACTION_START_OR_RESUME_SERVICE" -> {
                    if (isFirstRun) {
                        startForegroundService()
                        isFirstRun = false
                    }
                    startTimer()
                }
                "ACTION_PAUSE_SERVICE" -> {
                    pauseService()
                }
                "ACTION_STOP_SERVICE" -> {
                    killService()
                }
            }
        }
        return START_STICKY
    }

    private fun pauseService() {
        // This instantly stops the coroutine while loop and saves the elapsed time
        isTracking.postValue(false)
        isTimerEnabled = false

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationBuilder = NotificationCompat.Builder(this, "tracking_channel")
            .setAutoCancel(false)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_pause)
            .setContentTitle("LionFit")
            .setContentText("Running service paused")

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

        // This loop ticks up the timer in the background
        CoroutineScope(Dispatchers.Main).launch {
            while (isTracking.value == true) {
                val lapTime = System.currentTimeMillis() - timeStarted
                timeRunInMillis.postValue(timeRun + lapTime)
                delay(50L) // Update frequently for a smooth UI
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

        val notificationBuilder = NotificationCompat.Builder(this, "tracking_channel")
            .setAutoCancel(false)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle("LionFit")
            .setContentText("Tracking your run...")

        startForeground(1, notificationBuilder.build())
    }
}