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
    private val onQuantityChanged: () -> Unit
) : RecyclerView.Adapter<FoodResultAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView    = view.findViewById(R.id.tvFoodResultName)
        val chipCal: Chip       = view.findViewById(R.id.chipResultCal)
        val tvServing: TextView = view.findViewById(R.id.tvServing)
        val btnMinus: ImageButton = view.findViewById(R.id.btnMinus)
        val btnPlus: ImageButton  = view.findViewById(R.id.btnPlus)
        val tvQuantity: TextView  = view.findViewById(R.id.tvQuantity)
        val layoutQuantity: View  = view.findViewById(R.id.layoutQuantity)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_food_result, parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val food = items[position]
        val ctx  = holder.itemView.context

        holder.tvName.text  = food.name
        holder.chipCal.text = ctx.getString(R.string.chip_cal_short, food.calories)
        holder.tvServing.text = food.serving
        
        holder.tvQuantity.text = food.selectedQuantity.toString()

        holder.btnPlus.setOnClickListener {
            food.selectedQuantity++
            holder.tvQuantity.text = food.selectedQuantity.toString()
            onQuantityChanged()
        }

        holder.btnMinus.setOnClickListener {
            if (food.selectedQuantity > 0) {
                food.selectedQuantity--
                holder.tvQuantity.text = food.selectedQuantity.toString()
                onQuantityChanged()
            }
        }

        // No more immediate add on click
        holder.itemView.setOnClickListener(null)
    }
}
