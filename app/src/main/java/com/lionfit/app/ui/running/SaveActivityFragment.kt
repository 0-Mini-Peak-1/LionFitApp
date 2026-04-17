package com.lionfit.app.ui.running

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
import com.lionfit.app.ui.shared.RunSharedViewModel
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
import android.graphics.Color
import com.lionfit.app.data.model.RunSession
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView

// TODO: Clear this file's draft comments
class SaveActivityFragment : Fragment(R.layout.fragment_save_activity) {

    // Tap into SharedViewModel
    private val sharedViewModel: RunSharedViewModel by activityViewModels()

    // Draft layout variables
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

        btnSave.setOnClickListener {
            val currentSession = sharedViewModel.pendingRunSession.value
            if (currentSession != null) {
                saveFinalRunData(currentSession)
            }
        }
    }

    private fun initializeViews(view: View) {
        map = view.findViewById(R.id.map_history)
        etTitle = view.findViewById(R.id.etActivityTitle)
        etDescription = view.findViewById(R.id.etActivityDescription)
        spinnerType = view.findViewById(R.id.spinnerActivityType)
        tvDistance = view.findViewById(R.id.tvFinalDistance)
        tvPace = view.findViewById(R.id.tvFinalPace)
        tvTime = view.findViewById(R.id.tvFinalTime)
        btnSave = view.findViewById(R.id.btnSaveActivity)
    }

    private fun setupSpinnerDraft() {
        // Draft: Basic dropdown setup. We will map this back to Supabase "activity_type" later.
        val activities = arrayOf("Walk", "Run", "Ride")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, activities)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = adapter
    }

    private fun draftSessionDataToUi(session: RunSession) {
        // Draft: Populare the text labels with the math we already calculated
        tvDistance.text = String.format("%.2f km", session.distanceInKm)

        // Pace handling (e.g., convert 5.5 to 5:30/km for UI display)
        val paceText = String.format("%.2f min/km", session.averagePace)
        tvPace.text = paceText

        // Time handling (convert millis to roughly Minutes)
        val minutes = (session.durationInMillis / 1000) / 60
        tvTime.text = "$minutes Min"

        // --- Draft: Drawing the path on the map ---
        draftPathToMiniMap(session)
    }

    private fun draftPathToMiniMap(session: RunSession) {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(false)
        map.controller.setZoom(16.0)
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
                map.zoomToBoundingBox(boundingBox, false, 100)
            }
        }
    }

    private fun saveFinalRunData(session: com.lionfit.app.data.model.RunSession) {
        // TODO: Later, grab the REAL text from your UI Input Fields
        val uiTitle = "Night Walk" // e.g., titleEditText.text.toString()
        val uiDescription = "Felt great!"
        val uiType = "Walk"

        // Make a copy of the session, but update it with the new UI strings
        val finalSession = session.copy(
            title = uiTitle,
            description = uiDescription,
            activityType = uiType
        )

        // Save it to Room and Supabase (Moved from RunningFragment)
        val runDao = AppDatabase.getDatabase(requireContext()).runDao()

        lifecycleScope.launch(Dispatchers.IO) {
            runDao.insertRun(finalSession)
            val isSynced = SupabaseManager.syncRunToCloud(finalSession)

            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Activity Saved!", Toast.LENGTH_SHORT).show()
                sharedViewModel.pendingRunSession.value = null
                // Kill the GPS Service
                sendCommandToService("ACTION_STOP_SERVICE")
                (requireActivity() as MainActivity).switchFragment("dashboard")
            }
        }
    }

    private fun sendCommandToService(action: String) {
        android.content.Intent(requireContext(), com.lionfit.app.services.TrackingService::class.java).also {
            it.action = action
            requireContext().startService(it)
        }
    }
}