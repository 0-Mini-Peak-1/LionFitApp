package com.lionfit.app.ui.history

import android.os.Bundle
import android.widget.PopupMenu
import android.view.View
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lionfit.app.MainActivity
import com.lionfit.app.R
import com.lionfit.app.data.database.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.lionfit.app.data.model.RunSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.android.material.button.MaterialButtonToggleGroup
import java.io.File
import java.io.FileOutputStream

class RunHistoryFragment : Fragment(R.layout.fragment_run_history) {

    private lateinit var runHistoryAdapter: RunHistoryAdapter
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var allRuns = listOf<RunSession>()
    private var currentFilterId = R.id.btnFilterAll
    // Track the current sorting state
    enum class SortType { NEWEST, OLDEST, LONGEST_DISTANCE, LONGEST_TIME }
    private var currentSortType = SortType.NEWEST

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up the Back Button
        val btnBack = view.findViewById<ImageButton>(R.id.btn_back_to_run)
        btnBack.setOnClickListener {
            // Slide back to the running screen
            (requireActivity() as MainActivity).switchFragment("running")
        }

        // Set up the Filter Buttons
        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleFilterGroup)
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentFilterId = checkedId
                applyFilterAndUpdateUI()
            }
        }

        // Set up sort button
        val btnSort = view.findViewById<ImageButton>(R.id.btn_sort_runs)
        btnSort.setOnClickListener { anchor ->
            val popup = PopupMenu(requireContext(), anchor)
            popup.menu.add(0, 1, 0, "Newest First")
            popup.menu.add(0, 2, 0, "Oldest First")
            popup.menu.add(0, 3, 0, "Longest Distance")
            popup.menu.add(0, 4, 0, "Longest Time")

            popup.setOnMenuItemClickListener { item ->
                currentSortType = when (item.itemId) {
                    2 -> SortType.OLDEST
                    3 -> SortType.LONGEST_DISTANCE
                    4 -> SortType.LONGEST_TIME
                    else -> SortType.NEWEST
                }
                applyFilterAndUpdateUI() // Re-run the math and sort the list
                true
            }
            popup.show()
        }

        // Set up the RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_run_history)
        runHistoryAdapter = RunHistoryAdapter(emptyList()) { clickedRun ->
            // Open the Bottom Sheet
            val bottomSheet = RunDetailBottomSheet(
                runSession = clickedRun,
                onEditClicked = { runToEdit -> showEditTitleDialog(runToEdit) },
                onShareClicked = { runToShare -> shareRunDetails(runToShare) },
                onDeleteClicked = { runToDelete -> showDeleteConfirmationDialog(runToDelete) }
            )
            bottomSheet.show(parentFragmentManager, "RunDetailSheet")
        }
        recyclerView.adapter = runHistoryAdapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Swipe to refresh
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        swipeRefreshLayout?.setOnRefreshListener {
            fetchRunHistory() // User pulled down manually!
        }

        // Fetch the first time the app opens
        fetchRunHistory()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            // Auto-Fetch in the background
            swipeRefreshLayout?.isRefreshing = true // Show the spinner
            fetchRunHistory()
        }
    }

    private fun fetchRunHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentUser = SupabaseManager.client.auth.currentUserOrNull()
                if (currentUser != null) {
                    // Ask the database for this specific user's runs
                    val userRuns = SupabaseManager.getUserRunHistory(currentUser.id)

                    // Switch back to Main thread to update the UI
                    withContext(Dispatchers.Main) {
                        // 🌟 Instead of applying to the adapter directly, save to master list and filter!
                        allRuns = userRuns
                        applyFilterAndUpdateUI()

                        swipeRefreshLayout?.isRefreshing = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    // Turn off the spinner even if it fails
                    swipeRefreshLayout?.isRefreshing = false
                }
            }
        }
    }

    // The brains of the Dashboard
    private fun applyFilterAndUpdateUI() {
        val currentView = view ?: return
        val now = java.util.Calendar.getInstance()
        val currentYear = now.get(java.util.Calendar.YEAR)
        val currentMonth = now.get(java.util.Calendar.MONTH)
        val currentWeek = now.get(java.util.Calendar.WEEK_OF_YEAR)

        // Determine the title first before touching the list
        val filteredTitle = when (currentFilterId) {
            R.id.btnFilterYear -> "This Year's Summary"
            R.id.btnFilterMonth -> "This Month's Summary"
            R.id.btnFilterWeek -> "This Week's Summary"
            else -> "All Time Summary"
        }

        // FILTER THE DATA
        val filteredRuns = allRuns.filter { run ->
            val runCal = java.util.Calendar.getInstance().apply { timeInMillis = run.timestamp }

            when (currentFilterId) {
                R.id.btnFilterYear -> {
                    runCal.get(java.util.Calendar.YEAR) == currentYear
                }
                R.id.btnFilterMonth -> {
                    runCal.get(java.util.Calendar.YEAR) == currentYear &&
                            runCal.get(java.util.Calendar.MONTH) == currentMonth
                }
                R.id.btnFilterWeek -> {
                    runCal.get(java.util.Calendar.YEAR) == currentYear &&
                            runCal.get(java.util.Calendar.WEEK_OF_YEAR) == currentWeek
                }
                else -> true // btnFilterAll
            }
        }

        // CRUNCH THE MATH
        val totalDistance = filteredRuns.sumOf { it.distanceInKm.toDouble() }.toFloat()
        val totalTimeMs = filteredRuns.sumOf { it.durationInMillis }
        val totalRuns = filteredRuns.size

        // UPDATE THE SUMMARY CARD UI
        currentView.findViewById<TextView>(R.id.tvSummaryTitle).text = filteredTitle
        currentView.findViewById<TextView>(R.id.tvSumRuns).text = totalRuns.toString()
        currentView.findViewById<TextView>(R.id.tvSumDistance).text = String.format(java.util.Locale.getDefault(), "%.2f km", totalDistance)
        currentView.findViewById<TextView>(R.id.tvSumTime).text = formatTime(totalTimeMs)
        currentView.findViewById<TextView>(R.id.tvSumPace).text = formatPace(totalDistance, totalTimeMs)

        // UPDATE RECYCLER VIEW AND EMPTY STATE
        val emptyState = currentView.findViewById<View>(R.id.layout_empty_state)
        val recyclerView = currentView.findViewById<View>(R.id.rv_run_history)

        if (filteredRuns.isEmpty()) {
            emptyState?.visibility = View.VISIBLE
            recyclerView?.visibility = View.GONE
        } else {
            emptyState?.visibility = View.GONE
            recyclerView?.visibility = View.VISIBLE

            // Apply the current Sort selection
            val sortedList = when (currentSortType) {
                SortType.NEWEST -> filteredRuns.sortedByDescending { it.timestamp }
                SortType.OLDEST -> filteredRuns.sortedBy { it.timestamp }
                SortType.LONGEST_DISTANCE -> filteredRuns.sortedByDescending { it.distanceInKm }
                SortType.LONGEST_TIME -> filteredRuns.sortedByDescending { it.durationInMillis }
            }

            runHistoryAdapter.submitList(sortedList)
        }
    }

    // Math helpers:
    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.getDefault(), "%02d:%02d", m, s)
    }

    private fun formatPace(distanceKm: Float, timeMs: Long): String {
        if (distanceKm < 0.01f || timeMs == 0L) return "--:--"
        val timeMinutes = timeMs / 60000.0
        val paceMinPerKm = timeMinutes / distanceKm
        val paceMin = paceMinPerKm.toInt()
        val paceSec = ((paceMinPerKm - paceMin) * 60).toInt()
        return String.format(java.util.Locale.getDefault(), "%d:%02d", paceMin, paceSec)
    }

    // Edit run title
    private fun showEditTitleDialog(runSession: RunSession) {
        val context = requireContext()
        val editText = android.widget.EditText(context).apply {
            setText(runSession.title)
            // Put the cursor at the end of the text automatically
            setSelection(runSession.title.length)
        }

        // Add some nice padding so the text box isn't touching the screen edges
        val layout = android.widget.FrameLayout(context)
        layout.setPadding(64, 32, 64, 16)
        layout.addView(editText)

        android.app.AlertDialog.Builder(context)
            .setTitle("Edit Title")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = editText.text.toString().trim()
                // Only update if they actually changed something
                if (newTitle.isNotEmpty() && newTitle != runSession.title) {
                    executeEditRun(runSession, newTitle)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // The Execution Function
    private fun executeEditRun(oldSession: RunSession, newTitle: String) {
        val updatedSession = oldSession.copy(title = newTitle)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Update Local Database
                val runDao = com.lionfit.app.data.database.AppDatabase.getDatabase(requireContext()).runDao()
                runDao.updateRun(updatedSession)

                // Update Cloud Database
                SupabaseManager.updateRunTitle(updatedSession.id, newTitle)

                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(requireContext(), "Name updated", android.widget.Toast.LENGTH_SHORT).show()
                    // Trigger the swipe-refresh logic to pull the fresh data into the list
                    swipeRefreshLayout?.isRefreshing = true
                    fetchRunHistory()
                }
            } catch (e: Exception) {
                // Silent log for the developer
                if (com.lionfit.app.BuildConfig.DEBUG) {
                    e.printStackTrace()
                    android.util.Log.e("RunHistoryFragment", "Edit Run error: ${e.message}")
                }
                // Safe message for the user
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(requireContext(), "Could not edit run data. Please check your connection.", android.widget.Toast.LENGTH_LONG).show()

                }
            }
        }
    }

    // Delete Run Confirmation Dialog
    private fun showDeleteConfirmationDialog(runSession: RunSession) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Activity?")
            .setMessage("Are you sure you want to delete '${runSession.title}'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                // User confirmed
                executeDeleteRun(runSession)
            }
            .setNegativeButton("Cancel", null) // Do nothing if they cancel
            .show()
    }

    // The Execution Function
    private fun executeDeleteRun(runSession: RunSession) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Step A: Delete from Local Database
                val runDao = com.lionfit.app.data.database.AppDatabase.getDatabase(requireContext()).runDao()
                runDao.deleteRun(runSession)

                // Step B: Delete from Cloud Database
                SupabaseManager.deleteRunSession(runSession.id)

                // Step C: Cleanup the Storage Image
                if (runSession.mapSnapshotUrl != null) {
                    // Reconstruct the exact filename we used during upload
                    val fileName = "${runSession.userId}_${runSession.timestamp}.jpg"
                    SupabaseManager.deleteRunSnapshot(fileName)
                }

                // Step D: Refresh the UI
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(requireContext(), "Activity deleted!", android.widget.Toast.LENGTH_SHORT).show()
                    swipeRefreshLayout?.isRefreshing = true
                    fetchRunHistory()
                }
            } catch (e: Exception) {
                // Silent log for the developer
                if (com.lionfit.app.BuildConfig.DEBUG) {
                    e.printStackTrace()
                    android.util.Log.e("DietFragment", "Delete Run error: ${e.message}")
                }
                // Safe message for the user
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(requireContext(), "Could not delete run data. Please check your connection.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Share Run Activity
    private fun shareRunDetails(runSession: RunSession) {
        android.widget.Toast.makeText(requireContext(), "Generating Share Card...", android.widget.Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val context = requireContext()

                // Inflate the layout
                val view = LayoutInflater.from(context).inflate(R.layout.layout_share_card, null)

                // Format the stats
                val distanceStr = String.format(java.util.Locale.getDefault(), "%.2f km", runSession.distanceInKm)
                val totalSeconds = runSession.durationInMillis / 1000
                val timeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
                val paceStr = String.format(java.util.Locale.getDefault(), "%.1f", runSession.averagePace)

                // Plug data into the View
                view.findViewById<TextView>(R.id.tv_share_title).text = runSession.title
                view.findViewById<TextView>(R.id.tv_share_distance).text = distanceStr
                view.findViewById<TextView>(R.id.tv_share_time).text = timeStr
                view.findViewById<TextView>(R.id.tv_share_pace).text = paceStr

                // Download the map image synchronously using Coil
                if (runSession.mapSnapshotUrl != null) {
                    val loader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(runSession.mapSnapshotUrl)
                        .allowHardware(false) // Crucial for drawing to Canvas later
                        .build()

                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        view.findViewById<ImageView>(R.id.iv_share_map).setImageDrawable(result.drawable)
                    }
                }

                // Measure and layout the view
                val sizeSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
                view.measure(sizeSpec, sizeSpec)
                view.layout(0, 0, view.measuredWidth, view.measuredHeight)

                // Draw the view onto a Bitmap image
                val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                // Set the background color
                canvas.drawColor(android.graphics.Color.parseColor("#121212"))
                view.draw(canvas)

                // Save the Bitmap to a temporary file
                val cachePath = File(context.cacheDir, "shared_images")
                cachePath.mkdirs() // Create the folder if it doesn't exist
                val imageFile = File(cachePath, "lionfit_run_${System.currentTimeMillis()}.jpg")
                val stream = FileOutputStream(imageFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                stream.close()

                // Generate the secure FileProvider URI
                val imageUri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    imageFile
                )

                // Fire off the Share Intent
                withContext(Dispatchers.Main) {
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(android.content.Intent.EXTRA_STREAM, imageUri)
                        putExtra(android.content.Intent.EXTRA_TEXT, "I just crushed a ${distanceStr} run with LionFit! 🦁🏃‍♂️")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(android.content.Intent.createChooser(shareIntent, "Share Workout"))
                }

            } catch (e: Exception) {
                if (com.lionfit.app.BuildConfig.DEBUG) {
                    e.printStackTrace()
                    android.util.Log.e("RunHistoryFragment", "Share Run error: ${e.message}")
                }
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(requireContext(), "Could not share run data. Please check your connection.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}