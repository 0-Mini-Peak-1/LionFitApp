package com.lionfit.app.ui.diet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.lionfit.app.R

class FoodResultAdapter(
    private val items: List<FoodResult>,
    private val onAdd: (FoodResult) -> Unit
) : RecyclerView.Adapter<FoodResultAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView    = view.findViewById(R.id.tvFoodResultName)
        val chipCal: Chip       = view.findViewById(R.id.chipResultCal)
        val chipFat: Chip       = view.findViewById(R.id.chipResultFat)
        val chipCarb: Chip      = view.findViewById(R.id.chipResultCarb)
        val chipProtein: Chip   = view.findViewById(R.id.chipResultProtein)
        val tvServing: TextView = view.findViewById(R.id.tvServing)
        val btnAdd: ImageButton = view.findViewById(R.id.btnAddFood)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_food_result, parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val food = items[position]
        val ctx  = holder.itemView.context

        holder.tvName.text    = food.name
        holder.chipCal.text   = ctx.getString(R.string.chip_cal_short, food.calories)
        holder.chipFat.text   = ctx.getString(R.string.chip_fat_short, food.fat.toInt())
        holder.chipCarb.text  = ctx.getString(R.string.chip_carb_short, food.carbs.toInt())
        holder.chipProtein.text = ctx.getString(R.string.chip_protein_short, food.protein.toInt())
        holder.tvServing.text = food.serving
        holder.btnAdd.setOnClickListener { onAdd(food) }
    }
}