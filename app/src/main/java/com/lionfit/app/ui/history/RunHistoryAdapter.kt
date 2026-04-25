package com.lionfit.app.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lionfit.app.R
import com.lionfit.app.data.model.RunSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import coil.load
import com.lionfit.app.utils.setSafeOnClickListener

class RunHistoryAdapter(
    private var runs: List<RunSession>,
    private val onRunClicked: (RunSession) -> Unit
) :
    RecyclerView.Adapter<RunHistoryAdapter.RunViewHolder>() {
    private val sdf = SimpleDateFormat("dd/MM/yyyy 'at' hh:mm a", Locale.getDefault())

    inner class RunViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tv_run_title)
        val tvDistance: TextView = itemView.findViewById(R.id.tv_run_distance)
        val tvPace: TextView = itemView.findViewById(R.id.tv_run_pace)
        val tvTime: TextView = itemView.findViewById(R.id.tv_run_time)
        val tvDate: TextView = itemView.findViewById(R.id.tv_run_date)
        val tvCalories: TextView = itemView.findViewById(R.id.tv_run_calories)
        val ivMapSnapshot: ImageView = itemView.findViewById(R.id.iv_run_map_snapshot)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_run_history, parent, false)
        return RunViewHolder(view)
    }

    override fun getItemCount(): Int = runs.size

    override fun onBindViewHolder(holder: RunViewHolder, position: Int) {
        val run = runs[position]

        // Format Title
        holder.tvTitle.text = run.title

        // Format Distance
        holder.tvDistance.text = String.format("%.2f km", run.distanceInKm)

        // Format Pace
        val paceMinutes = run.averagePace.toInt()
        val paceSeconds = ((run.averagePace - paceMinutes) * 60f).toInt()
        holder.tvPace.text = String.format("%d'%02d\"", paceMinutes, paceSeconds)

        // Format Duration (Milliseconds to Minutes)
        val totalMinutes = (run.durationInMillis / 1000) / 60
        holder.tvTime.text = "$totalMinutes Min"

        // Format Calories
        holder.tvCalories.text = run.caloriesBurned.toString()

        // Format Date (Timestamp to Readable String)
        holder.tvDate.text = sdf.format(Date(run.timestamp))

        if (run.mapSnapshotUrl != null) {
            holder.ivMapSnapshot.load(run.mapSnapshotUrl) {
                // crossfade(true)
                placeholder(R.drawable.ic_image_placeholder)
                error(R.drawable.ic_image_placeholder) // Fallback if the URL breaks
            }
        } else {
            // If they have older runs without a picture, just show the placeholder
            holder.ivMapSnapshot.setImageResource(R.drawable.ic_image_placeholder)
        }

        // Click Listener
        holder.itemView.setSafeOnClickListener {
            onRunClicked(run)
        }
    }

    // Function to update the list when data arrives from Supabase
    fun submitList(newRuns: List<RunSession>) {
        runs = newRuns
        notifyDataSetChanged()
    }
}