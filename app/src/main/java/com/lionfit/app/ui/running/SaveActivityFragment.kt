package com.lionfit.app.ui.running

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.lionfit.app.R
import com.lionfit.app.data.database.AppDatabase
import com.lionfit.app.data.database.SupabaseManager
import com.lionfit.app.MainActivity
import com.lionfit.app.ui.shared.SharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import com.lionfit.app.data.model.RunSession
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.ByteArrayOutputStream
import android.view.MotionEvent

class SaveActivityFragment : Fragment(R.layout.fragment_save_activity) {

    // Tap into SharedViewModel
    private val sharedViewModel: SharedViewModel by activityViewModels()

    // Layout variables
    private lateinit var map: MapView
    private lateinit var etTitle: EditText
    private lateinit var etDescription: EditText
    private lateinit var spinnerType: Spinner
    private lateinit var tvDistance: TextView
    private lateinit var tvPace: TextView
    private lateinit var tvTime: TextView
    private lateinit var btnSave: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // MapView Configuration required by Osmdroid
        Configuration.getInstance().load(requireContext(), androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext()))

        // View Binding Draft
        initializeViews(view)
        setupSpinnerDraft()

        // Draft the Data
        sharedViewModel.pendingRunSession.observe(viewLifecycleOwner) { session ->
            if (session != null) {
                draftSessionDataToUi(session)
            }
        }

        // Resume button
        val btnResume = view.findViewById<TextView>(R.id.tvResume)
        @android.annotation.SuppressLint("ClickableViewAccessibility")
        btnResume.setOnTouchListener { resumeView, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    resumeView.animate()
                        .scaleX(0.9f)
                        .scaleY(0.9f)
                        .setDuration(100)
                        .start()
                    true // Tells Android we consumed this touch event
                }

                MotionEvent.ACTION_UP -> {
                    // Bounce back to normal size
                    resumeView.performClick() // For accessibility
                    resumeView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150) // Slightly longer for a smoother release
                        .start()

                    // Execute the resume logic INSTANTLY to eliminate the "freeze"
                    sharedViewModel.pendingRunSession.value = null

                    val resumeIntent = android.content.Intent(requireContext(), com.lionfit.app.services.TrackingService::class.java).apply {
                        action = com.lionfit.app.services.TrackingService.ACTION_START_OR_RESUME_SERVICE
                    }
                    requireContext().startService(resumeIntent)

                    (requireActivity() as MainActivity).switchFragment("running")

                    true
                }

                // 3. FINGER DRAGS OFF: They changed their mind and swiped away
                MotionEvent.ACTION_CANCEL -> {
                    // Just bounce back to normal size, don't trigger the resume
                    resumeView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .start()
                    true
                }

                else -> false
            }
        }

        // Save run button
        btnSave.setOnClickListener {
            val currentSession = sharedViewModel.pendingRunSession.value
            if (currentSession != null) {
                btnSave.isEnabled = false
                btnSave.text = "Saving..."

                val etTitle = view.findViewById<EditText>(R.id.etActivityTitle)
                val etDescription = view.findViewById<EditText>(R.id.etActivityDescription)
                val spinnerActivityType = view.findViewById<Spinner>(R.id.spinnerActivityType)
                val userTitle = etTitle.text.toString().trim()
                val finalTitle = if (userTitle.isNotEmpty()) userTitle else "Today's Activity"
                val selectedType = spinnerActivityType.selectedItem.toString()
                val descriptionText = etDescription.text.toString().trim()

                // Launch a coroutine because uploading takes a second
                lifecycleScope.launch {
                    try {
                        // Snap the picture
                        val imageBytes = captureMapScreenshot(map)
                        var mapImageUrl: String? = null

                        // Upload to storage
                        if (imageBytes != null) {
                            mapImageUrl = withContext(Dispatchers.IO) {
                                SupabaseManager.uploadRunSnapshot(
                                    userId = currentSession.userId,
                                    timestamp = currentSession.timestamp,
                                    imageBytes = imageBytes
                                )
                            }
                        }

                        // Attach URL to session
                        val finalSessionToSave = currentSession.copy(
                            title = finalTitle,
                            description = descriptionText,
                            activityType = selectedType,
                            mapSnapshotUrl = mapImageUrl
                        )
                        saveFinalRunData(finalSessionToSave)

                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(requireContext(), "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                        // Re-enable the button if it fails so they can try again
                        btnSave.isEnabled = true
                        btnSave.text = "Save Activity"
                    }
                }
            }
        }
    }

    private fun initializeViews(view: View) {
        map = view.findViewById(R.id.map_history)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setBuiltInZoomControls(false)
        map.setMultiTouchControls(false) // Pinch to zoom
        map.setOnTouchListener { _, _ -> true } // Lock user from moving the map

        etTitle = view.findViewById(R.id.etActivityTitle)
        etDescription = view.findViewById(R.id.etActivityDescription)
        spinnerType = view.findViewById(R.id.spinnerActivityType)
        tvDistance = view.findViewById(R.id.tvFinalDistance)
        tvPace = view.findViewById(R.id.tvFinalPace)
        tvTime = view.findViewById(R.id.tvFinalTime)
        btnSave = view.findViewById(R.id.btnSaveActivity)
    }

    private fun setupSpinnerDraft() {
        val activities = arrayOf("Walk", "Run", "Ride")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, activities)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = adapter
    }

    private fun draftSessionDataToUi(session: RunSession) {
        // Distance handling
        tvDistance.text = String.format("%.2f km", session.distanceInKm)

        // Pace handling
        val rawPace = session.averagePace
        val paceMinutes = rawPace.toInt()
        val paceSeconds = ((rawPace - paceMinutes) * 60f).toInt()
        val finalPaceString = String.format("%d'%02d\"", paceMinutes, paceSeconds)
        tvPace.text = finalPaceString

        // Time handling (convert millis to Minutes)
        val minutes = (session.durationInMillis / 1000) / 60
        tvTime.text = "$minutes Min"

        draftPathToMiniMap(session)
    }

    private fun draftPathToMiniMap(session: RunSession) {
        map.overlays.clear()

        val allPointsForCamera = mutableListOf<GeoPoint>()

        session.pathCoordinates?.forEach { segment ->
            val mapPoints = segment.map { GeoPoint(it.lat, it.lng) }

            if (mapPoints.isNotEmpty()) {
                allPointsForCamera.addAll(mapPoints)

                val polyline = Polyline()
                polyline.setPoints(mapPoints)
                polyline.color = android.graphics.Color.parseColor("#FFAB00")
                polyline.width = 10f
                map.overlays.add(polyline)
            }
        }

        if (allPointsForCamera.isNotEmpty()) {
            map.post {
                val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(allPointsForCamera)
                val diagonalDistance = boundingBox.diagonalLengthInMeters

                if (diagonalDistance < 50.0) {
                    // If the run was super short (or a single point), manually set a safe zoom
                    map.controller.setZoom(19.0)
                    map.controller.setCenter(allPointsForCamera.first())
                } else {
                    // Normal run, use the bounding box as usual
                    map.zoomToBoundingBox(boundingBox, false, 50)
                    // Restrict the max zoom just in case
                    map.maxZoomLevel = 20.0
                }
            }
        }
    }

    private fun saveFinalRunData(finalSession: com.lionfit.app.data.model.RunSession) {
        // Save it to Room and Supabase (Moved from RunningFragment)
        val runDao = AppDatabase.getDatabase(requireContext()).runDao()

        lifecycleScope.launch(Dispatchers.IO) {
            runDao.insertRun(finalSession)
            val isSynced = SupabaseManager.saveRunSession(finalSession)

            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Activity Saved!", Toast.LENGTH_SHORT).show()
                sharedViewModel.pendingRunSession.value = null
                // Kill the GPS Service
                sendCommandToService(com.lionfit.app.services.TrackingService.ACTION_STOP_SERVICE)
                btnSave.isEnabled = true
                btnSave.text = "Save Activity"
                view?.findViewById<EditText>(R.id.etActivityTitle)?.text?.clear()
                view?.findViewById<EditText>(R.id.etActivityDescription)?.text?.clear()
                (requireActivity() as MainActivity).switchFragment("running")
            }
        }
    }

    private fun captureMapScreenshot(mapView: MapView): ByteArray? {
        // Safety check to ensure the map actually has a size
        if (mapView.width == 0 || mapView.height == 0) return null

        try {
            val bitmap = Bitmap.createBitmap(mapView.width, mapView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            mapView.draw(canvas)

            // Compression
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)

            return stream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun sendCommandToService(action: String) {
        android.content.Intent(requireContext(), com.lionfit.app.services.TrackingService::class.java).also {
            it.action = action
            requireContext().startService(it)
        }
    }
}