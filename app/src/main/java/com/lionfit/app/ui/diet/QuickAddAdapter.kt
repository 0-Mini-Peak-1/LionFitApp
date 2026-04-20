package com.lionfit.app.ui.diet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lionfit.app.R

class QuickAddAdapter(
    private val items: List<FoodResult>,
    private val onAddClick: (FoodResult) -> Unit
) : RecyclerView.Adapter<QuickAddAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvQuickFoodName)
        val tvCal: TextView = view.findViewById(R.id.tvQuickFoodCal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quick_add_food, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        holder.tvCal.text = "${item.calories} cal"
        holder.itemView.setOnClickListener { onAddClick(item) }
    }

    override fun getItemCount() = items.size
}
